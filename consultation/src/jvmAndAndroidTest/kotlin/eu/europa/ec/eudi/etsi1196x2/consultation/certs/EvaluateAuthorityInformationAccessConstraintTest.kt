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
package eu.europa.ec.eudi.etsi1196x2.consultation.certs

import eu.europa.ec.eudi.etsi1196x2.consultation.CertOps
import eu.europa.ec.eudi.etsi1196x2.consultation.CertOps.toX509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertTrue

class EvaluateAuthorityInformationAccessConstraintTest {

    private val x500Name = X500Name("CN=Foo")

    private val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)

    @Test
    fun `AIA constraint should accept self-signed certificate without AIA`() = runTest {
        // Generate a self-signed certificate (trust anchor)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", x500Name)
        val certificate = certHolder.toX509Certificate()

        val profile = certificateProfile { authorityInformationAccessIfCAIssued() }
        val evaluation = certificateProfileValidator.validate(profile, certificate)
        assertTrue(evaluation.isMet(), "Self-signed certificate should NOT require AIA")
    }

    @Test
    fun `AIA constraint should reject CA-issued certificate without AIA`() = runTest {
        // Generate CA
        val (caKeyPair, caCertHolder) = CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=Test CA"))

        // Generate EE without AIA
        val eeKeyPair = CertOps.genTrustAnchor("SHA256withECDSA", x500Name).first
        val eeCertHolder = CertOps.createEndEntity(
            caCertHolder,
            caKeyPair.private,
            "SHA256withECDSA",
            eeKeyPair.public,
            x500Name,
        )
        val certificate = eeCertHolder.toX509Certificate()

        val profile = certificateProfile { authorityInformationAccessIfCAIssued() }

        val evaluation = certificateProfileValidator.validate(profile, certificate)
        assertTrue(!evaluation.isMet(), "CA-issued certificate should require AIA")
        assertTrue(evaluation.violations.any { it.reason.contains("AIA") })
    }
}
