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
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.KeyUsage
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for WRPAC Provider certificate constraints (ETSI TS 119 602 Annex F).
 */
class EUWRPACProviderCertificateTest {

    private val cnWrpacProvider = X500Name("CN=WRPAC Provider Test")

    private val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)

    private suspend fun evaluateProviderCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        certificateProfileValidator.validate(wrpacProviderCertificateProfile(), certificate)

    private fun CertificateConstraintEvaluation.Violated.assertSingleViolation(
        message: String? = null,
        assertTrue: (String) -> Boolean,
    ) {
        assertEquals(1, violations.size)
        val violation = violations.first()
        assertTrue(assertTrue(violation.reason), message)
    }

    @Test
    fun `WRPAC Provider certificate should be valid`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, wrpacProviderCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            policyOids = null,
            pathLenConstraint = null,
        )
        val certificate = wrpacProviderCertHolder.toX509Certificate()

        // Validate as WRPAC Provider
        val constraintEvaluation = evaluateProviderCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet())
    }

    @Test
    fun `WRPAC Provider certificate should require keyCertSign`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (_, wrpacProviderCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = cnWrpacProvider,
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = null,
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
            policyOids = null,
            caIssuersUri = null,
            ocspUri = null,
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

    // TODO : WRPAC Provider certificate
    //  - Check that the certificate is not self-signed
}
