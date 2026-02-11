/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntitiesClaims
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/**
 * Interface for fetching JWTs from URIs.
 * This allows for dependency injection of different HTTP client implementations.
 */
public fun interface JwtFetcher {
    /**
     * Fetches a JWT from the given URI.
     *
     * @param uri The URI to fetch the JWT from
     * @return The JWT as a string
     * @throws Exception if the fetch fails
     */
    public suspend operator fun invoke(uri: String): String
}

/**
 * Utility class for parsing JWTs to extract ListOfTrustedEntities.
 */
public object JwtParser {
    private val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /**
     * Extracts the payload from a JWT string.
     *
     * @param jwt The JWT in compact form (header.payload.signature)
     * @return The decoded payload as a JsonObject
     */
    public fun getPayload(jwt: String): JsonObject {
        require(jwt.isNotBlank()) { "JWT must not be empty" }
        val parts = jwt.split(".")
        require(parts.size == 3) { "Input must be a JWS in compact form" }

        return Json.parseToJsonElement(
            base64UrlSafeNoPadding.decode(parts[1]).decodeToString(),
        ).jsonObject
    }

    /**
     * Converts a JWT string to a ListOfTrustedEntities object.
     *
     * @param jwt The JWT containing the LoTE in its payload
     * @return The parsed ListOfTrustedEntities
     */
    public fun loteOfJwt(jwt: String): ListOfTrustedEntities {
        val payload = getPayload(jwt)
        val claims = Json.decodeFromJsonElement(ListOfTrustedEntitiesClaims.serializer(), payload)
        return claims.listOfTrustedEntities
    }
}

/**
 * Result of downloading a LoTE and its references.
 *
 * @param list The main LoTE that was requested
 * @param otherLists All LoTEs that were referenced by this LoTE, each with their own nested references
 * @param problems A list of problems that occurred during the download process
 */
public data class LoTEDownloadResult(
    val list: ListOfTrustedEntities,
    val otherLists: List<LoTEDownloadResult>,
    val problems: List<LoTEDownloadEvent.Problem> = emptyList(),
) {
    public companion object {

        public suspend fun collect(eventsFlow: Flow<LoTEDownloadEvent>): LoTEDownloadResult {
            val events = eventsFlow.toSet()
            return buildLoTEDownloadResult(events)
        }

        /**
         * Builds a LoTEDownloadResult from a set of events.
         * This implementation reconstructs the hierarchy based on the depth information in the events.
         */
        private fun buildLoTEDownloadResult(events: Set<LoTEDownloadEvent>): LoTEDownloadResult {
            val mainLoteEvent = events.filterIsInstance<LoTEDownloadEvent.LoTEDownloaded>().firstOrNull()
                ?: throw LoTEDownloadException("No main LoTE event found")

            // Group referenced LoTEs by depth to reconstruct the hierarchy
            val referencedEvents = events.filterIsInstance<LoTEDownloadEvent.OtherLoTEDownloaded>()

            // Convert events to problems
            val problems = mutableListOf<LoTEDownloadEvent.Problem>()
            problems.addAll(
                events.filterIsInstance<LoTEDownloadEvent.MaxDepthReached>().map {
                    LoTEDownloadEvent.MaxDepthReached(it.uri, it.maxDepth)
                },
            )
            problems.addAll(
                events.filterIsInstance<LoTEDownloadEvent.MaxLotesReached>().map {
                    LoTEDownloadEvent.MaxLotesReached(it.uri, it.maxLotes)
                },
            )
            problems.addAll(
                events.filterIsInstance<LoTEDownloadEvent.CircularReferenceDetected>().map {
                    LoTEDownloadEvent.CircularReferenceDetected(it.uri)
                },
            )
            problems.addAll(
                events.filterIsInstance<LoTEDownloadEvent.Error>().map {
                    LoTEDownloadEvent.Error(it.uri, it.error)
                },
            )

            // For a simple reconstruction, we'll create a flat list of all referenced LoTEs
            // A more sophisticated implementation could reconstruct the full tree structure
            val allReferencedLotes = referencedEvents.map { LoTEDownloadResult(it.lote, emptyList()) }

            return LoTEDownloadResult(mainLoteEvent.lote, allReferencedLotes, problems)
        }
    }
}

/**
 * Event emitted during the download process.
 */
public sealed interface LoTEDownloadEvent {
    public data class LoTEDownloaded(val lote: ListOfTrustedEntities) : LoTEDownloadEvent {
        override fun toString(): String {
            return "LoTEDownloaded(lote=${lote.schemeInformation.type})"
        }
    }

    public data class OtherLoTEDownloaded(val lote: ListOfTrustedEntities, val depth: Int, val sourceUri: String) :
        LoTEDownloadEvent

