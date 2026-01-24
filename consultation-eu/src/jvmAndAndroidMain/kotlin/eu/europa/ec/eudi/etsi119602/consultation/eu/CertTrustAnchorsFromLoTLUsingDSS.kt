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

import eu.europa.ec.eudi.etsi119602.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi119602.consultation.eu.CertTrustAnchorsFromLoTLUsingDSS.Companion.DefaultTrustAnchorCreator
import eu.europa.ec.eudi.etsi119602.consultation.jvm
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

internal class CertTrustAnchorsFromLoTLUsingDSS(
    private val dssTrustedListsCertificateSource: TrustedListsCertificateSource,
    private val trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = DefaultTrustAnchorCreator,
) : () -> List<TrustAnchor> {

    override fun invoke(): List<TrustAnchor> =
        dssTrustedListsCertificateSource.certificates
            .orEmpty()
            .map { trustAnchorCreator(it.certificate) }

    internal companion object {
        val DefaultTrustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>
            get() = TrustAnchorCreator.jvm()
    }
}

public fun TrustedListsCertificateSource.asTrustAnchorsProvider(
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = DefaultTrustAnchorCreator,
): () -> List<TrustAnchor> =
    CertTrustAnchorsFromLoTLUsingDSS(this, trustAnchorCreator)
