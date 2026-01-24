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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.*
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

public fun IsChainTrusted.Companion.forLoTLUsingDSS(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.trustAnchorCreator(),
    getTrustedListsCertificateSource: suspend () -> TrustedListsCertificateSource,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> =
    IsChainTrusted(validateCertificateChain) {
        val source = getTrustedListsCertificateSource()
        source.certificates.orEmpty().map { trustAnchorCreator(it.certificate) }
    }

public fun IsChainTrustedForContext.Companion.forLoTLUsingDSS(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    config: Map<VerificationContext, Pair<TrustSource.LoTL, TrustAnchorCreator<X509Certificate, TrustAnchor>?>>,
    getTrustedListsCertificateSourceFor: GetTrustedListsCertificateSourceFor,
): DefaultIsChainTrustedForContext<List<X509Certificate>, TrustAnchor> {
    val trust = config.mapValues { (_, value) ->
        val (trustSource, trustAnchorCreator) = value
        IsChainTrusted.forLoTLUsingDSS(
            validateCertificateChain,
            trustAnchorCreator ?: JvmSecurity.trustAnchorCreator(),
        ) {
            getTrustedListsCertificateSourceFor(trustSource)
        }
    }
    return DefaultIsChainTrustedForContext(trust)
}

public fun interface GetTrustedListsCertificateSourceFor {
    public suspend operator fun invoke(trustSource: TrustSource.LoTL): TrustedListsCertificateSource
}
