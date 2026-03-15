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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Wallet Provider certificate constraints (ETSI TS 119 602 Annex E).
 */
class EUWalletProviderCertificateTest {

    private suspend fun evaluateCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        CertificateProfileValidatorJVM.validate(walletProviderCertificateProfile(), certificate)

    private val ca = CertOps.genTrustAnchor(
        sigAlg = "SHA256withECDSA",
        subject = X500Name("CN=Test CA"),
        policyOids = null,
        pathLenConstraint = null,
    )

    private fun genCAIssuedEndEntityCertificate(
        qcTypeAndCompliance: Pair<String, Boolean>? = null,
        policyOids: List<String>? = null,
        caIssuersUri: String? = null,
        ocspUri: String? = null,
    ): X509Certificate {
        val (caKeyPair, caCert) = ca
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = "SHA256withECDSA",
            subject = X500Name("CN=Test Wallet Provider"),
            qcTypeAndCompliance = qcTypeAndCompliance,
            policyOids = policyOids,
            caIssuersUri = caIssuersUri,
            ocspUri = ocspUri,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `CA-issued certificate should require QCStatement`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcTypeAndCompliance = null, // No QCStatement
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
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
    fun `CA-issued certificate should require QCStatement ID_ETSI_QCT_WAL`() = runTest {
        // Generate a certificate with the wrong QCStatement type (Wallet instead of PID)
        val certificate = genCAIssuedEndEntityCertificate(
            qcTypeAndCompliance = ETSI119412.ID_ETSI_QCT_PID to true, // Wrong type
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - wrong QCStatement type
        assertFalse(constraintEvaluation.isMet())
        assertEquals(1, constraintEvaluation.violations.size)
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("QCStatement") })
    }

    @Test
    fun `CA-issued certificate should require QCStatement ID_ETSI_QCT_WAL compliance`() = runTest {
        // Generate certificate with non-compliant QCStatement
        val certificate = genCAIssuedEndEntityCertificate(
            qcTypeAndCompliance = ETSI119412.ID_ETSI_QCT_WAL to false, // Non-compliant
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
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
            qcTypeAndCompliance = ETSI119412.ID_ETSI_QCT_WAL to true,
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("AIA") }
    }

    @Test
    fun `CA Issued certificate should be valid`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcTypeAndCompliance = ETSI119412.ID_ETSI_QCT_WAL to true,
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "$constraintEvaluation")
    }

    // TODO: Add tests for CA-issued Wallet Provider Certificate
    //  - End-entity not CA
    //  - key usage must be digital signature

    // TODO: Add tests for Self-signed Wallet Provider Certificate
    //  - End-entity not CA
    //  - QCStatement MUST NOT be present (Not sure about that)
    //  - AIA MUST NOT be present (Not sure about that)
    //  - key usage must be digital signature
}
