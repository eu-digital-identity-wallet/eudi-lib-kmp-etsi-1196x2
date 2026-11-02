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
import org.bouncycastle.asn1.x509.KeyUsage
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertTrue

class KeyUsageConstraintTest {

    private val x500Name = X500Name("CN=Foo")
    private val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)

    @Test
    fun `KeyUsageConstraint should reject when digitalSignature not set`() = runTest {
        // Generate a CA certificate (has keyCertSign, not digitalSignature)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", x500Name)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for digitalSignature

        val profile = certificateProfile { keyUsageDigitalSignature() }

        // Validate
        val constraintEvaluation = certificateProfileValidator.validate(profile, certificate)

        // Should fail - CA certificate has keyCertSign, not digitalSignature
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("keyUsage") })
    }

    private fun caIssuedEndEntity(keyUsage: KeyUsage?): X509Certificate {
        val (caKeyPair, caCertHolder) = CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=Test CA"))
        val (_, eeCertHolder) = CertOps.genEndEntity(
            caCertHolder,
            caKeyPair.private,
            "SHA256withECDSA",
            x500Name,
            keyUsage = keyUsage,
        )
        return eeCertHolder.toX509Certificate()
    }

    private suspend fun evaluateKeyUsageOneOfETSI319412Types(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation {
        val profile = certificateProfile { keyUsageOneOfETSI319412Types() }
        return certificateProfileValidator.validate(profile, certificate)
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should accept Type A (nonRepudiation only)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.nonRepudiation))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(constraintEvaluation.isMet(), "Type A should be accepted: $constraintEvaluation")
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should accept Type B (nonRepudiation + digitalSignature)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.nonRepudiation or KeyUsage.digitalSignature))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(constraintEvaluation.isMet(), "Type B should be accepted: $constraintEvaluation")
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should accept Type C (digitalSignature only)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.digitalSignature))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(constraintEvaluation.isMet(), "Type C should be accepted: $constraintEvaluation")
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should accept Type F (nonRepudiation + digitalSignature + keyEncipherment)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.nonRepudiation or KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(constraintEvaluation.isMet(), "Type F (keyEncipherment) should be accepted: $constraintEvaluation")
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should accept Type F (nonRepudiation + digitalSignature + keyAgreement)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.nonRepudiation or KeyUsage.digitalSignature or KeyUsage.keyAgreement))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(constraintEvaluation.isMet(), "Type F (keyAgreement) should be accepted: $constraintEvaluation")
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should reject CA keyUsage (keyCertSign + cRLSign)`() = runTest {
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", x500Name)
        val certificate = certHolder.toX509Certificate()
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("keyUsage", ignoreCase = true) })
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should reject mixed keyUsage (digitalSignature + keyCertSign)`() = runTest {
        val certificate = caIssuedEndEntity(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign))
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("keyUsage", ignoreCase = true) })
    }

    @Test
    fun `keyUsageOneOfETSI319412Types should reject certificate without keyUsage extension`() = runTest {
        val certificate = caIssuedEndEntity(keyUsage = null)
        val constraintEvaluation = evaluateKeyUsageOneOfETSI319412Types(certificate)
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("does not contain keyUsage", ignoreCase = true) })
    }
}
