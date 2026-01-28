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

import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi1196x2.consultation.JvmSecurity
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

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
