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
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for PID Provider certificate constraints (ETSI TS 119 602 Annex D).
 */
class EUPIDProvidersEndEntityCertificateTests {

    private val cnPidProvider = X500Name("CN=PID Provider Test")
    val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)
    private suspend fun evaluateCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        certificateProfileValidator.validate(pidProviderCertificateProfile(), certificate)

    @Test
    fun `PID Provider validator should validate end-entity certificate`() = runTest {
        val (caKeyPair, caCert) = CertOps.genTrustAnchor("SHA256withECDSA", cnPidProvider)
        val (_, certHolder) = CertOps.genEndEntity(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = "SHA256withECDSA",
            subject = cnPidProvider,
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertTrue(!constraintEvaluation.isMet())

        // Should fail QCStatement check (end-entity cert without QCStatement)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("QCStatement") },
            "Expected failure for missing QCStatement",
        )
    }

    @Test
    fun `PID Provider validator should accept certificate with valid QCStatement`() = runTest {
        // Generate certificate with id-etsi-qct-pid QCStatement
        val keyPair = CertOps.generateECPair()
        val certHolder = CertOps.createTrustAnchorWithQCStatement(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnPidProvider,
            qcType = ETSI119412.ID_ETSI_QCT_PID,
            qcCompliance = true,
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "PID Provider certificate with valid QCStatement should pass")
    }

    @Test
    fun `PID Provider validator should reject certificate without QCStatement compliance`() = runTest {
        // Generate certificate with non-compliant QCStatement
        val (_, certHolder) = CertOps.genTrustAnchorWithQCStatement(
            sigAlg = "SHA256withECDSA",
            name = cnPidProvider,
            qcType = ETSI119412.ID_ETSI_QCT_PID,
            qcCompliance = false, // Non-compliant
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - QCStatement is present but not marked as compliant
        assertTrue(!constraintEvaluation.isMet(), "Non-compliant QCStatement should fail")
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("compliant") },
            "Expected failure for non-compliant QCStatement",
        )
    }

    @Test
    fun `PID Provider validator should reject certificate with wrong QCStatement type`() = runTest {
        // Generate certificate with wrong QCStatement type (Wallet instead of PID)
        val (_, certHolder) = CertOps.genTrustAnchorWithQCStatement(
            sigAlg = "SHA256withECDSA",
            name = cnPidProvider,
            qcType = ETSI119412.ID_ETSI_QCT_WAL, // Wrong type
            qcCompliance = true,
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - wrong QCStatement type
        assertTrue(!constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("QCStatement") })
    }

    @Test
    fun `CA-issued PID certificate with AIA should pass validation`() = runTest {
        // Generate CA
        val (_, caCert) = CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=Test CA"))

        // Generate EE with AIA and QCStatement
        val (_, eeCertHolder) = CertOps.genEndEntityWithAIA(
            signerCert = caCert,
            sigAlg = "SHA256withECDSA",
            subject = cnPidProvider,
            qcType = ETSI119412.ID_ETSI_QCT_PID,
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
        )
        val certificate = eeCertHolder.toX509Certificate()

        // Validate as PID Provider (should pass AIA check)
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has AIA and QCStatement
        assertTrue(constraintEvaluation.isMet(), "CA-issued PID certificate with AIA should pass")
    }
}
