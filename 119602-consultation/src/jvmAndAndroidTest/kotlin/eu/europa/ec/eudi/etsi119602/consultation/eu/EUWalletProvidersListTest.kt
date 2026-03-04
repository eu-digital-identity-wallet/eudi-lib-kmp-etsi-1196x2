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
import eu.europa.ec.eudi.etsi119602.consultation.evaluateCertificateConstraints
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.TrustAnchor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for Wallet Provider certificate constraints (ETSI TS 119 602 Annex E).
 */
class EUWalletProvidersListTest {

    private val cnWalletProvider = X500Name("CN=Wallet Provider Test")

    @Test
    fun `Wallet Provider validator should validate end-entity certificate`() = runTest {
        // Generate a trust anchor (end-entity certificate for Wallet Provider)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWalletProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as Wallet Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWalletProvidersList)

        assertTrue(!constraintEvaluation.isMet())

        // Should pass basic constraints (end-entity) and key usage (digitalSignature)
        // Will fail QCStatement and Certificate Policy
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("QCStatement") } ||
                constraintEvaluation.violations.any { it.reason.contains("certificate policies") },
            "Expected failure for missing QCStatement or Certificate Policy",
        )
    }

    @Test
    fun `Wallet Provider validator should accept certificate with valid QCStatement`() = runTest {
        // Generate certificate with id-etsi-qct-wal QCStatement
        val keyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWalletProvider).first
        val certHolder = CertOps.createTrustAnchorWithQCStatement(
            keyPair = keyPair,
            sigAlg = "SHA256withECDSA",
            name = cnWalletProvider,
            qcType = ETSI119412.ID_ETSI_QCT_WAL,
            qcCompliance = true,
            policyOids = listOf(ETSI119412.ID_ETSI_QCT_WAL),
        )
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as Wallet Provider
        val constraintEvaluation = trustAnchor.evaluateCertificateConstraints(EUWalletProvidersList)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "Wallet Provider certificate with valid QCStatement should pass")
    }
}