    public sealed interface Problem : LoTEDownloadEvent
    public data class MaxDepthReached(val uri: String, val maxDepth: Int) : Problem
    public data class MaxLotesReached(val uri: String, val maxLotes: Int) : Problem
    public data class CircularReferenceDetected(val uri: String) : Problem
    public data class Error(val uri: String, val error: Throwable) : Problem
}

/**
 * Downloads a LoTE and all referenced LoTEs recursively.
 *
 * @param jwtFetcher The component responsible for fetching JWTs from URIs
 * @param parallelism Number of concurrent downloads for processing references in parallel
 */
public class LoTEDownloader(
    internal val parallelism: Int = 2,
    internal val jwtFetcher: JwtFetcher,
) {

    private class LoTEDownloadState(
        val visitedUris: MutableSet<String>,
        initialCount: Int = 0,
    ) {
        val totalLotesCount: AtomicInt = atomic(initialCount)
    }

    /**
     * Downloads a LoTE from the given URI and emits events for each downloaded LoTE.
     *
     * @param uri The URI of the initial LoTE to download
     * @param maxDepth Maximum depth to recurse to prevent extremely deep reference chains
     * @param maxTotalLotes Maximum total number of LoTEs to download to prevent excessive resource usage
     * @return A Flow of LoTEDownloadEvent objects
     */
    public fun downloadFlow(uri: String, maxDepth: Int, maxTotalLotes: Int): Flow<LoTEDownloadEvent> = channelFlow {
        val visitedUris = mutableSetOf<String>()
        val state = LoTEDownloadState(visitedUris)

        // Download the main LoTE
        downloadSingleLoteWithEvents(uri, state, 0, maxDepth, maxTotalLotes, this)

        // Note: The main LoTE event is emitted inside downloadSingleLoteWithEvents
    }

    private suspend fun downloadSingleLoteWithEvents(
        uri: String,
        state: LoTEDownloadState,
        currentDepth: Int,
        maxDepth: Int,
        maxTotalLotes: Int,
        emitter: ProducerScope<LoTEDownloadEvent>,
    ) = withContext(Dispatchers.IO) {
        // Check for cancellation
        currentCoroutineContext().ensureActive()

        // Prevent circular references and limit depth
        if (currentDepth > maxDepth) {
            emitter.send(LoTEDownloadEvent.MaxDepthReached(uri, maxDepth))
            return@withContext
        }

        if (state.totalLotesCount.value >= maxTotalLotes) {
            emitter.send(LoTEDownloadEvent.MaxLotesReached(uri, maxTotalLotes))
            return@withContext
        }

        if (uri in state.visitedUris) {
            emitter.send(LoTEDownloadEvent.CircularReferenceDetected(uri))
            return@withContext
        }

        // Mark URI as visited before processing to detect circular references
        state.visitedUris.add(uri)

        try {
            // Fetch and parse the JWT
            val jwt = jwtFetcher.invoke(uri)
            val lote = JwtParser.loteOfJwt(jwt)

            // Increment the count after successfully fetching and parsing
            state.totalLotesCount.incrementAndGet()

            // Emit the main LoTE event
            if (currentDepth == 0) {
                emitter.send(LoTEDownloadEvent.LoTEDownloaded(lote))
            } else {
                emitter.send(LoTEDownloadEvent.OtherLoTEDownloaded(lote, currentDepth, uri))
            }

            // Process references recursively with parallel processing and event emission
            processReferencesWithEvents(lote, state, currentDepth + 1, maxDepth, maxTotalLotes, emitter)
        } catch (e: Exception) {
            // Emit error event
            emitter.send(LoTEDownloadEvent.Error(uri, e))
        } finally {
            // Remove from visited when returning from this level of recursion
            state.visitedUris.remove(uri)
        }
    }

    private suspend fun processReferencesWithEvents(
        lote: ListOfTrustedEntities,
        state: LoTEDownloadState,
        currentDepth: Int,
        maxDepth: Int,
        maxTotalLotes: Int,
        emitter: ProducerScope<LoTEDownloadEvent>,
    ): Unit = withContext(Dispatchers.IO) {
        val references = lote.schemeInformation.pointersToOtherLists

        if (references.isNullOrEmpty()) {
            return@withContext
        }

        // Process references in parallel using supervisorScope to handle failures independently
        supervisorScope {
            // Split references into chunks based on parallelism
            references.chunked(parallelism).forEach { chunk ->
                val deferredTasks = chunk.map { reference ->
                    async {
                        val referenceUri = reference.location
                        downloadSingleLoteWithEvents(
                            referenceUri,
                            state,
                            currentDepth,
                            maxDepth,
                            maxTotalLotes,
                            emitter,
                        )
                    }
                }

                deferredTasks.awaitAll()
            }
        }
    }
}

/**
 * Exception thrown when there's an issue during LoTE download.
 */
public class LoTEDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
