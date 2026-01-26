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

import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.AsyncCache.Entry
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import kotlinx.coroutines.*
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

public fun IsChainTrusted.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.trustAnchorCreator(),
    getTrustedListsCertificateSource: suspend () -> TrustedListsCertificateSource,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> =
    IsChainTrusted.Companion(validateCertificateChain) {
        val source = getTrustedListsCertificateSource()
        source.certificates.orEmpty().map { trustAnchorCreator(it.certificate) }
    }

public fun IsChainTrustedForContext.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    config: Map<VerificationContext, Pair<TrustSource.LoTL, TrustAnchorCreator<X509Certificate, TrustAnchor>?>>,
    getTrustedListsCertificateByTrustSource: GetTrustedListsCertificateByTrustSource,
): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val trust = config.mapValues { (_, value) ->
        val (trustSource, trustAnchorCreator) = value
        IsChainTrusted.usingLoTL(
            validateCertificateChain,
            trustAnchorCreator ?: JvmSecurity.trustAnchorCreator(),
        ) {
            getTrustedListsCertificateByTrustSource(trustSource)
        }
    }
    return IsChainTrustedForContext(trust)
}

public fun interface GetTrustedListsCertificateByTrustSource {
    public suspend operator fun invoke(trustSource: TrustSource.LoTL): TrustedListsCertificateSource

    public companion object {
        public fun fromBlocking(
            scope: CoroutineScope,
            maxCacheSize: Int,
            block: (TrustSource.LoTL) -> TrustedListsCertificateSource,
        ): GetTrustedListsCertificateByTrustSource =
            GetTrustedListsCertificateByTrustSourceBlocking(scope, maxCacheSize, block)
    }
}

internal class GetTrustedListsCertificateByTrustSourceBlocking(
    scope: CoroutineScope,
    maxCacheSize: Int,
    block: (TrustSource.LoTL) -> TrustedListsCertificateSource,
) : GetTrustedListsCertificateByTrustSource {

    private val cached =
        AsyncCache<TrustSource.LoTL, TrustedListsCertificateSource>(
            scope = scope,
            maxCacheSize = maxCacheSize,
        ) { trustSource -> withContext(Dispatchers.IO) { block(trustSource) } }

    override suspend fun invoke(trustSource: TrustSource.LoTL): TrustedListsCertificateSource = cached(trustSource)
}

internal class AsyncCache<A : Any, B : Any>(
    private val scope: CoroutineScope,
    private val maxCacheSize: Int,
    private val ttl: Duration = 10.minutes,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B {

    private data class Entry<B>(val deferred: Deferred<B>, val createdAt: Long)

    private val cache = object : LinkedHashMap<A, Entry<B>>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<A, Entry<B>>) = size > maxCacheSize
    }

    override suspend fun invoke(key: A): B {
        val now = System.currentTimeMillis()
        val entry = synchronized(cache) {
            val existing = cache[key]
            if (existing != null && (now - existing.createdAt) < ttl.inWholeMilliseconds) {
                existing
            } else {
                // Launch new computation
                val newDeferred = scope.async(Dispatchers.IO) {
                    try {
                        supplier(key)
                    } catch (e: Exception) {
                        // Evict on failure so next call retries
                        synchronized(cache) { cache.remove(key) }
                        throw e
                    }
                }
                Entry(newDeferred, now).also { cache[key] = it }
            }
        }
        return entry.deferred.await()
    }
}
