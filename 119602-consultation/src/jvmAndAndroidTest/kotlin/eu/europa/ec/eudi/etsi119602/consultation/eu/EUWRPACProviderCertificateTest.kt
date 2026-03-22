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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.KeyUsage
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for WRPAC Provider certificate constraints (ETSI TS 119 602 Annex F).
 */
class EUWRPACProviderCertificateTest {

    private val cnWrpacProvider = X500Name("CN=WRPAC Provider Test")

    private suspend fun evaluateProviderCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        CertificateProfileValidatorJVM.validate(wrpacProviderCertificateProfile(), certificate)

    @Test
    fun `WRPAC Provider certificate should require keyCertSign`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, wrpacProviderCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = listOf("1.2.3.4.5"), // TSP defined policy OID
            pathLenConstraint = null,
        )
        val certificate = wrpacProviderCertHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateProviderCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("keyCertSign", ignoreCase = true) }
    }

    @Test
    fun `WRPAC Provider certificate should not be an end-entity certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (rootKey, rootCertHolder) = CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=Test CA"))
        val (_, wrpacProviderCertHolder) = CertOps.genCAIssuedEndEntityCertificate(
            sigAlg = "SHA256withECDSA",
            signerKey = rootKey.private,
            signerCert = rootCertHolder,
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://ca.example.com/ca.crt",
            ocspUri = "http://ocsp.example.com/",
            subjectAltNameUri = "https://wallet-relying-party.example.com",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
        )
        val certificate = wrpacProviderCertHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateProviderCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.violations.forEach { println(it.reason) }
        constraintEvaluation.assertSingleViolation { it.contains("expected CA", ignoreCase = true) }
    }

    @Test
    fun `WRPAC Provider certificate should require policy`() = runTest {
        val (_, wrprcProviderCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
            policyOids = null, // mising policy
            pathLenConstraint = null,
        )
        val certificate = wrprcProviderCertHolder.toX509Certificate()

        // Validate as WRPRC Provider
        val constraintEvaluation = evaluateProviderCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())

        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (not implemented yet)
        assertTrue(
            constraintEvaluation.violations.any { it.reason.contains("certificatePolicies extension") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `WRPAC Provider certificate should be valid`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, wrpacProviderCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            policyOids = listOf("1.2.3.4.5"),
            pathLenConstraint = null,
        )
        val certificate = wrpacProviderCertHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateProviderCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet())
    }
}
