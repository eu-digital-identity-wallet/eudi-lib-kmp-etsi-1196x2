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
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Creates an instance of [IsChainTrusted] using a trusted list of trust anchors (LoTL).
 *
 * @param validateCertificateChain the function used to validate a given certificate chain
 * @param trustAnchorCreator a function that creates a trust anchor from a certificate
 * @param getTrustedListsCertificateSource a suspend function that retrieves the trusted lists certificate source containing trust anchors
 * @return an [IsChainTrusted] instance configured to validate certificate chains using the provided trusted list
 *
 * @see TrustedListsCertificateSource
 * @see GetTrustedListsCertificateByLOTLSource
 */
public fun IsChainTrusted.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.TRUST_ANCHOR_WITH_NO_NAME_CONSTRAINTS,
    getTrustedListsCertificateSource: suspend () -> TrustedListsCertificateSource,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> =
    IsChainTrusted(validateCertificateChain) {
        getTrustedListsCertificateSource().trustAnchors(trustAnchorCreator)
    }

internal fun TrustedListsCertificateSource.trustAnchors(
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
): List<TrustAnchor> = certificates.map { certToken -> trustAnchorCreator(certToken.certificate) }

public fun IsChainTrustedForContext.Companion.usingLoTL(
    validateCertificateChain: ValidateCertificateChain<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.TRUST_ANCHOR_WITH_NO_NAME_CONSTRAINTS,
    sourcePerVerification: Map<VerificationContext, LOTLSource>,
    getTrustedListsCertificateByLOTLSource: GetTrustedListsCertificateByLOTLSource,
): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val trust = sourcePerVerification.mapValues { (_, lotlSource) ->
        val provider = getTrustedListsCertificateByLOTLSource.asProviderFor(lotlSource, trustAnchorCreator)
        IsChainTrusted(validateCertificateChain, provider)
    }
    return IsChainTrustedForContext(trust)
}

public fun IsChainTrustedForContext.Companion.usingLoTL(
    fileCacheLoader: FileCacheDataLoader,
    sourcePerVerification: Map<VerificationContext, LOTLSource>,
    validateCertificateChain: ValidateCertificateChain<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.TRUST_ANCHOR_WITH_NO_NAME_CONSTRAINTS,
    clock: Clock = Clock.System,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ttl: Duration = 10.minutes,
): IsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val dssLoader = DSSLoader(fileCacheLoader)
    val getTrustedListsCertificateByLOTLSource = dssLoader
        .getTrustedListsCertificateByLOTLSource(
            coroutineScope = coroutineScope,
            coroutineDispatcher = coroutineDispatcher,
            expectedTrustSourceNo = sourcePerVerification.size,
            ttl = ttl,
            clock = clock,
        )
    return usingLoTL(
        validateCertificateChain,
        trustAnchorCreator,
        sourcePerVerification,
        getTrustedListsCertificateByLOTLSource,
    )
}
