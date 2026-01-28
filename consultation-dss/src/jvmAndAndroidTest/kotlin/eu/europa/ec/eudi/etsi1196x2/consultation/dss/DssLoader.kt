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

import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.or
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.sql.Date
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

class DSSLoader(
    private val sourcePerVerification: Map<VerificationContext, LOTLSource>,
    private val onlineLoader: DSSCacheFileLoader,
    private val offlineLoader: DSSCacheFileLoader?,
    private val cacheCleaner: CacheCleaner?,
) {

    fun trustedListsCertificateSourceOf(
        lotlSource: LOTLSource,
    ): TrustedListsCertificateSource {
        require(lotlSource in sourcePerVerification.values) { "Not configured $lotlSource" }
        return TrustedListsCertificateSource().also { source ->
            validationJob(lotlSource, source).refresh()
        }
    }

    private fun TLValidationJob.refresh() {
        try {
            onlineRefresh()
        } catch (e: Exception) {
            if (offlineLoader != null) {
                try {
                    offlineRefresh()
                } catch (_: Exception) {
                    throw RuntimeException("Both online and offline trust loading failed", e)
                }
            } else {
                throw e
            }
        }
    }

    private fun validationJob(
        lotlSource: LOTLSource,
        source: TrustedListsCertificateSource,
    ): TLValidationJob =
        TLValidationJob().apply {
            setListOfTrustedListSources(lotlSource)
            setOnlineDataLoader(onlineLoader)
            setTrustedListCertificateSource(source)
            setSynchronizationStrategy(ExpirationAndSignatureCheckStrategy())
            offlineLoader?.let { setOfflineDataLoader(it) }
            cacheCleaner?.let { setCacheCleaner(it) }
        }

    fun isChainTrustedForContext(
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
        clock: Clock = Clock.System,
        ttl: Duration = 10.minutes,
        revocationEnabled: Boolean = false,
        fallBack: IsChainTrusted<List<X509Certificate>, TrustAnchor>? = null,
    ): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
        val validateCertificateChain =
            ValidateCertificateChainJvm {
                isRevocationEnabled = revocationEnabled
                date = Date.from(clock.now().toJavaInstant())
            }
        val getTrustedListsCertificateByLOTLSource =
            GetTrustedListsCertificateByLOTLSource.fromBlocking(
                coroutineScope = coroutineScope,
                coroutineDispatcher = coroutineDispatcher,
                expectedTrustSourceNo = sourcePerVerification.size,
                ttl = ttl,
                clock = clock,
                block = ::trustedListsCertificateSourceOf,
            )

        val trust = sourcePerVerification.mapValues { (_, lotlSource) ->
            val provider = getTrustedListsCertificateByLOTLSource.asProviderFor(lotlSource)
            val isChainTrusted = IsChainTrusted(validateCertificateChain, provider)
            if (fallBack != null) (isChainTrusted or fallBack) else isChainTrusted
        }

        return IsChainTrustedForContext(trust)
    }

    companion object {
        operator fun invoke(
            cacheDir: Path,
            sourcePerVerification: Map<VerificationContext, LOTLSource>,
        ): DSSLoader {
            val tlCacheDirectory = cacheDir.toFile()
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
            return DSSLoader(sourcePerVerification, onlineLoader, offlineLoader, cacheCleaner)
        }
    }
}
