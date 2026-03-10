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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for Wallet Provider certificate constraints (ETSI TS 119 602 Annex E).
 */
class EUWalletProvidersEndEntityCertificateTest {

    private val cnWalletProvider = X500Name("CN=Wallet Provider Test")
    private val evaluateCertificateConstraints =
        checkNotNull(EUWalletProvidersList.endEntityCertificateConstrainsEvaluator(CertificateOperationsJvm))

    @Test
    fun `Wallet Provider validator should validate end-entity certificate`() = runTest {
        val caKeyPair = CertOps.genTrustAnchor("SHA256withECDSA", cnWalletProvider)
        val (_, certHolder) = CertOps.genEndEntity(
            signerCert = caKeyPair.second,
            signerKey = caKeyPair.first.private,
            sigAlg = "SHA256withECDSA",
            subject = cnWalletProvider,
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as Wallet Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(!constraintEvaluation.isMet())

        // Should fail QCStatement check (end-entity cert without QCStatement)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("QCStatement") },
            "Expected failure for missing QCStatement",
        )
    }

    @Test
    fun `Wallet Provider validator should accept certificate with valid QCStatement`() = runTest {
        // Generate certificate with id-etsi-qct-wal QCStatement
        val (_, certHolder) = CertOps.genTrustAnchorWithQCStatement(
            sigAlg = "SHA256withECDSA",
            name = cnWalletProvider,
            qcType = ETSI119412.ID_ETSI_QCT_WAL,
            qcCompliance = true,
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as Wallet Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "Wallet Provider certificate with valid QCStatement should pass")
    }
}
