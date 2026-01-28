/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.AsyncCache.Entry
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.cert.TrustAnchor
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Defines a functional interface for retrieving a trusted lists certificate source
 * using a specified LOTLSource.
 *
 * The interface provides a method to asynchronously fetch a [TrustedListsCertificateSource]
 * based on the provided [LOTLSource]. It includes a companion object for creating an instance
 * using blocking logic wrapped in a coroutine-friendly structure.
 *
 * Note that in DSS:
 * - [LOTLSource] is a set of predicates to traverse a LOTL, using a [eu.europa.esig.dss.tsl.job.TLValidationJob]
 * - A [eu.europa.esig.dss.tsl.job.TLValidationJob] job aggregates matching certificates to a [TrustedListsCertificateSource]
 */
public fun interface GetTrustedListsCertificateByLOTLSource {

    /**
     * Retrieves a trusted lists certificate source based on the provided [LOTLSource].
     * @param trustSource the LOTLSource to use for fetching the certificate source
     * @return the [TrustedListsCertificateSource]
     */
    public suspend operator fun invoke(trustSource: LOTLSource): TrustedListsCertificateSource

    public fun asProviderFor(
        trustSource: LOTLSource,
        trustAnchorCreator: TrustAnchorCreator<CertificateToken, TrustAnchor> = DSSTrustAnchorCreator,
    ): GetTrustAnchors<TrustAnchor> =
        GetTrustAnchors {
            invoke(trustSource).trustAnchors(trustAnchorCreator)
        }

    public companion object {
        private val DEFAULT_SCOPE = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val DEFAULT_DISPATCHER = Dispatchers.IO

        /**
         * Creates a [GetTrustedListsCertificateByLOTLSource] instance from blocking logic.
         *
         * @param coroutineScope the overall scope of the resulting [GetTrustedListsCertificateByLOTLSource]. By default, adds [SupervisorJob]
         * @param coroutineDispatcher the coroutine dispatcher for executing the blocking logic
         * @param expectedTrustSourceNo the expected number of trust sources
         * @param ttl the time-to-live duration for caching the certificate source. It should be set to a value higher than the average duration of executing the [block]
         * @param block the blocking function to retrieve the certificate source
         * @return the [GetTrustedListsCertificateByLOTLSource] instance
         */
        public fun fromBlocking(
            coroutineScope: CoroutineScope = DEFAULT_SCOPE,
            coroutineDispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
            clock: Clock = Clock.System,
            ttl: Duration,
            expectedTrustSourceNo: Int,
            block: (LOTLSource) -> TrustedListsCertificateSource,
        ): GetTrustedListsCertificateByLOTLSource =
            GetTrustedListsCertificateByLOTLSourceBlocking(
                coroutineScope,
                coroutineDispatcher,
                clock,
                ttl,
                expectedTrustSourceNo,
                block,
            )
    }
}

internal class GetTrustedListsCertificateByLOTLSourceBlocking(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    clock: Clock,
    ttl: Duration,
    expectedTrustSourceNo: Int,
    block: (LOTLSource) -> TrustedListsCertificateSource,
) : GetTrustedListsCertificateByLOTLSource {

    private val cached: AsyncCache<LOTLSource, TrustedListsCertificateSource> =
        AsyncCache(scope, dispatcher, clock, ttl, expectedTrustSourceNo) { trustSource ->
            block(trustSource)
        }

    override suspend fun invoke(trustSource: LOTLSource): TrustedListsCertificateSource = cached(trustSource)
}

internal class AsyncCache<A : Any, B : Any>(
    coroutineScope: CoroutineScope,
    private val coroutineDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
    private val ttl: Duration,
    private val maxCacheSize: Int,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B {

    private val scope = coroutineScope + SupervisorJob(coroutineScope.coroutineContext[Job])

    private data class Entry<B>(val deferred: Deferred<B>, val createdAt: Long)

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<A, Entry<B>>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<A, Entry<B>>) =
                size > maxCacheSize
        }

    override suspend fun invoke(key: A): B {
        val now = clock.now().toEpochMilliseconds()
        val entry = mutex.withLock {
            val existing = cache[key]
            if (existing != null && (now - existing.createdAt) < ttl.inWholeMilliseconds) {
                existing
            } else {
                // Launch new computation
                val newDeferred = scope.async(coroutineDispatcher) {
                    supplier(key)
                }
                Entry(newDeferred, now).also { cache[key] = it }
            }
        }
        return try {
            entry.deferred.await()
        } catch (e: Exception) {
            handleFailure(key, entry)
            throw e
        }
    }

    private suspend fun handleFailure(key: A, entry: Entry<B>) {
        mutex.withLock {
            if (cache[key] === entry) {
                cache.remove(key)
            }
        }
    }
}
