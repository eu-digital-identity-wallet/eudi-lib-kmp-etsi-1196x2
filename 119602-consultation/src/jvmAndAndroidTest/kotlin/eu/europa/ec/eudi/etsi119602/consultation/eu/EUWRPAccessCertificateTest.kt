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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EUWRPAccessCertificateTest {

    private val cnWalletRelyingParty = X500Name("CN=Wallet Relying Party")

    private val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)

    private suspend fun evaluateEndEntityCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        certificateProfileValidator.validate(wrpacEndEntityCertificateProfile(), certificate)

    private val wrpacProvider = CertOps.genTrustAnchor(
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
        val (caKeyPair, caCert) = wrpacProvider
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = "SHA256withECDSA",
            subject = X500Name("CN=Test Wallet Relying Party"),
            qcTypeAndCompliance = qcTypeAndCompliance,
            policyOids = policyOids,
            caIssuersUri = caIssuersUri,
            ocspUri = ocspUri,
        )
        return certHolder.toX509Certificate()
    }

    private fun CertificateConstraintEvaluation.Violated.assertSingleViolation(
        message: String? = null,
        assertTrue: (String) -> Boolean,
    ) {
        assertEquals(1, violations.size)
        val violation = violations.first()
        assertTrue(assertTrue(violation.reason), message)
    }

    @Test
    fun `WRPAC should require policy`() = runTest {
        // Generate end-entity certificate without policy

        val certificate = genCAIssuedEndEntityCertificate(
            policyOids = null,
        )

        // Validate as WRPAC end-entity
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)

        // Should fail - missing policy OID
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("certificate policies", ignoreCase = true) }
    }

    @Test
    fun `WRPAC should accept NCP-n policy`() =
        shouldAcceptPolicy(ETSI119411.NCP_N_EUDIWRP)

    @Test
    fun `WRPAC should accept NCP-l policy`() =
        shouldAcceptPolicy(ETSI119411.NCP_L_EUDIWRP)

    @Test
    fun `WRPAC should accept QCP-n policy`() =
        shouldAcceptPolicy(ETSI119411.QCP_N_EUDIWRP)

    @Test
    fun `WRPAC should accept QCP-l policy`() =
        shouldAcceptPolicy(ETSI119411.QCP_L_EUDIWRP)

    fun shouldAcceptPolicy(policyOid: String) = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            policyOids = listOf(policyOid),
        )
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertTrue(constraintEvaluation.isMet())
    }

    @Test
    fun `WRPAC should reject unknown policy`() = runTest {
        // Create an end-entity certificate with unknown policy OID (not in ETSI119411.ALL)
        val unknownPolicyOid = "0.4.0.194118.999.999"
        val certificate = genCAIssuedEndEntityCertificate(
            policyOids = listOf(unknownPolicyOid),
        )

        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation {
            it.contains("do not match any of the required policies")
        }
    }

    @Test
    fun `WRPAC should not be CA certificate`() = runTest {
        val (_, certHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWalletRelyingParty,
            policyOids = listOf(ETSI119411.NCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC end-entity
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)

        // Should fail - CA certificate, not end-entity
        assertFalse(constraintEvaluation.isMet())
        assertTrue(
            constraintEvaluation.violations.any {
                it.reason.contains("Certificate type mismatch", ignoreCase = true)
            },
        )
    }

    // TODO: WRPAC should not be self-signed
}
