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

import java.math.BigInteger
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

public data class X509CertificateIdentify(val subject: X500Principal, val serialNumber: BigInteger)

private fun X509Certificate.identity(): X509CertificateIdentify =
    X509CertificateIdentify(subjectX500Principal, serialNumber)

public val ValidateCertificateChainUsingDirectTrustJvm: ValidateCertificateChainUsingDirectTrust<List<X509Certificate>, TrustAnchor, X509CertificateIdentify> =
    ValidateCertificateChainUsingDirectTrust(
        headCertificateId = { it.first().identity() },
        trustToCertificateId = { it.trustedCert.identity() },
    )
