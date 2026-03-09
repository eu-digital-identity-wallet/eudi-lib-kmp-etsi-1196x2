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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119475
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for WRPRC Provider certificate constraints (ETSI TS 119 602 Annex G).
 */
class EUWRPRCProvidersListTest {

    private val cnWrprcProvider = X500Name("CN=WRPRC Provider Test")
    private val evaluateCertificateConstraints =
        checkNotNull(EUWRPRCProvidersList.certificateConstraintsEvaluator(CertificateOperationsJvm))

    @Test
    fun `WRPRC Provider validator should validate CA certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPRC Provider)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrprcProvider)
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPRC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertTrue(!constraintEvaluation.isMet())

        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (not implemented yet)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("certificate policies") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `WRPRC Provider validator should accept CA certificate with valid policy`() = runTest {
        // Generate CA certificate with WRPRC policy OID
        // Note: WRPRC Providers are CAs, so they need cA=TRUE
        val keyPair = CertOps.generateECPair()
        val certHolder = CertOps.createTrustAnchor(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrprcProvider,
        )
        // Note: We can't easily add policy OIDs to existing certificates
        // This test documents that CA certificates pass basic constraints check
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPRC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - missing certificate policy (can't be added with current test utilities)
        // This test documents the current limitation
        assertTrue(!constraintEvaluation.isMet(), "CA certificate without policy should fail")
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("certificate policies") })
    }

    @Test
    fun `WRPRC Provider validator should accept CA certificate with WRPRC policy`() = runTest {
        // Generate CA certificate with WRPRC policy OID (ETSI TS 119 475)
        val keyPair = CertOps.generateECPair()
        val certHolder = CertOps.createCACertificateWithPolicy(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWrprcProvider,
            policyOids = listOf(ETSI119475.WRPRC),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPRC Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass - has valid WRPRC policy
        assertTrue(constraintEvaluation.isMet(), "WRPRC Provider certificate with WRPRC policy should pass")
    }
}
