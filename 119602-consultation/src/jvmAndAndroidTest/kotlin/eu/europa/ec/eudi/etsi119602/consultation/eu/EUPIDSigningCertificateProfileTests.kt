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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412Part6
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.RFC5280
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import java.security.KeyPair
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EUPIDSigningCertificateProfileTests {

    private suspend fun evaluateCertificateConstraints(
        certificate: X509Certificate,
    ): CertificateConstraintEvaluation = CertificateProfileValidatorJVM.validate(pidSigningCertificateProfile(), certificate)

    private val legalEntityPidProviderName = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "EU")
        addRDN(BCStyle.O, "PID Provider Organization")
        addRDN(BCStyle.ORGANIZATION_IDENTIFIER, "LEIEU-5493001KJTIIGC8Y1R12")
        addRDN(BCStyle.CN, "PID Provider")
    }.build()
    private val naturalPersonPidProvider = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "GR")
        addRDN(BCStyle.GIVENNAME, "John") // givenName
        addRDN(BCStyle.SURNAME, "Doe") // surname
        addRDN(BCStyle.CN, "John Doe")
        addRDN(BCStyle.SERIALNUMBER, "PASGR-839201")
    }.build()

    // Legal entity PID Provider subject missing organizationIdentifier (issue #149:
    // organizationIdentifier is mandatory for legal-person subjects per EN 319 412-3 LEG-4.2.1-2 /
    // PID-4.3-02).
    private val legalEntityPidProviderNameMissingOrganizationIdentifier = X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, "EU")
        addRDN(BCStyle.O, "PID Provider Organization")
        addRDN(BCStyle.CN, "PID Provider")
        // Missing ORGANIZATION_IDENTIFIER
    }.build()

    private val ca = CertOps.genTrustAnchor(
        sigAlg = "SHA256withECDSA",
        subject = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, "EU")
            addRDN(BCStyle.O, "Test CA Organization")
            addRDN(BCStyle.ORGANIZATION_IDENTIFIER, "LEIEU-12312312")
            addRDN(BCStyle.CN, "Test CA")
        }.build(),
        policyOids = null,
        pathLenConstraint = null,
    )

    // CA (issuer) that is a legal person missing organizationIdentifier. Per EN 319 412-2
    // GEN-4.2.3.1-3, organizationIdentifier is only conditionally mandatory for issuer DNs
    // (issue #149 analysis), so its absence alone must not fail validation.
    private val caMissingOrganizationIdentifier = CertOps.genTrustAnchor(
        sigAlg = "SHA256withECDSA",
        subject = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, "EU")
            addRDN(BCStyle.O, "Test CA Organization")
            addRDN(BCStyle.CN, "Test CA")
            // Missing ORGANIZATION_IDENTIFIER
        }.build(),
        policyOids = null,
        pathLenConstraint = null,
    )

    private fun genCAIssuedEndEntityCertificate(
        subject: X500Name,
        qcStatements: List<QCStatementInfo>? = null,
        policyOids: List<String>? = null,
        caIssuersUri: String? = null,
        ocspUri: String? = null,
        subjectAltNameUri: String? = null,
        keyUsage: KeyUsage,
        withSKI: Boolean = true,
        keyUsageCritical: Boolean = true,
        basicConstraintsCritical: Boolean = true,
        subjectAltNameCritical: Boolean = false,
        certificatePoliciesCritical: Boolean = false,
        customExtensions: List<Triple<String, Boolean, ASN1Encodable>> = emptyList(),
        issuer: Pair<KeyPair, X509CertificateHolder> = ca,
    ): X509Certificate {
        val sigAlg = "SHA256withECDSA"
        val (caKeyPair, caCert) = issuer
        val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
            signerCert = caCert,
            signerKey = caKeyPair.private,
            sigAlg = sigAlg,
            subject = subject,
            keyUsage = keyUsage,
            qcStatements = qcStatements,
            policyOids = policyOids,
            caIssuersUri = caIssuersUri,
            ocspUri = ocspUri,
            subjectAltNameUri = subjectAltNameUri,
            withSKI = withSKI,
            keyUsageCritical = keyUsageCritical,
            basicConstraintsCritical = basicConstraintsCritical,
            subjectAltNameCritical = subjectAltNameCritical,
            certificatePoliciesCritical = certificatePoliciesCritical,
            customExtensions = customExtensions,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `CA-issued certificate should require QCStatement`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = null,
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        // Should fail QCType check (end-entity cert without QCStatement)
        constraintEvaluation.assertSingleViolation {
            it.contains("QCType")
        }
    }

    @Test
    fun `CA-issued certificate should require QCStatement ID_ETSI_QCT_PID`() = runTest {
        // Generate a certificate with the wrong QCStatement type (Wallet instead of PID)
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_WAL)), // Wrong type
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should fail - wrong QCStatement type
        assertFalse(constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        assertEquals(1, constraintEvaluation.violations.size)
        assertTrue(
            constraintEvaluation.violations.any {
                it.reason.contains("required QcType identified by '${ETSI119412Part6.ID_ETSI_QCT_PID}'")
            },
        )
    }

    @Test
    fun `CA-issued certificate should require AIA`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("AIA") }
    }

    @Test
    fun `CA Issued certificate should be valid`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"), // TSP-defined policy OID
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
        )

        // Validate as PID Provider
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        // Should pass all constraints
        assertTrue(constraintEvaluation.isMet(), "$constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate should require end-entity not CA`() = runTest {
        val (_, caCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = legalEntityPidProviderName,
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = listOf("1.2.3.4.5"),
            pathLenConstraint = null,
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
        )
        val certificate = caCertHolder.toX509Certificate()

        // Validate as PID Provider - should fail because it's a CA certificate
        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        println(constraintEvaluation)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("CA", ignoreCase = true) }
    }

    @Test
    fun `CA-issued certificate should reject keyUsage that is not one of types A B C or F`() = runTest {
        // Generate an end-entity certificate with keyCertSign instead of a Type A/B/C/F setting
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.keyCertSign), // wrong key usage
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    @Test
    fun `CA-issued certificate with keyUsage Type A (nonRepudiation) is accepted`() = runTest {
        // PID-4.4.1-01 allows Type A (nonRepudiation only) from table 1 of EN 319 412-2
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.nonRepudiation), // Type A
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Type A keyUsage should be accepted: $constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate with keyUsage Type B is accepted`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation), // Type B
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Type B keyUsage should be accepted: $constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate with keyUsage Type F (keyEncipherment) is accepted`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation or KeyUsage.keyEncipherment), // Type F
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Type F keyUsage should be accepted: $constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate with keyUsage Type F (keyAgreement) is accepted`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation or KeyUsage.keyAgreement), // Type F
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Type F keyUsage should be accepted: $constraintEvaluation")
    }

    @Test
    fun `CA-issued certificate with mixed keyUsage (digitalSignature and keyCertSign) is rejected`() = runTest {
        // digitalSignature alone is Type C, but combined with keyCertSign it matches none of types A, B, C or F
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign), // mixed, not a valid setting
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    //
    // Self-signed PID Provider Certificate Tests
    //

    private fun genSelfSignedEndEntityCertificate(
        subject: X500Name,
        qcStatements: List<QCStatementInfo>? = null,
        policyOids: List<String>? = null,
        keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
        withSKI: Boolean = true,
    ): X509Certificate {
        val sigAlg = "SHA256withECDSA"
        val (_, certHolder) = CertOps.genSelfSignedEndEntityCertificate(
            sigAlg = sigAlg,
            subject = subject,
            keyUsage = keyUsage,
            qcStatements = qcStatements,
            policyOids = policyOids,
            withSKI = withSKI,
        )
        return certHolder.toX509Certificate()
    }

    @Test
    fun `Self-signed certificate should require end-entity not CA`() = runTest {
        // Generate a self-signed CA certificate (cA=TRUE) instead of end-entity
        val (_, caCertHolder) = CertOps.genTrustAnchor(
            sigAlg = "SHA256withECDSA",
            subject = legalEntityPidProviderName,
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            policyOids = listOf("1.2.3.4.5"),
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            pathLenConstraint = null,
        )
        val certificate = caCertHolder.toX509Certificate()

        // Validate as PID Provider - should fail because it's a CA certificate
        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "CA certificate should fail PID Provider validation")
        constraintEvaluation.assertSingleViolation { it.contains("CA", ignoreCase = true) }
    }

    @Test
    fun `Self-signed LP certificate should reject keyUsage that is not one of types A B C or F`() = runTest {
        // Generate a self-signed certificate with keyCertSign instead of a Type A/B/C/F setting
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.keyCertSign), // wrong key usage
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate with keyCertSign only should fail")
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    @Test
    fun `Self-signed NP certificate should reject keyUsage that is not one of types A B C or F`() = runTest {
        // Generate a self-signed certificate with keyCertSign instead of a Type A/B/C/F setting
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.keyCertSign), // wrong key usage
            subject = naturalPersonPidProvider,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate with keyCertSign only should fail")
        constraintEvaluation.assertSingleViolation { it.contains("keyUsage", ignoreCase = true) }
    }

    @Test
    fun `Self-signed LP certificate with keyUsage Type A (nonRepudiation) is accepted`() = runTest {
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.nonRepudiation), // Type A
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Type A keyUsage should be accepted: $constraintEvaluation")
    }

    @Test
    fun `Self-signed LP certificate should require QCStatement`() = runTest {
        // Generate a self-signed certificate without QCStatement
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = null, // No QCStatement
            policyOids = listOf("1.2.3.4.5"),
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate without QCStatement should fail")
        constraintEvaluation.assertSingleViolation { it.contains("QCType", ignoreCase = true) }
    }

    @Test
    fun `Self-signed NP certificate should require QCStatement`() = runTest {
        // Generate a self-signed certificate without QCStatement
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = null, // No QCStatement
            policyOids = listOf("1.2.3.4.5"),
            subject = naturalPersonPidProvider,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Certificate without QCStatement should fail")
        constraintEvaluation.assertSingleViolation { it.contains("QCType", ignoreCase = true) }
    }

    @Test
    fun `Self-signed LP certificate should require QCStatement ID_ETSI_QCT_PID`() = runTest {
        // Generate a self-signed certificate with wrong QCStatement type (Wallet instead of PID)
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_WAL)), // Wrong type
            policyOids = listOf("1.2.3.4.5"),
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        constraintEvaluation.assertSingleViolation {
            it.contains("required QcType identified by '${ETSI119412Part6.ID_ETSI_QCT_PID}'")
        }
    }

    @Test
    fun `Self-signed NP certificate should require QCStatement ID_ETSI_QCT_PID`() = runTest {
        // Generate a self-signed certificate with wrong QCStatement type (Wallet instead of PID)
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_WAL)), // Wrong type
            policyOids = listOf("1.2.3.4.5"),
            subject = naturalPersonPidProvider,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Wrong QCStatement type should fail")
        constraintEvaluation.assertSingleViolation {
            it.contains("required QcType identified by '${ETSI119412Part6.ID_ETSI_QCT_PID}'")
        }
    }

    @Test
    fun `Self-signed LP certificate should be valid`() = runTest {
        // Generate a valid self-signed certificate with all requirements
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Valid self-signed certificate should pass: $constraintEvaluation")
    }

    @Test
    fun `Self-signed NP certificate should be valid`() = runTest {
        // Generate a valid self-signed certificate with all requirements
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = naturalPersonPidProvider,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(constraintEvaluation.isMet(), "Valid self-signed certificate should pass: $constraintEvaluation")
    }

    // Issue #149: Per ETSI EN 319 412-3 LEG-4.2.1-2 (PID-4.3-02), organizationIdentifier is
    // mandatory for legal-person PID Provider subjects.
    @Test
    fun `CA-issued LP certificate should reject subject missing organizationIdentifier`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderNameMissingOrganizationIdentifier,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Legal person subject without organizationIdentifier must be rejected")
        constraintEvaluation.assertSingleViolation {
            it.contains("organizationIdentifier", ignoreCase = true) && it.contains("missing", ignoreCase = true)
        }
    }

    @Test
    fun `Self-signed LP certificate should reject subject missing organizationIdentifier`() = runTest {
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderNameMissingOrganizationIdentifier,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertFalse(constraintEvaluation.isMet(), "Legal person subject without organizationIdentifier must be rejected")
        constraintEvaluation.assertSingleViolation {
            it.contains("organizationIdentifier", ignoreCase = true) && it.contains("missing", ignoreCase = true)
        }
    }

    // Issue #149 (analysis): organizationIdentifier is only conditionally mandatory for issuer DNs
    // (EN 319 412-2 GEN-4.2.3.1-3), so a CA issuer without it must still be accepted.
    @Test
    fun `CA-issued LP certificate should be accepted when issuer is missing organizationIdentifier`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
            issuer = caMissingOrganizationIdentifier,
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)

        assertTrue(
            constraintEvaluation.isMet(),
            "Issuer missing organizationIdentifier alone must not cause rejection: $constraintEvaluation",
        )
    }

    @Test
    fun `CA-issued certificate should require subjectKeyIdentifier`() = runTest {
        val certificate = genCAIssuedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            caIssuersUri = "http://example.com/ca.crt",
            ocspUri = "http://example.com/ocsp",
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
            withSKI = false, // Explicitly omit SKI
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("subjectKeyIdentifier", ignoreCase = true) }
    }

    @Test
    fun `Self-signed certificate should require subjectKeyIdentifier`() = runTest {
        val certificate = genSelfSignedEndEntityCertificate(
            qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
            policyOids = listOf("1.2.3.4.5"),
            keyUsage = KeyUsage(KeyUsage.digitalSignature),
            subject = legalEntityPidProviderName,
            withSKI = false, // Explicitly omit SKI
        )

        val constraintEvaluation = evaluateCertificateConstraints(certificate)
        assertFalse(constraintEvaluation.isMet())
        constraintEvaluation.assertSingleViolation { it.contains("subjectKeyIdentifier", ignoreCase = true) }
    }

    // PID-4.1-02 (TS 119 412-6), like EN 319 412-2 GEN-4.1-2, allows criticality where RFC 5280 allows or requires it.
    // RFC 5280 §4.2.1.3: keyUsage "SHOULD" be critical; §4.2.1.9: basicConstraints "MAY" be either in end entity
    // certificates; §4.2.1.4: certificatePolicies and §4.2.1.6: subjectAltName may be critical or non-critical in
    // end-entity certificates; §4.2.1.12: extendedKeyUsage "MAY, at the option of the certificate issuer, be either
    // critical or non-critical."

    private fun validCertificate(
        keyUsageCritical: Boolean = true,
        basicConstraintsCritical: Boolean = true,
        subjectAltNameUri: String? = null,
        subjectAltNameCritical: Boolean = false,
        certificatePoliciesCritical: Boolean = false,
        caIssuersUri: String? = "http://example.com/ca.crt",
        ocspUri: String? = "http://example.com/ocsp",
        customExtensions: List<Triple<String, Boolean, ASN1Encodable>> = emptyList(),
    ) = genCAIssuedEndEntityCertificate(
        qcStatements = listOf(QCStatementInfo.QcType(ETSI119412Part6.ID_ETSI_QCT_PID)),
        policyOids = listOf("1.2.3.4.5"),
        caIssuersUri = caIssuersUri,
        ocspUri = ocspUri,
        subjectAltNameUri = subjectAltNameUri,
        keyUsage = KeyUsage(KeyUsage.digitalSignature),
        subject = legalEntityPidProviderName,
        keyUsageCritical = keyUsageCritical,
        basicConstraintsCritical = basicConstraintsCritical,
        subjectAltNameCritical = subjectAltNameCritical,
        certificatePoliciesCritical = certificatePoliciesCritical,
        customExtensions = customExtensions,
    )

    @Test
    fun `PID Provider certificate with a non-critical keyUsage is accepted`() = runTest {
        val evaluation = evaluateCertificateConstraints(validCertificate(keyUsageCritical = false))
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `PID Provider certificate with a non-critical basicConstraints is accepted`() = runTest {
        val evaluation = evaluateCertificateConstraints(validCertificate(basicConstraintsCritical = false))
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `PID Provider certificate with a critical extendedKeyUsage is accepted`() = runTest {
        val evaluation = evaluateCertificateConstraints(
            validCertificate(
                customExtensions = listOf(
                    Triple(RFC5280.EXT_EXTENDED_KEY_USAGE, true, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)),
                ),
            ),
        )
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `PID Provider certificate with a critical subjectAltName is accepted`() = runTest {
        val evaluation = evaluateCertificateConstraints(
            validCertificate(
                subjectAltNameUri = "https://pid-provider.example.com",
                subjectAltNameCritical = true,
            ),
        )
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `PID Provider certificate with a critical certificatePolicies is accepted`() = runTest {
        val evaluation = evaluateCertificateConstraints(
            validCertificate(
                certificatePoliciesCritical = true,
            ),
        )
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `PID Provider certificate with a critical authorityInfoAccess is rejected`() = runTest {
        // RFC 5280 §4.2.2.1: "Conforming CAs MUST mark this extension as non-critical."
        val evaluation = evaluateCertificateConstraints(
            validCertificate(
                caIssuersUri = null,
                ocspUri = null,
                customExtensions = listOf(
                    Triple(
                        RFC5280.EXT_AUTHORITY_INFO_ACCESS,
                        true,
                        DERSequence(
                            arrayOf(
                                AccessDescription(
                                    AccessDescription.id_ad_caIssuers,
                                    GeneralName(
                                        GeneralName.uniformResourceIdentifier,
                                        DERIA5String("http://example.com/ca.crt"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertFalse(evaluation.isMet())
        evaluation.assertSingleViolation { it.contains(RFC5280.EXT_AUTHORITY_INFO_ACCESS) }
    }
}
