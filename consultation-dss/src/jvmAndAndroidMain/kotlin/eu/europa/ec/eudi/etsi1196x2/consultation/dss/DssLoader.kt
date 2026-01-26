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

import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustSource
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.AsyncCache.Entry
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.sql.Date
import java.util.function.Predicate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

@Suppress("SameParameterValue")
public fun buildLoTLTrust(
    clock: Clock = Clock.System,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    cacheDir: Path? = null,
    revocationEnabled: Boolean = false,
    builder: MutableMap<VerificationContext, Pair<TrustSource.LoTL, String>>.() -> Unit,
): DssLoadAndTrust = DssLoadAndTrust(
    clock,
    scope,
    cacheDir,
    revocationEnabled,
    buildMap(builder),
)

public data class DssLoadAndTrust private constructor(
    val dssLoader: DSSLoader,
    val isChainTrustedForContext: IsChainTrustedForContext<List<X509Certificate>, TrustAnchor>,
) {
    public companion object {
        public operator fun invoke(
            clock: Clock = Clock.System,
            scope: CoroutineScope,
            cacheDir: Path? = null,
            revocationEnabled: Boolean = false,
            instructions: Map<VerificationContext, Pair<TrustSource.LoTL, String>>,
        ): DssLoadAndTrust {
            val dssLoader = run {
                val lotlLocationPerSource =
                    instructions.values.associate { it.first to it.second }
                DSSLoader(cacheDir, lotlLocationPerSource)
            }

            val isChainTrustedForContext = run {
                val config =
                    instructions.mapValues { it.value.first to null }
                val validateCertificateChain =
                    ValidateCertificateChainJvm {
                        isRevocationEnabled = revocationEnabled
                        date = Date.from(clock.now().toJavaInstant())
                    }
                val getTrustedListsCertificateByTrustSource =
                    GetTrustedListsCertificateByTrustSourceUsingDssLoader(
                        dssLoader,
                        scope,
                        config.size,
                    )
                IsChainTrustedForContext.Companion.usingLoTL(
                    validateCertificateChain,
                    config,
                    getTrustedListsCertificateByTrustSource,
                )
            }

            return DssLoadAndTrust(dssLoader, isChainTrustedForContext)
        }
    }
}

private class GetTrustedListsCertificateByTrustSourceUsingDssLoader(
    dssLoader: DSSLoader,
    scope: CoroutineScope,
    maxCacheSize: Int,
) : GetTrustedListsCertificateByTrustSource {

    private val cached =
        AsyncCache<TrustSource.LoTL, TrustedListsCertificateSource>(
            scope = scope,
            maxCacheSize = maxCacheSize,
        ) { trustSource ->
            withContext(Dispatchers.IO) {
                dssLoader.trustedListsCertificateSourceOf(trustSource)
            }
        }

    override suspend fun invoke(trustSource: TrustSource.LoTL): TrustedListsCertificateSource = cached(trustSource)
}

public class DSSLoader(
    private val lotlLocationPerSource: Map<TrustSource.LoTL, String>,
    private val onlineLoader: DSSCacheFileLoader,
    private val offlineLoader: DSSCacheFileLoader?,
    private val cacheCleaner: CacheCleaner?,
) {

    public fun trustedListsCertificateSourceOf(
        trustSource: TrustSource.LoTL,
    ): TrustedListsCertificateSource {
        println("Loading trusted lists for ${trustSource.serviceType}...")
        val lotlUrl = lotlLocationPerSource[trustSource]
        requireNotNull(lotlUrl) { "No location for $trustSource" }
        return TrustedListsCertificateSource().also { source ->
            with(trustSource) {
                validationJob(lotlUrl, source).refresh(trustSource)
            }
        }
    }

    private fun TLValidationJob.refresh(trustSource: TrustSource.LoTL) {
        try {
            onlineRefresh()
        } catch (e: Exception) {
            println("Online refresh failed for ${trustSource.serviceType}, attempting offline load...")
            try {
                offlineRefresh()
            } catch (_: Exception) {
                throw RuntimeException("Both online and offline trust loading failed", e)
            }
        }
    }

    private fun TrustSource.LoTL.validationJob(
        lotlUrl: String,
        source: TrustedListsCertificateSource,
    ) = TLValidationJob().apply {
        setListOfTrustedListSources(lotlSource(lotlUrl))
        setOnlineDataLoader(onlineLoader)
        setTrustedListCertificateSource(source)
        setSynchronizationStrategy(ExpirationAndSignatureCheckStrategy())
        offlineLoader?.let { setOfflineDataLoader(it) }
        cacheCleaner?.let { setCacheCleaner(it) }
    }

    private fun TrustSource.LoTL.lotlSource(lotlUrl: String): LOTLSource =
        LOTLSource().apply {
            url = lotlUrl
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(5, 6)
            serviceType.let {
                trustServicePredicate = Predicate { tspServiceType ->
                    tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
                }
            }
        }

    public companion object {
        public operator fun invoke(
            cacheDir: Path?,
            lotlLocationPerSource: Map<TrustSource.LoTL, String>,
        ): DSSLoader {
            val tlCacheDirectory =
                (cacheDir ?: Files.createTempDirectory("lotl-cache")).toFile()

            val offlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
                setCacheExpirationTime(24 * 60 * 60 * 1000)
                setFileCacheDirectory(tlCacheDirectory)
                dataLoader = IgnoreDataLoader()
            }

            val onlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
                setCacheExpirationTime(24 * 60 * 60 * 1000)
                setFileCacheDirectory(tlCacheDirectory)
                dataLoader = CommonsDataLoader()
            }

            val cacheCleaner = CacheCleaner().apply {
                setCleanMemory(true)
                setCleanFileSystem(true)
                setDSSFileLoader(offlineLoader)
            }
            return DSSLoader(lotlLocationPerSource, onlineLoader, offlineLoader, cacheCleaner)
        }
    }
}

private class AsyncCache<A : Any, B : Any>(
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
