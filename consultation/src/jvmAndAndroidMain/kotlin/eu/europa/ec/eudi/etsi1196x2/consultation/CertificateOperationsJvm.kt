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
package eu.europa.ec.eudi.etsi1196x2.consultation

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.GeneralName
import java.security.cert.X509Certificate
import kotlin.time.toKotlinInstant

/**
 * JVM/Android implementations of certificate constraint extractors for [X509Certificate].
 *
 * This object provides platform-specific functions to extract certificate information
 * required by the constraint validators defined in commonMain.
 */
public object CertificateOperationsJvm : CertificateOperations<X509Certificate> {

    /**
     * Extracts basic constraints information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo] with isCa and pathLenConstraint
     */
    public override suspend fun getBasicConstraints(certificate: X509Certificate): BasicConstraintsInfo =
        withContext(Dispatchers.IO) {
            val basicConstraints = certificate.basicConstraints
            BasicConstraintsInfo(
                isCa = basicConstraints >= 0,
                pathLenConstraint = basicConstraints.takeIf { it >= 0 },
            )
        }

    /**
     * Extracts QCStatement information from an X509Certificate.
     *
     * QCStatements are encoded in the certificate extension with OID 1.3.6.1.5.5.7.1.3.
     * This function parses the DER-encoded extension value to extract QC type OIDs.
     *
     * @return list of [eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo] or empty list if no QCStatements present
     */
    public override suspend fun getQcStatements(certificate: X509Certificate): List<QCStatementInfo> =
        withContext(Dispatchers.IO) {
            // OID for QCStatements extension (id-pe-qcStatements)
            val qcStatementsExtension = certificate.getExtensionValue("1.3.6.1.5.5.7.1.3")
            qcStatementsExtension?.parseQcStatements().orEmpty()
        }

    /**
     * Extracts key usage information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits] or null if keyUsage extension is not present
     */
    public override suspend fun getKeyUsage(certificate: X509Certificate): KeyUsageBits? =
        withContext(Dispatchers.IO) {
            certificate.keyUsage?.let { keyUsage ->
                KeyUsageBits(
                    digitalSignature = keyUsage.getOrElse(0) { false },
                    nonRepudiation = keyUsage.getOrElse(1) { false },
                    keyEncipherment = keyUsage.getOrElse(2) { false },
                    dataEncipherment = keyUsage.getOrElse(3) { false },
                    keyAgreement = keyUsage.getOrElse(4) { false },
                    keyCertSign = keyUsage.getOrElse(5) { false },
                    crlSign = keyUsage.getOrElse(6) { false },
                    encipherOnly = keyUsage.getOrElse(7) { false },
                    decipherOnly = keyUsage.getOrElse(8) { false },
                )
            }
        }

    /**
     * Extracts validity period information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod] with notBefore and notAfter timestamps
     */
    public override suspend fun getValidityPeriod(certificate: X509Certificate): ValidityPeriod =
        withContext(Dispatchers.IO) {
            ValidityPeriod(
                notBefore = certificate.notBefore.toInstant().toKotlinInstant(),
                notAfter = certificate.notAfter.toInstant().toKotlinInstant(),
            )
        }

    /**
     * Extracts certificate policy OIDs from an X509Certificate.
     *
     * Certificate policies are encoded in the certificate extension with OID 2.5.29.32.
     * This function parses the DER-encoded extension value to extract policy OIDs.
     *
     * @return list of certificate policy OIDs or empty list if no policies present
     */
    public override suspend fun getCertificatePolicies(certificate: X509Certificate): List<String> =
        withContext(Dispatchers.IO) {
            // OID for Certificate Policies extension
            val certPoliciesExtension = certificate.getExtensionValue("2.5.29.32")
            certPoliciesExtension?.parseCertificatePolicies().orEmpty()
        }

    /**
     * Checks if an X509Certificate is self-signed.
     *
     * A certificate is self-signed if its subject and issuer are the same.
     *
     * @return true if self-signed, false otherwise
     */
    public override suspend fun isSelfSigned(certificate: X509Certificate): Boolean =
        withContext(Dispatchers.IO) {
            certificate.subjectX500Principal == certificate.issuerX500Principal
        }

    /**
     * Extracts Authority Information Access (AIA) extension from an X509Certificate.
     *
     * @return [AuthorityInformationAccess] or null if extension is not present or parsing fails
     */
    public override suspend fun getAiaExtension(certificate: X509Certificate): AuthorityInformationAccess? =
        withContext(Dispatchers.IO) {
            // OID for AIA extension (id-pe-authorityInfoAccess)
            val aiaExtension = certificate.getExtensionValue("1.3.6.1.5.5.7.1.1")
            aiaExtension?.parseAiaExtension()
        }

