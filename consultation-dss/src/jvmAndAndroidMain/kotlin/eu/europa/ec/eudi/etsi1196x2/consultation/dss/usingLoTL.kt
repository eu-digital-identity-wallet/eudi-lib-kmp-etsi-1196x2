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
import eu.europa.ec.eudi.etsi1196x2.consultation.JvmSecurity
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustSource
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

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
        IsChainTrusted.Companion.usingLoTL(
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
}
