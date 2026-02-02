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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Creates an instance of [IsChainTrusted] using a trusted list of trust anchors (LoTL).
 *
 * @param validateCertificateChain the function used to validate a given certificate chain.
 *        Defaults to [ValidateCertificateChainJvm.Default]
 * @param trustAnchorCreator a function that creates a trust anchor from a [CertificateToken]
 *        Defaults to [DSSTrustAnchorCreator]
 * @param getTrustedListsCertificateSource a suspend function that retrieves the trusted lists certificate source containing trust anchors
 * @return an [IsChainTrusted] instance configured to validate certificate chains using the provided trusted list
 *
 * @see TrustedListsCertificateSource
 * @see GetTrustedListsCertificateByLOTLSource
 */
public fun IsChainTrusted.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm.Default,
    trustAnchorCreator: TrustAnchorCreator<CertificateToken, TrustAnchor> = DSSTrustAnchorCreator,
    getTrustedListsCertificateSource: suspend () -> TrustedListsCertificateSource,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> =
    IsChainTrusted(validateCertificateChain) {
        getTrustedListsCertificateSource().trustAnchors(trustAnchorCreator)
    }

/**
 * Creates an instance of [IsChainTrustedForContext] using a trusted list of trust anchors (LoTL).
 *
 * @param validateCertificateChain the function used to validate a given certificate chain.
 *        Defaults to [ValidateCertificateChainJvm.Default]
 * @param trustAnchorCreator a function that creates a trust anchor from a [CertificateToken]
 *        Defaults to [DSSTrustAnchorCreator]
 * @param sourcePerVerification a map of verification contexts to trusted list sources
 * @param getTrustedListsCertificateByLOTLSource a function that retrieves the trusted lists certificate
 * source containing trust anchors for a given verification context
 *
 * @return an [IsChainTrustedForContext] instance configured to validate certificate chains
 * using the provided trusted lists
 */
public fun IsChainTrustedForContext.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChain<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainJvm.Default,
    trustAnchorCreator: TrustAnchorCreator<CertificateToken, TrustAnchor> = DSSTrustAnchorCreator,
    sourcePerVerification: Map<VerificationContext, LOTLSource>,
    getTrustedListsCertificateByLOTLSource: GetTrustedListsCertificateByLOTLSource,
): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val trust = sourcePerVerification.mapValues { (_, lotlSource) ->
        val provider = getTrustedListsCertificateByLOTLSource.asProviderFor(lotlSource, trustAnchorCreator)
        IsChainTrusted(validateCertificateChain, provider)
    }
    return IsChainTrustedForContext(trust)
}

/**
 * Creates an instance of [IsChainTrustedForContext] using a trusted list of trust anchors (LoTL) and a file cache loader.
 * The implementation has the following characteristics:
 * - Utilizes a file cache loader to load trusted lists from the file system
 * - Depending on the file cache loader configuration, the file downloader may use an online fetcher to
 *   refresh the file cache.
 * - Given that [FileCacheDataLoader] is blocking, it creates a suspendable and cachable wrapper around it using
 *   [GetTrustedListsCertificateByLOTLSource.fromBlocking].
 *
 * The example creates an [IsChainTrustedForContext] that:
 * - Supports PUB EAA
 * - Caches calls in memory for 10 minutes
 * - Every 10 minutes, the file cache is being used
 * - Every 24 hours, the trusted lists are fetched from the internet
 *
 * ```kotlin
 * IsChainTrustedForContext.usingLoTL(
 *   IsChainTrustedForContext.usingLoTL(
 *     dssAdapter = DSSAdapter.usingFileCacheDataLoader(
 *         fileCacheExpiration = 24.hours,
 *         cacheDirectory = createTempDirectory("lotl-cache"),
 *     ),
 *     sourcePerVerification = buildMap {
 *         put(VerificationContext.PubEAA, lotlSource(PUB_EAA_SVC_TYPE))
 *     },
 *     validateCertificateChain = ValidateCertificateChainJvm(customization = {
 *         isRevocationEnabled = false
 *     }),
 *     coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
 *     coroutineDispatcher = Dispatchers.IO,
 *     ttl = 10.seconds,
 *  )
 * )
 * ````
 *

 * @param sourcePerVerification a map of verification contexts to trusted list sources
 * @param validateCertificateChain the function used to validate a given certificate chain
 *        Defaults to [ValidateCertificateChainJvm.Default]
 * @param trustAnchorCreator a function that creates a trust anchor from a [CertificateToken]
 *        Defaults to [DSSTrustAnchorCreator]
 * @param dssAdapter the DSS adapter to use for retrieving the trusted lists certificate source
 *        Defaults to [DSSAdapter.Default]
 * @param clock the clock used to retrieve the current time
 *        Defaults to [Clock.System]
 * @param coroutineScope the overall scope that controls [fileCacheLoader]
 *        Defaults to [GetTrustedListsCertificateByLOTLSource.DEFAULT_SCOPE]
 * @param coroutineDispatcher the coroutine dispatcher for executing the blocking logic of [fileCacheLoader]
 *        Defaults to [GetTrustedListsCertificateByLOTLSource.DEFAULT_DISPATCHER]
 * @param ttl the time-to-live duration for caching the certificate source.
 *
 */
public fun IsChainTrustedForContext.Companion.usingLoTL(
    sourcePerVerification: Map<VerificationContext, LOTLSource>,
    validateCertificateChain: ValidateCertificateChain<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainJvm.Default,
    trustAnchorCreator: TrustAnchorCreator<CertificateToken, TrustAnchor> = DSSTrustAnchorCreator,
    dssAdapter: DSSAdapter = DSSAdapter.Default,
    clock: Clock = Clock.System,
    coroutineScope: CoroutineScope = GetTrustedListsCertificateByLOTLSource.DEFAULT_SCOPE,
    coroutineDispatcher: CoroutineDispatcher = GetTrustedListsCertificateByLOTLSource.DEFAULT_DISPATCHER,
    ttl: Duration,
): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val getTrustedListsCertificateByLOTLSource =
        with(DSSAdapterOps) {
            dssAdapter.asGetTrustedListsCertificateByLOTLSource(
                coroutineScope = coroutineScope,
                coroutineDispatcher = coroutineDispatcher,
                expectedTrustSourceNo = sourcePerVerification.size,
                ttl = ttl,
                clock = clock,
            )
        }
    return usingLoTL(
        validateCertificateChain,
        trustAnchorCreator,
        sourcePerVerification,
        getTrustedListsCertificateByLOTLSource,
    )
}
