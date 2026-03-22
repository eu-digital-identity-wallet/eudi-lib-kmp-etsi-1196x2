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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.CertOps
import eu.europa.ec.eudi.etsi119602.consultation.CertOps.toX509Certificate
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412Part6
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.KeyUsage
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EUPIDSigningCertificateProfileTests {

    private suspend fun evaluateCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        CertificateProfileValidatorJVM.validate(pidSigningCertificateProfile(), certificate)

    private val ca = CertOps.genTrustAnchor(
        sigAlg = "SHA256withECDSA",
        subject = X500Name("CN=Test CA"),
        policyOids = null,
        pathLenConstraint = null,
    )

    private fun genCAIssuedEndEntityCertificate(
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = null,
        caIssuersUri: String? = null,
        ocspUri: String? = null,
        subjectAltNameUri: String? = null,
        keyUsage: KeyUsage,
    ): X509Certificate {
        val sigAlg = "SHA256withECDSA"
        val (caKeyPair, caCert) = ca
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = sigAlg,
            subject = X500Name("CN=PID Provider Test"),
            keyUsage = keyUsage,
            qcStatements = qcStatements,
            policyOids = policyOids,
            caIssuersUri = caIssuersUri,
            ocspUri = ocspUri,
            subjectAltNameUri = subjectAltNameUri,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `CA-issued certificate should require QCStatement`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = null,
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())

        // Should fail QCStatement check (end-entity cert without QCStatement)
        constraintEvaluation.assertSingleViolation {
            it.contains("QCStatement")
        }
    }

    @Test
    fun `CA-issued certificate should require QCStatement ID_ETSI_QCT_PID`() = runTest {
        // Generate a certificate with the wrong QCStatement type (Wallet instead of PID)
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_WAL to true), // Wrong type
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - wrong QCStatement type
        assertFalse(constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        assertEquals(1, constraintEvaluation.violations.size)
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("QCStatement") })
    }

    @Test
    fun `CA-issued certificate should require QCStatement ID_ETSI_QCT_PID compliance`() = runTest {
        // Generate certificate with non-compliant QCStatement
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to false), // Non-compliant
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - QCStatement is present but not marked as compliant
        assertFalse(constraintEvaluation.isMet(), "Non-compliant QCStatement should fail")
        constraintEvaluation.assertSingleViolation { it.contains("compliant") }
    }

    @Test
    fun `CA-issued certificate should require AIA`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("AIA") }
    }

    @Test
    fun `CA Issued certificate should be valid`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "$constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate should require end-entity not CA`() = runTest {
        val (_, caCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = X500Name("CN=Self-Signed PID Provider CA Test"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = listOf("1.2.3.4.5"),
            pathLenConstraint = null,
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
        )
        val certificate = caCertHolder.toX509Certificate()

        // Validate as PID Provider - should fail because it's a CA certificate
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        println(constraintEvaluation)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("CA", ignoreCase = true) }
    }

    @Test
    fun `CA-issued certificate should require digitalSignature key usage`() = runTest {
        // Generate an end-entity certificate with keyCertSign instead of digitalSignature
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.keyCertSign), // wrong key usage
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    //
    // Self-signed PID Provider Certificate Tests
    //

    private fun genSelfSignedEndEntityCertificate(
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = null,
        keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
    ): X509Certificate {
        val sigAlg = "SHA256withECDSA"
        val (_, certHolder) = CertOps.genSelfSignedEndEntityCertificate(
            sigAlg = sigAlg,
            subject = X500Name("CN=Self-Signed PID Provider Test"),
            keyUsage = keyUsage,
            qcStatements = qcStatements,
            policyOids = policyOids,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `Self-signed certificate should require end-entity not CA`() = runTest {
        // Generate a self-signed CA certificate (cA=TRUE) instead of end-entity
        val (_, caCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = X500Name("CN=Self-Signed PID Provider CA Test"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = listOf("1.2.3.4.5"),
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            pathLenConstraint = null,
        )
        val certificate = caCertHolder.toX509Certificate()

        // Validate as PID Provider - should fail because it's a CA certificate
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "CA certificate should fail PID Provider validation")
        constraintEvaluation.assertSingleViolation { it.contains("CA", ignoreCase = true) }
    }

    @Test
    fun `Self-signed certificate should require digitalSignature key usage`() = runTest {
        // Generate a self-signed certificate with keyCertSign instead of digitalSignature
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.keyCertSign), // wrong key usage
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate without digitalSignature should fail")
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    @Test
    fun `Self-signed certificate should require QCStatement`() = runTest {
        // Generate a self-signed certificate without QCStatement
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = null, // No QCStatement
            policyOids = listOf("1.2.3.4.5"),
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate without QCStatement should fail")
        constraintEvaluation.assertSingleViolation { it.contains("QCStatement", ignoreCase = true) }
    }

    @Test
    fun `Self-signed certificate should require QCStatement ID_ETSI_QCT_PID`() = runTest {
        // Generate a self-signed certificate with wrong QCStatement type (Wallet instead of PID)
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_WAL to true), // Wrong type
            policyOids = listOf("1.2.3.4.5"),
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        constraintEvaluation.assertSingleViolation { it.contains("QCStatement", ignoreCase = true) }
    }

    @Test
    fun `Self-signed certificate should be valid`() = runTest {
        // Generate a valid self-signed certificate with all requirements
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Valid self-signed certificate should pass: $constraintEvaluation")
    }
}
