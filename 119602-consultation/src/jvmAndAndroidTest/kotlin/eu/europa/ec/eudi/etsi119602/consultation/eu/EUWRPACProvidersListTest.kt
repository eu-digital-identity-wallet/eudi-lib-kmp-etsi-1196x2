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
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for WRPAC Provider certificate constraints (ETSI TS 119 602 Annex F).
 */
@Ignore("Further investigation needed")
class EUWRPACProvidersListTest {

    private val cnWrpacProvider = X500Name("CN=WRPAC Provider Test")

    private val evaluateCertificateConstraints =
        checkNotNull(EUWRPACProvidersList.endEntityCertificateConstrainsEvaluator(CertificateOperationsJvm))

    @Test
    fun `WRPAC Provider validator should validate CA certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertTrue(!constraintEvaluation.isMet())
        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (certificate lacks policy OID)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("certificate policies") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `WRPAC Provider validator should accept NCP-n policy`() = runTest {
        // Generate CA certificate with NCP-n-eudiwrp policy
        val (_, certHolder) = CertOps.genCACertifiicateWithPolicy(
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.NCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has valid NCP-n policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with NCP-n policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept NCP-l policy`() = runTest {
        // Generate CA certificate with NCP-l-eudiwrp policy
        val (_, certHolder) = CertOps.genCACertifiicateWithPolicy(
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.NCP_L_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has valid NCP-l policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with NCP-l policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept QCP-n policy`() = runTest {
        // Generate CA certificate with QCP-n-eudiwrp policy
        val (_, certHolder) = CertOps.genCACertifiicateWithPolicy(
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.QCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has valid QCP-n policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with QCP-n policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should accept QCP-l policy`() = runTest {
        // Generate CA certificate with QCP-l-eudiwrp policy
        val (_, certHolder) = CertOps.genCACertifiicateWithPolicy(
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(ETSI119411.QCP_L_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has valid QCP-l policy
        assertTrue(constraintEvaluation.isMet(), "WRPAC Provider certificate with QCP-l policy should pass")
    }

    @Test
    fun `WRPAC Provider validator should reject unknown policy`() = runTest {
        // Create certificate with unknown policy OID (not in ETSI119411.ALL)
        val unknownPolicyOid = "0.4.0.194118.999.999"
        val (_, certHolder) = CertOps.genCACertifiicateWithPolicy(
            sigAlg = "SHA256withECDSA",
            name = cnWrpacProvider,
            policyOids = listOf(unknownPolicyOid),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC Provider - should reject unknown policy
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Verify rejection
        assertTrue(!constraintEvaluation.isMet(), "Certificate with unknown policy should be rejected")
    }
}
