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

import eu.europa.ec.eudi.etsi119602.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi119602.consultation.TrustSource
import eu.europa.ec.eudi.etsi119602.consultation.ValidateCertificateChainJvm
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

public class CertTrustAnchorsFromLoTLUsingDSS(
    private val dss: Map<TrustSource.LoTL, TrustedListsCertificateSource>,
) : suspend (TrustSource.LoTL) -> List<TrustAnchor>? {
    override suspend fun invoke(trustSource: TrustSource.LoTL): List<TrustAnchor>? =
        dss[trustSource]?.let { certSource ->
            certSource.certificates.orEmpty().map { TrustAnchor(it.certificate, null) }
        }
}

public fun IsChainTrusted.Companion.jvmUsingLoTLsFromDss(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    getTrustAnchorsFromLoTL: CertTrustAnchorsFromLoTLUsingDSS,
): IsChainTrusted<List<X509Certificate>, TrustSource.LoTL> =
    IsChainTrusted(validateCertificateChain, getTrustAnchorsFromLoTL)
