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
package eu.europa.ec.eudi.etsi1196x2.consultation

import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * A default implementation of [ValidateCertificateChainUsingDirectTrust] for the JVM.
 * Uses [X509Certificate.getEncoded] to get the DER representation of the certificate
 * Compares the DER encoded form of the leaf certificate of the chain each trust anchor
 */
public val ValidateCertificateChainUsingDirectTrustJvm:
    ValidateCertificateChainUsingDirectTrust<List<X509Certificate>, TrustAnchor> =
    ValidateCertificateChainUsingDirectTrust.comparingDER(
        headDer = { chain ->
            val leaf = chain.firstOrNull()
            requireNotNull(leaf) { "Chain cannot be empty" }
            leaf.encoded
        },
        trustAnchorDer = { trustAnchor ->
            val cert = trustAnchor.trustedCert
            requireNotNull(cert) { "Trust anchor missing certificate\n$trustAnchor" }
            cert.encoded
        },
    )