    /**
     * Helper function to parse AIA from DER-encoded extension value.
     */
    private fun ByteArray.parseAiaExtension(): AuthorityInformationAccess? = try {
        ASN1InputStream(this).use { asn1InputStream ->
            val obj = asn1InputStream.readObject()
            val octetString = obj as? DEROctetString ?: return null
            val aiaSequence = ASN1Sequence.getInstance(octetString.octets)

            var caIssuersUri: String? = null
            var ocspUri: String? = null

            for (i in 0 until aiaSequence.size()) {
                val accessDescription = AccessDescription.getInstance(aiaSequence.getObjectAt(i))
                val accessMethod = accessDescription.accessMethod
                val accessLocation = accessDescription.accessLocation

                if (accessLocation.tagNo == GeneralName.uniformResourceIdentifier) {
                    val uri = accessLocation.name.toString()

                    when (accessMethod) {
                        AccessDescription.id_ad_caIssuers -> caIssuersUri = uri
                        AccessDescription.id_ad_ocsp -> ocspUri = uri
                    }
                }
            }
            AuthorityInformationAccess(caIssuersUri, ocspUri)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Helper function to parse QCStatements from DER-encoded extension value.
     *
     * The QCStatements extension has the following ASN.1 structure (ETSI EN 319 412-5):
     * ```
     * QCStatements ::= SEQUENCE OF QCStatement
     * QCStatement ::= SEQUENCE {
     *   statementId   OBJECT IDENTIFIER,
     *   statementInfo ANY DEFINED BY statementId OPTIONAL
     * }
     * ```
     *
     * The extension value is wrapped in an OCTET STRING, so we need to unwrap it first.
     *
     * @receiver derValue the DER-encoded extension value
     * @return list of [QCStatementInfo] or empty list if parsing fails
     *
     * @see [ETSI EN 319 412-5](https://www.etsi.org/deliver/etsi_en/319400_319499/31941205/)
     */
    private fun ByteArray.parseQcStatements(): List<QCStatementInfo> = try {
        ASN1InputStream(this).use { asn1InputStream ->
            val obj = asn1InputStream.readObject()
            // The extension value is wrapped in an OCTET STRING
            val octetString = obj as? DEROctetString ?: return emptyList()
            val qcStatements = ASN1Sequence.getInstance(octetString.octets)

            qcStatements.mapNotNull { qcStatementObj ->
                val qcStatement = qcStatementObj as? ASN1Sequence ?: return@mapNotNull null
                if (qcStatement.size() < 1) return@mapNotNull null

                // First element is the statementId (OID)
                val statementIdObj = qcStatement.getObjectAt(0) as? ASN1ObjectIdentifier ?: return@mapNotNull null
                val statementId = statementIdObj.id

                // Second element (optional) is statementInfo - we check for QC compliance
                // Per ETSI EN 319 412-5, QCStatements can have a compliance bit
                // For simplicity, we assume compliant if present
                val qcCompliance = qcStatement.size() > 1

                QCStatementInfo(
                    qcType = statementId,
                    qcCompliance = qcCompliance,
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Helper function to parse Certificate Policies from DER-encoded extension value.
     *
     * The Certificate Policies extension has the following ASN.1 structure (RFC 5280):
     * ```
     * CertificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
     * PolicyInformation ::= SEQUENCE {
     *   policyIdentifier   CertPolicyId,
     *   policyQualifiers   SEQUENCE SIZE (1..MAX) OF PolicyQualifierInfo OPTIONAL
     * }
     * CertPolicyId ::= OBJECT IDENTIFIER
     * ```
     *
     * The extension value is wrapped in an OCTET STRING, so we need to unwrap it first.
     *
     * @receiver the DER-encoded extension value
     * @return list of policy OIDs or empty list if parsing fails
     *
     * @see [RFC 5280 Section 4.2.1.4](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.4)
     */
    private fun ByteArray.parseCertificatePolicies(): List<String> = try {
        ASN1InputStream(this).use { asn1InputStream ->
            val obj = asn1InputStream.readObject()
            // The extension value is wrapped in an OCTET STRING
            val octetString = obj as? DEROctetString ?: return emptyList()
            val certPolicies = ASN1Sequence.getInstance(octetString.octets)

            certPolicies.mapNotNull { policyInfoObj ->
                val policyInfo = policyInfoObj as? ASN1Sequence ?: return@mapNotNull null
                if (policyInfo.size() < 1) return@mapNotNull null

                // First element is the policyIdentifier (OID)
                val policyIdObj = policyInfo.getObjectAt(0) as? ASN1ObjectIdentifier ?: return@mapNotNull null
                policyIdObj.id
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
