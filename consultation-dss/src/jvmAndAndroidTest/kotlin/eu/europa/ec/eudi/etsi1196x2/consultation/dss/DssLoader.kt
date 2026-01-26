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
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.function.TLPredicateFactory
import eu.europa.esig.dss.tsl.function.TypeOtherTSLPointer
import eu.europa.esig.dss.tsl.function.XMLOtherTSLPointer
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.sql.Date
import java.util.function.Predicate
import kotlin.time.Clock
import kotlin.time.toJavaInstant

@Suppress("SameParameterValue")
fun buildLoTLTrust(
    clock: Clock = Clock.System,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    cacheDir: Path,
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
            cacheDir: Path,
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
                    GetTrustedListsCertificateByTrustSource.fromBlocking(
                        scope,
                        config.size,
                        dssLoader::trustedListsCertificateSourceOf,
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
            lotlPredicate = TLPredicateFactory.createEULOTLPredicate()
            tlPredicate = TypeOtherTSLPointer(tlType).and(XMLOtherTSLPointer())
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
            cacheDir: Path,
            lotlLocationPerSource: Map<TrustSource.LoTL, String>,
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
            return DSSLoader(lotlLocationPerSource, onlineLoader, offlineLoader, cacheCleaner)
        }
    }
}
