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
import eu.europa.ec.eudi.etsi119602.OtherLoTEPointer
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

public fun interface LoadDocument<out DOCUMENT : Any> {
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
    val downloaded: LoTELoadEvent.LoTELoaded?,
    val otherLists: List<LoTELoadEvent.OtherLoTELoaded>,
    val problems: List<LoTELoadEvent.Problem>,
    val startedAt: Instant,
    val endedAt: Instant,
) {
    public companion object {

        public suspend fun collect(
            eventsFlow: Flow<LoTELoadEvent>,
            clock: Clock = Clock.System,
        ): LoTEDownloadResult {
            val startedAt = clock.now()
            var downloaded: LoTELoadEvent.LoTELoaded? = null
            val otherLists = mutableListOf<LoTELoadEvent.OtherLoTELoaded>()
            val problems = mutableListOf<LoTELoadEvent.Problem>()
            eventsFlow.toList().forEach { event ->
                when (event) {
                    is LoTELoadEvent.LoTELoaded -> {
                        check(downloaded == null) { "Multiple LoTEs downloaded" }
                        downloaded = event
                    }

                    is LoTELoadEvent.OtherLoTELoaded -> otherLists.add(event)
                    is LoTELoadEvent.Problem -> problems.add(event)
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
public sealed interface LoTELoadEvent {
    public data class LoTELoaded(val lote: ListOfTrustedEntities, val sourceUri: String) : LoTELoadEvent

    public data class OtherLoTELoaded(val lote: ListOfTrustedEntities, val sourceUri: String, val depth: Int) :
        LoTELoadEvent

    public sealed interface Problem : LoTELoadEvent
    public data class MaxDepthReached(val uri: String, val maxDepth: Int) : Problem
    public data class MaxDownloadsReached(val uri: String, val maxLotes: Int) : Problem
    public data class CircularReferenceDetected(val uri: String) : Problem
    public data class TimedOut(val duration: Duration) : Problem
    public data class Error(val uri: String, val error: Throwable) : Problem
}

public data class Constraints(
    val maxDepth: Int,
    val maxDownloads: Int,
) {
    init {
        require(maxDepth > 0) { "Max depth must be greater than 0" }
        require(maxDownloads > 0) { "Max downloads must be greater than 0" }
    }
}

public class LoadLoTE(
    internal val parallelism: Int = 2,
    private val constraints: Constraints = Constraints(1, 20),
    internal val loadDocument: LoadDocument<ListOfTrustedEntitiesClaims>,
) {

    init {
        require(parallelism > 0) { "Parallelism must be greater than 0" }
    }

    private class State(val visitedUris: MutableSet<String>, initialCount: Int = 0) {
        val downloadsCounter: AtomicInt = atomic(initialCount)
    }

    private data class Step(val uri: String, val depth: Int) {
        fun childStep(uri: String) = Step(uri, depth + 1)
    }

    public operator fun invoke(uri: String): Flow<LoTELoadEvent> = channelFlow {
        val initial = State(mutableSetOf(), 0)
        val firstStep = Step(uri, 0)
        processLoTE(initial, firstStep)
    }

    private suspend fun ProducerScope<LoTELoadEvent>.processLoTE(state: State, step: Step) =
        withContext(Dispatchers.IO) {
            // Check for cancellation
            currentCoroutineContext().ensureActive()

            // Check constraints
            val violation = violationInStep(state, step)
            if (violation != null) {
                send(violation)
                return@withContext
            }

            // Mark URI as visited before processing to detect circular references
            state.visitedUris.add(step.uri)

            try {
                // Fetch and parse the JWT
                val claims = loadDocument(step.uri)
                val lote = claims.listOfTrustedEntities

                state.downloadsCounter.incrementAndGet()
                send(loadedInStep(lote, step))

                // Process references recursively with parallel processing and event emission
                val otherLoTEPointers = lote.schemeInformation.pointersToOtherLists
                if (!otherLoTEPointers.isNullOrEmpty()) {
                    processPointersToOtherLists(state, step, otherLoTEPointers)
                }
            } catch (e: Exception) {
                // Emit error event
                send(errorInStep(e, step))
            } finally {
                // Remove from visited when returning from this level of recursion
                state.visitedUris.remove(step.uri)
            }
        }

    private suspend fun ProducerScope<LoTELoadEvent>.processPointersToOtherLists(
        state: State,
        parentStep: Step,
        otherLoTEPointers: List<OtherLoTEPointer>,
    ) {
        withContext(Dispatchers.IO) {
            supervisorScope {
                otherLoTEPointers.chunked(parallelism).forEach { chunk ->
                    val deferredTasks = chunk.map { reference ->
                        async {
                            val step = parentStep.childStep(reference.location)
                            processLoTE(state, step)
                        }
                    }

                    deferredTasks.awaitAll()
                }
            }
        }
    }

    //
    // Event factories
    //

    private fun violationInStep(
        state: State,
        currentStep: Step,
    ): LoTELoadEvent.Problem? {
        val (maxDepth, maxDownloads) = constraints
        val (sourceUri, depth) = currentStep
        return when {
            depth > maxDepth -> LoTELoadEvent.MaxDepthReached(sourceUri, maxDepth)
            state.downloadsCounter.value >= maxDownloads -> LoTELoadEvent.MaxDownloadsReached(
                sourceUri,
                maxDownloads,
            )

            sourceUri in state.visitedUris -> LoTELoadEvent.CircularReferenceDetected(sourceUri)
            else -> null
        }
    }

    private fun loadedInStep(lote: ListOfTrustedEntities, step: Step): LoTELoadEvent {
        val (sourceUri, depth) = step
        return if (depth == 0) {
            LoTELoadEvent.LoTELoaded(lote, sourceUri)
        } else {
            LoTELoadEvent.OtherLoTELoaded(lote, sourceUri, depth)
        }
    }

    private fun errorInStep(error: Throwable, step: Step): LoTELoadEvent.Error =
        LoTELoadEvent.Error(step.uri, error)
}
