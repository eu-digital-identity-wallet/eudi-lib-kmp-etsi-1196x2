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
import eu.europa.ec.eudi.etsi119602.URI
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

public fun interface LoadLoTE<out LOTE : Any> {
    public suspend operator fun invoke(uri: URI): LOTE
}

public class LoadLoTEAndPointers(
    private val constraints: Constraints,
    private val verifyJwtSignature: VerifyJwtSignature,
    private val loadLoTE: LoadLoTE<String>,
) {

    /**
     * Events emitted by the loader
     */
    public sealed interface Event {
        public data class LoTELoaded(val lote: ListOfTrustedEntities, val sourceUri: URI, val depth: Int) : Event
        public sealed interface Problem : Event
        public data class InvalidJWTSignature(val uri: URI, val cause: Throwable?) : Problem
        public data class FailedToParseJwt(val uri: URI, val cause: Throwable?) : Problem
        public data class MaxDepthReached(val uri: URI, val maxDepth: Int) : Problem
        public data class MaxListsReached(val uri: URI, val maxLists: Int) : Problem
        public data class CircularReferenceDetected(val uri: URI) : Problem
        public data class TimedOut(val duration: Duration) : Problem
        public data class Error(val uri: URI, val error: Throwable) : Problem
    }

    private class State(val visitedUris: MutableSet<URI>, initialCount: Int = 0) {
        val downloadsCounter: AtomicInt = atomic(initialCount)
    }

    private data class Step(val uri: URI, val depth: Int) {
        fun childStep(uri: String) = Step(uri, depth + 1)
    }

    private val parseJwt: ParseJwt<JsonObject, ListOfTrustedEntitiesClaims> = ParseJwt()

    public operator fun invoke(uri: URI): Flow<Event> = channelFlow {
        val initial = State(mutableSetOf(), 0)
        val firstStep = Step(uri, depth = 0)
        processLoTE(initial, firstStep)
    }

    private suspend fun ProducerScope<Event>.processLoTE(
        state: State,
        step: Step,
    ) = withContext(Dispatchers.IO) {
        // Check for cancellation
        currentCoroutineContext().ensureActive()

        // Check constraints
        val violation = violationInStep(state, step)
        if (violation != null) {
            send(violation)
            return@withContext
        }

        state.visitedUris.add(step.uri)
        try {
            val unverifiedJwt = loadLoTE(step.uri)
            val event = verifySignatureAndParseJwt(step, unverifiedJwt)

            state.downloadsCounter.incrementAndGet()
            send(event)

            if (event is Event.LoTELoaded) {
                handleOtherPointers(state, step, event)
            }
        } catch (e: Exception) {
            send(errorInStep(step, e))
        } finally {
            state.visitedUris.remove(step.uri)
        }
    }

    private suspend fun ProducerScope<Event>.handleOtherPointers(
        state: State,
        parentStep: Step,
        event: Event.LoTELoaded,
    ): Unit = withContext(Dispatchers.IO) {
        val otherLoTEPointers = event.lote.schemeInformation.pointersToOtherLists
        if (otherLoTEPointers.isNullOrEmpty()) {
            return@withContext
        }
        supervisorScope {
            otherLoTEPointers.chunked(constraints.otherLoTEParallelism).forEach { chunk ->
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

    private suspend fun verifySignatureAndParseJwt(step: Step, unverifiedJwt: String): Event =
        when (val verification = verifyJwtSignature(unverifiedJwt)) {
            is VerifyJwtSignature.Outcome.Verified -> parseJwtInStep(step, verification)
            is VerifyJwtSignature.Outcome.NotVerified -> invalidJwtSignatureInStep(step, verification.cause)
        }

    private fun parseJwtInStep(step: Step, verified: VerifyJwtSignature.Outcome.Verified): Event =
        when (val result = parseJwt(verified.jwt)) {
            is ParseJwt.Outcome.Parsed<*, ListOfTrustedEntitiesClaims> -> {
                val payload = result.payload
                loadedInStep(step, payload.listOfTrustedEntities)
            }
            is ParseJwt.Outcome.ParseFailed -> parseFailedInStep(step, result.cause)
        }

    //
    // Event factories
    //

    private fun violationInStep(
        state: State,
        currentStep: Step,
    ): Event.Problem? {
        val (_, maxDepth, maxDownloads) = constraints
        val (sourceUri, depth) = currentStep
        return when {
            depth > maxDepth -> Event.MaxDepthReached(sourceUri, maxDepth)
            state.downloadsCounter.value >= maxDownloads -> Event.MaxListsReached(
                sourceUri,
                maxDownloads,
            )

            sourceUri in state.visitedUris -> Event.CircularReferenceDetected(sourceUri)
            else -> null
        }
    }

    private fun loadedInStep(step: Step, lote: ListOfTrustedEntities): Event.LoTELoaded {
        val (sourceUri, depth) = step
        return Event.LoTELoaded(lote, sourceUri, depth)
    }

    private fun invalidJwtSignatureInStep(step: Step, cause: Throwable?) =
        Event.InvalidJWTSignature(step.uri, cause)

    private fun parseFailedInStep(step: Step, cause: Throwable?) =
        Event.FailedToParseJwt(step.uri, cause)

    private fun errorInStep(step: Step, error: Throwable): Event.Error =
        Event.Error(step.uri, error)

    public data class Constraints(
        val otherLoTEParallelism: Int,
        val maxDepth: Int,
        val maxLists: Int,
    ) {
        init {
            require(otherLoTEParallelism > 0) { "Parallelism must be greater than 0" }
            require(maxDepth > 0) { "Max depth must be greater than 0" }
            require(maxLists > 0) { "Max downloads must be greater than 0" }
        }
    }
}

public data class LoTELoadResult(
    val list: LoadLoTEAndPointers.Event.LoTELoaded?,
    val otherLists: List<LoadLoTEAndPointers.Event.LoTELoaded>,
    val problems: List<LoadLoTEAndPointers.Event.Problem>,
    val startedAt: Instant,
    val endedAt: Instant,
) {
    public companion object {

        public suspend fun collect(
            eventsFlow: Flow<LoadLoTEAndPointers.Event>,
            clock: Clock = Clock.System,
        ): LoTELoadResult {
            val startedAt = clock.now()
            var list: LoadLoTEAndPointers.Event.LoTELoaded? = null
            val otherLists = mutableListOf<LoadLoTEAndPointers.Event.LoTELoaded>()
            val problems = mutableListOf<LoadLoTEAndPointers.Event.Problem>()
            eventsFlow.toList().forEach { event ->
                when (event) {
                    is LoadLoTEAndPointers.Event.LoTELoaded ->
                        if (event.depth == 0) {
                            check(list == null) { "Multiple LoTEs downloaded with depth 0" }
                            list = event
                        } else {
                            otherLists.add(event)
                        }

                    is LoadLoTEAndPointers.Event.Problem -> problems.add(event)
                }
            }
            if (!otherLists.isEmpty()) {
                checkNotNull(list) { "Other LoTEs downloaded before main LoTE" }
            }
            val endedAt = clock.now()
            return LoTELoadResult(list, otherLists.toList(), problems.toList(), startedAt, endedAt)
        }
    }
}
