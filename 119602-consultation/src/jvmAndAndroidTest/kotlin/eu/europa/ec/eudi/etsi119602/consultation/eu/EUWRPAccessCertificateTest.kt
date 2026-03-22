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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ETSI319412
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EUWRPAccessCertificateTest {

    private val wrpacProviderName = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "EU")
        addRDN(BCStyle.O, "Wallet Relying Party Authority")
        addRDN(BCStyle.ORGANIZATION_IDENTIFIER, "LEIEU-5493001KJTIIGC8Y1R12")
        addRDN(BCStyle.CN, "Wallet Relying Party Authority")
    }.build()

    // Subject DN with all required attributes for a legal person (organization)
    // Per ETSI EN 319 412-3: countryName, organizationName, organizationIdentifier, commonName
    private val legalPersonSubject =
        X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, "EU")
            addRDN(BCStyle.O, "WRPAC Provider")
            addRDN(BCStyle.ORGANIZATION_IDENTIFIER, "VATEU-1KJTIIGC8Y12222")
            addRDN(BCStyle.CN, "Wallet Relying Party Registry")
        }.build()

    // Subject DN with all required attributes for a natural person
    // Per ETSI EN 319 412-2: countryName, givenName/surname/pseudonym, commonName, serialNumber
    private val naturalPersonSubject = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "GR")
        addRDN(BCStyle.GIVENNAME, "John") // givenName
        addRDN(BCStyle.SURNAME, "Doe") // surname
        addRDN(BCStyle.CN, "John Doe")
        addRDN(BCStyle.SERIALNUMBER, "PASGR-839201")
    }.build()

    // Subject DN missing serialNumber (for testing serialNumber requirement)
    private val naturalPersonSubjectMissingSerialNumber = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "EU")
        addRDN(BCStyle.GIVENNAME, "John") // givenName
        addRDN(BCStyle.SURNAME, "Doe") // surname
        addRDN(BCStyle.CN, "John Doe")
        // Missing SERIALNUMBER
    }.build()

    private suspend fun evaluateEndEntityCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation =
        CertificateProfileValidatorJVM.validate(wrpAccessCertificateProfile(), certificate)

    private fun wrpacProvider(subject: X500Name = wrpacProviderName) = CertOps.genTrustAnchor(
        sigAlg = "SHA256withECDSA",
        subject = subject,
        policyOids = null,
        pathLenConstraint = null,
    )

    private fun genCAIssuedEndEntityCertificate(
        subject: X500Name = X500Name("C=EU,O=Test,CN=Test Wallet Relying Party"),
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = listOf("0.4.0.194118.1.1"), // Default: NCP-n-eudiwrp
        caIssuersUri: String? = "http://ca.example.com/ca.crt",
        ocspUri: String? = "http://ocsp.example.com/",
        crlDistributionPointUri: String? = null,
    ): X509Certificate {
        val (caKeyPair, caCert) = wrpacProvider()
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = "SHA256withECDSA",
            subject = subject,
            qcStatements = qcStatements,
            policyOids = policyOids,
            caIssuersUri = caIssuersUri,
            ocspUri = ocspUri,
            crlDistributionPointUri = crlDistributionPointUri,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `WRPAC should require policy`() = runTest {
        // Generate end-entity certificate WITHOUT policy (all other attributes present)
        val certificate = genCAIssuedEndEntityCertificate(
            subject = legalPersonSubject,
            policyOids = null, // Only missing attribute
        )

        // Validate as WRPAC end-entity
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)

        // Should fail - missing policy OID only
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.violations.forEach { println(it.reason) }
        constraintEvaluation.assertSingleViolation { it.contains("certificate policies", ignoreCase = true) }
    }

    @Test
    fun `WRPAC should accept NCP-n policy`() =
        shouldAcceptPolicy(ETSI119411Part8.NCP_N_EUDIWRP, naturalPersonSubject)

    @Test
    fun `WRPAC should accept NCP-l policy`() =
        shouldAcceptPolicy(ETSI119411Part8.NCP_L_EUDIWRP, legalPersonSubject)

    @Test
    fun `WRPAC should accept QCP-n policy`() =
        shouldAcceptPolicy(ETSI119411Part8.QCP_N_EUDIWRP, naturalPersonSubject)

    @Test
    fun `WRPAC should accept QCP-l policy`() =
        shouldAcceptPolicy(ETSI119411Part8.QCP_L_EUDIWRP, legalPersonSubject)

    fun shouldAcceptPolicy(policyOid: String, subject: X500Name) = runTest {
        val qcStatements = when (policyOid) {
            ETSI119411Part8.QCP_N_EUDIWRP -> listOf(
                ETSI319412.QC_COMPLIANCE to true,
                ETSI319412.QC_SSCD to true,
            )

            ETSI119411Part8.QCP_L_EUDIWRP -> listOf(
                ETSI319412.QC_COMPLIANCE to true,
                ETSI319412.QC_SSCD to true,
                ETSI319412.QC_TYPE to true,
            )

            else -> null
        }
        val certificate = genCAIssuedEndEntityCertificate(
            subject = subject,
            policyOids = listOf(policyOid),
            qcStatements = qcStatements,
        )
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertTrue(constraintEvaluation.isMet())
    }

    @Test
    fun `WRPAC should reject unknown policy`() = runTest {
        // Create an end-entity certificate with unknown policy OID (all other attributes present)
        val unknownPolicyOid = "0.4.0.194118.999.999"
        val certificate = genCAIssuedEndEntityCertificate(
            subject = legalPersonSubject,
            policyOids = listOf(unknownPolicyOid), // Only different attribute
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
            subject = legalPersonSubject,
            policyOids = listOf(ETSI119411Part8.NCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC end-entity
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)

        // Should fail - CA certificate, not end-entity
        assertFalse(evaluation.isMet())
        assertTrue(
            evaluation.violations.any {
                it.reason.contains("Certificate type mismatch", ignoreCase = true)
            },
        )
    }

    @Test
    fun `WRPAC should not be self-signed`() = runTest {
        // Generate a self-signed end-entity certificate with valid WRPAC policy
        // Note: Self-signed certificates will fail multiple requirements (self-signed, missing AIA, etc.)
        val (_, certHolder) = CertOps.genSelfSignedEndEntityCertificate(
            sigAlg = "SHA256withECDSA",
            subject = naturalPersonSubject,
            keyUsage = org.bouncycastle.asn1.x509.KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature),
            policyOids = listOf(ETSI119411Part8.NCP_N_EUDIWRP),
        )
        val certificate = certHolder.toX509Certificate()

        // Validate as WRPAC end-entity
        val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)

        // Should fail - WRPAC must not be self-signed (among other violations)
        assertFalse(constraintEvaluation.isMet())
        // Check that at least one violation mentions self-signed
        assertTrue(
            constraintEvaluation.violations.any {
                it.reason.contains("self-signed", ignoreCase = true)
            },
            "Expected at least one violation about self-signed certificate",
        )
    }

    @Test
    fun `WRPAC should reject CA-issued certificate without AIA`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            subject = legalPersonSubject,
            policyOids = listOf(ETSI119411Part8.NCP_L_EUDIWRP),
            caIssuersUri = null,
            ocspUri = null,
            crlDistributionPointUri = "http://crl.example.com/crl.crl", // present so only AIA violation fires
        )
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation { it.contains("AIA", ignoreCase = true) }
    }

    @Test
    fun `WRPAC should reject QCP-n without QCStatements`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            subject = naturalPersonSubject,
            policyOids = listOf(ETSI119411Part8.QCP_N_EUDIWRP),
            qcStatements = null, // Only missing attribute
        )
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertFalse(evaluation.isMet())
        // QCP-n requires two QCStatements: QcCompliance and QcSSCD; both will be missing → 2 violations
        assertEquals(2, evaluation.violations.size)
        evaluation.violations.forEach {
            assertTrue(it.reason.contains("qcstatement", ignoreCase = true))
        }
    }

    @Test
    fun `WRPAC should reject QCP-n missing QcSSCD`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            subject = naturalPersonSubject,
            policyOids = listOf(ETSI119411Part8.QCP_N_EUDIWRP),
            qcStatements = listOf(ETSI319412.QC_COMPLIANCE to true), // Missing QcSSCD
        )
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation { it.contains("qcstatement", ignoreCase = true) }
    }

    @Test
    fun `WRPAC should reject certificate with disallowed public key algorithm`() = runTest {
        // DSA is not an allowed algorithm for WRPAC
        val (caKeyPair, caCert) = wrpacProvider()
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = "SHA256withECDSA",
            subject = legalPersonSubject,
            qcStatements = null,
            policyOids = listOf(ETSI119411Part8.NCP_L_EUDIWRP),
            caIssuersUri = "http://ca.example.com/ca.crt",
            ocspUri = "http://ocsp.example.com/",
            subjectKeyPairAlg = "RSA",
            subjectKeySize = 1024, // Too small for RSA (< 2048)
        )
        val certificate = certHolder.toX509Certificate()
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation {
            it.contains(
                "does not satisfy any of the required options",
                ignoreCase = true,
            )
        }
    }

    @Test
    fun `WRPAC should reject QCP-l missing QcPurpose`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            subject = legalPersonSubject,
            policyOids = listOf(ETSI119411Part8.QCP_L_EUDIWRP),
            qcStatements = listOf(
                ETSI319412.QC_COMPLIANCE to true,
                ETSI319412.QC_SSCD to true,
            ), // Missing QcType
        )
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)
        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation { it.contains("qcstatement", ignoreCase = true) }
    }

    @Test
    fun `WRPAC should reject natural person certificate missing serialNumber`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            subject = naturalPersonSubjectMissingSerialNumber,
            policyOids = listOf(ETSI119411Part8.NCP_N_EUDIWRP),
        )
        val evaluation = evaluateEndEntityCertificateConstraints(certificate)

        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation { it.contains("serialNumber", ignoreCase = true) }
    }
}
