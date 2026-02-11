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
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

public fun interface DocumentFetcher<out DOCUMENT : Any> {
    public suspend operator fun invoke(uri: String): DOCUMENT
}

/**
 * Result of downloading a LoTE and its references.
 *
 * @param downloaded The main LoTE that was requested
 * @param otherLists All LoTEs that were referenced by this LoTE, each with their own nested references
 * @param problems A list of problems that occurred during the download process
 */
public data class LoTEDownloadResult(
    val downloaded: LoTEDownloadEvent.LoTEDownloaded?,
    val otherLists: List<LoTEDownloadEvent.OtherLoTEDownloaded>,
    val problems: List<LoTEDownloadEvent.Problem>,
    val startedAt: Instant,
    val endedAt: Instant,
) {
    public companion object {

        public suspend fun collect(
            eventsFlow: Flow<LoTEDownloadEvent>,
            clock: Clock = Clock.System,
        ): LoTEDownloadResult {
            val startedAt = clock.now()
            var downloaded: LoTEDownloadEvent.LoTEDownloaded? = null
            val otherLists = mutableListOf<LoTEDownloadEvent.OtherLoTEDownloaded>()
            val problems = mutableListOf<LoTEDownloadEvent.Problem>()
            eventsFlow.toList().forEach { event ->
                when (event) {
                    is LoTEDownloadEvent.LoTEDownloaded -> {
                        check(downloaded == null) { "Multiple LoTEs downloaded" }
                        downloaded = event
                    }

                    is LoTEDownloadEvent.OtherLoTEDownloaded -> otherLists.add(event)
                    is LoTEDownloadEvent.Problem -> problems.add(event)
                }
            }
            if (!otherLists.isEmpty()) {
                checkNotNull(downloaded) { "Other LoTEs downloaded before main LoTE" }
            }
            val endedAt = clock.now()
            return LoTEDownloadResult(downloaded, otherLists.toList(), problems.toList(), startedAt, endedAt)
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
    public data class TimedOut(val duration: Duration) : Problem
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
    internal val jwtFetcher: DocumentFetcher<ListOfTrustedEntitiesClaims>,
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
            val claims = jwtFetcher.invoke(uri)
            val lote = claims.listOfTrustedEntities

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
