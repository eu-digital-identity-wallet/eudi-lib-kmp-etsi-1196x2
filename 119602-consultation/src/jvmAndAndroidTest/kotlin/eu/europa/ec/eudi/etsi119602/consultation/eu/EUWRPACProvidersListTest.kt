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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411
import eu.europa.ec.eudi.etsi119602.consultation.evaluateCertificateConstraints
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateBasicConstraintsConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.TrustAnchor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for WRPAC Provider certificate constraints (ETSI TS 119 602 Annex F).
 */
class EUWRPACProvidersListTest {

    private val cnWrpacProvider = X500Name("CN=WRPAC Provider Test")

    @Test
    fun `WRPAC Provider validator should validate CA certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)
        assertTrue(!constraintEvaluation.isMet())
        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (certificate lacks policy OID)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("certificate policies") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `BasicConstraintsConstraint should reject CA when end-entity expected`() = runTest {
        // Generate a CA certificate (trust anchor)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for end-entity
        val constraint = EvaluateBasicConstraintsConstraint.requireEndEntity(
            getBasicConstraints = CertificateOperationsJvm::getBasicConstraints,
        )

        // Validate
        val constraintEvaluation = constraint(certificate)

        // Should fail - CA certificate when end-entity expected
        assertTrue(!constraintEvaluation.isMet())

        assertTrue(constraintEvaluation.violations.any { it.reason.contains("CA") })
    }

    @Test
    fun `KeyUsageConstraint should reject when digitalSignature not set`() = runTest {
        // Generate a CA certificate (has keyCertSign, not digitalSignature)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for digitalSignature
        val constraint = KeyUsageConstraint.requireDigitalSignature(
            getKeyUsage = CertificateOperationsJvm::getKeyUsage,
        )

        // Validate
        val constraintEvaluation = constraint(certificate)

        // Should fail - CA certificate has keyCertSign, not digitalSignature
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("keyUsage") })
    }

    @Test
    fun `BasicConstraintsConstraint should validate pathLenConstraint for CA certificates`() = runTest {
        // Generate a CA certificate (trust anchor) without pathLenConstraint
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for CA with maxPathLen = 2
        val constraint = EvaluateBasicConstraintsConstraint.requireCa(
            maxPathLen = 2,
            getBasicConstraints = CertificateOperationsJvm::getBasicConstraints,
        )

        // Validate - should fail because CA certificate has no pathLenConstraint
        val constraintEvaluation = constraint(certificate)

        // Should fail - CA certificate missing pathLenConstraint
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("pathLenConstraint") })
    }

    @Test
    fun `WRPAC Provider validator should accept NCP-n policy`() = runTest {
        // Generate CA certificate with NCP-n-eudiwrp policy
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider).first
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.NCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)

        // Should pass - has valid NCP-n policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with NCP-n policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept NCP-l policy`() = runTest {
        // Generate CA certificate with NCP-l-eudiwrp policy
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider).first
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.NCP_L_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)

        // Should pass - has valid NCP-l policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with NCP-l policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept QCP-n policy`() = runTest {
        // Generate CA certificate with QCP-n-eudiwrp policy
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider).first
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.QCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)

        // Should pass - has valid QCP-n policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with QCP-n policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept QCP-l policy`() = runTest {
        // Generate CA certificate with QCP-l-eudiwrp policy
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider).first
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.QCP_L_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)

        // Should pass - has valid QCP-l policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with QCP-l policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should reject unknown policy`() = runTest {
        // Create certificate with unknown policy OID (not in ETSI119411.ALL)
        val unknownPolicyOid = "0.4.0.1949.999.999"
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider).first
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(unknownPolicyOid),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider - should reject unknown policy
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWRPACProvidersList)

        // Verify rejection
        assertTrue(!constraintEvaluation.isMet(), "Certificate with unknown policy should be rejected")
    }
}
