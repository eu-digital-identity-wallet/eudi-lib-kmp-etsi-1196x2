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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.AuthorityInformationAccess
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.AuthorityKeyIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.qualified.QCStatement
import org.slf4j.LoggerFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.security.auth.x500.X500Principal
import kotlin.time.toKotlinInstant
import org.bouncycastle.asn1.x509.AuthorityInformationAccess as BcAuthorityInformationAccess
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier as BcAuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.CertificatePolicies as BcCertificatePolicies

/**
 * JVM/Android implementations of certificate constraint extractors for [X509Certificate].
 *
 * This object provides platform-specific functions to extract certificate information
 * required by the constraint validators defined in commonMain.
 */
public object CertificateOperationsJvm : CertificateOperations<X509Certificate> {

    private val logger = LoggerFactory.getLogger(CertificateOperationsJvm::class.java)

    /**
     * Extracts basic constraints information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo] with isCa and pathLenConstraint
     */
    public override fun getBasicConstraints(certificate: X509Certificate): BasicConstraintsInfo {
        val basicConstraints = certificate.basicConstraints
        val isCa = basicConstraints >= 0
        return BasicConstraintsInfo(
            isCa = isCa,
            pathLenConstraint = basicConstraints.takeIf { isCa },
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
    public override fun getQcStatements(certificate: X509Certificate): List<QCStatementInfo> {
        val qcStatementsExtension = certificate.getExtensionValue(Extension.qCStatements.id)
        return qcStatementsExtension?.parseQcStatements().orEmpty()
    }

    /**
     * Extracts key usage information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits] or null if keyUsage extension is not present
     */
    public override fun getKeyUsage(certificate: X509Certificate): Pair<KeyUsageBits, Boolean>? =
        certificate.keyUsage?.let { keyUsage ->
            val bits = KeyUsageBits(
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
            val critical = certificate.isCritical(Extension.keyUsage.id)
            bits to critical
        }

    private fun X509Certificate.isCritical(p: String): Boolean {
        return p in criticalExtensionOIDs.orEmpty()
    }

    /**
     * Extracts validity period information from an X509Certificate.
     *
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod] with notBefore and notAfter timestamps
     */
    public override fun getValidityPeriod(certificate: X509Certificate): ValidityPeriod =
        ValidityPeriod(
            notBefore = certificate.notBefore.toInstant().toKotlinInstant(),
            notAfter = certificate.notAfter.toInstant().toKotlinInstant(),
        )

    /**
     * Extracts certificate policy OIDs from an X509Certificate.
     *
     * Certificate policies are encoded in the certificate extension with OID 2.5.29.32.
     * This function parses the DER-encoded extension value to extract policy OIDs.
     *
     * @return list of certificate policy OIDs or empty list if no policies present
     */
    public override fun getCertificatePolicies(certificate: X509Certificate): List<String> {
        val certPoliciesExtension =
            certificate.getExtensionValue(Extension.certificatePolicies.id)
        return certPoliciesExtension?.parseCertificatePolicies().orEmpty()
    }

    /**
     * Checks if an X509Certificate is self-signed.
     *
     * A certificate is self-signed if its subject and issuer are the same.
     *
     * @return true if self-signed, false otherwise
     */
    public override fun isSelfSigned(certificate: X509Certificate): Boolean =
        certificate.subjectX500Principal == certificate.issuerX500Principal

    /**
     * Extracts Authority Information Access (AIA) extension from an X509Certificate.
     *
     * @return [AuthorityInformationAccess] or null if extension is not present or parsing fails
     */
    public override fun getAiaExtension(certificate: X509Certificate): AuthorityInformationAccess? {
        val aiaExtension = certificate.getExtensionValue(Extension.authorityInfoAccess.id)
        return aiaExtension?.parseAiaExtension()
    }

    /**
     * Helper function to parse AIA from DER-encoded extension value.
     */
    private fun ByteArray.parseAiaExtension(): AuthorityInformationAccess? = try {
        val octetString = DEROctetString.getInstance(this)
        val aia = BcAuthorityInformationAccess.getInstance(octetString.octets)

        var caIssuersUri: String? = null
        var ocspUri: String? = null

        val accessDescriptions = aia.accessDescriptions
        for (accessDescription in accessDescriptions) {
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
    } catch (e: Exception) {
        logger.warn("Failed to parse AIA extension from certificate: ${e.message}", e)
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
        val octetString = DEROctetString.getInstance(this)
        val qcStatements = ASN1Sequence.getInstance(octetString.octets)

        qcStatements.mapNotNull { qcStatementObj ->
            val qcStatement = QCStatement.getInstance(qcStatementObj) ?: return@mapNotNull null

            // First element is the statementId (OID)
            val statementId = qcStatement.statementId.id

            // Second element (optional) is statementInfo - check for QC compliance
            // Per ETSI EN 319 412-5, QCStatements can have a compliance indicator
            // We check if the second element is a UTF8String containing "compliant"
            var qcCompliance = false
            qcStatement.statementInfo?.let { statementInfo ->
                // Check if it's a UTF8String with compliance indicator
                if (statementInfo is DERUTF8String) {
                    val complianceStr = statementInfo.string.lowercase()
                    qcCompliance = complianceStr == "compliant"
                } else {
                    // If there's any second element, assume compliant (backward compatibility)
                    qcCompliance = true
                }
            }

            QCStatementInfo(
                qcType = statementId,
                qcCompliance = qcCompliance,
            )
        }
    } catch (e: Exception) {
        logger.warn("Failed to parse QCStatements from certificate: ${e.message}", e)
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
        val octetString = DEROctetString.getInstance(this)
        val certPolicies = BcCertificatePolicies.getInstance(octetString.octets)

        certPolicies.policyInformation.map { policyInfo ->
            policyInfo.policyIdentifier.id
        }
    } catch (e: Exception) {
        logger.warn("Failed to parse CertificatePolicies from certificate: ${e.message}", e)
        emptyList()
    }

    /**
     * Extracts the subject Distinguished Name from an X509Certificate.
     *
     * @return [DistinguishedName] or null if parsing fails
     */
    public override fun getSubject(certificate: X509Certificate): DistinguishedName? = try {
        certificate.subjectX500Principal.asDistinguishedName()
    } catch (e: Exception) {
        logger.warn("Failed to parse subject DN from certificate: ${e.message}", e)
        null
    }

    /**
     * Extracts the issuer Distinguished Name from an X509Certificate.
     *
     * @return [DistinguishedName] or null if parsing fails
     */
    public override fun getIssuer(certificate: X509Certificate): DistinguishedName? = try {
        certificate.issuerX500Principal.asDistinguishedName()
    } catch (e: Exception) {
        logger.warn("Failed to parse issuer DN from certificate: ${e.message}", e)
        null
    }

    /**
     * Extracts Subject Alternative Names from an X509Certificate.
     *
     * @return list of [SubjectAlternativeName] or empty list if extension is not present
     */
    public override fun getSubjectAltNames(certificate: X509Certificate): List<SubjectAlternativeName> = try {
        val sanExtension = certificate.getExtensionValue(Extension.subjectAlternativeName.id)
        if (sanExtension == null) {
            emptyList()
        } else {
            val octetString = DEROctetString.getInstance(sanExtension)
            val names = GeneralNames.getInstance(octetString.octets)
            names.names.mapNotNull { generalName ->
                val type = generalName.tagNo
                val name = generalName.name
                val value = name.toString()

                when (type) {
                    GeneralName.otherName -> SubjectAlternativeName.OtherName(type.toString(), value)
                    GeneralName.rfc822Name -> SubjectAlternativeName.Email(value)
                    GeneralName.dNSName -> SubjectAlternativeName.DNSName(value)
                    GeneralName.x400Address -> SubjectAlternativeName.OtherName(type.toString(), value)
                    GeneralName.directoryName -> SubjectAlternativeName.OtherName(type.toString(), value)
                    GeneralName.ediPartyName -> SubjectAlternativeName.OtherName(type.toString(), value)
                    GeneralName.uniformResourceIdentifier -> SubjectAlternativeName.Uri(value)
                    GeneralName.iPAddress -> {
                        val ipValue = DEROctetString.getInstance(name).octets
                        SubjectAlternativeName.IPAddress(ipValue)
                    }

                    GeneralName.registeredID -> SubjectAlternativeName.RegisteredId(value)
                    else -> SubjectAlternativeName.OtherName(type.toString(), value)
                }
            }
        }
    } catch (e: Exception) {
        logger.warn("Failed to parse SubjectAltNames from certificate: ${e.message}", e)
        emptyList()
    }

    /**
     * Extracts CRL Distribution Points from an X509Certificate.
     *
     * @return list of [CrlDistributionPoint] or empty list if extension is not present
     */
    public override fun getCrlDistributionPoints(certificate: X509Certificate): List<CrlDistributionPoint> = try {
        val crldpExtension = certificate.getExtensionValue(Extension.cRLDistributionPoints.id)
        crldpExtension?.parseCrlDistributionPoints().orEmpty()
    } catch (e: Exception) {
        logger.warn("Failed to parse CRLDistributionPoints from certificate: ${e.message}", e)
        emptyList()
    }

    /**
     * Helper function to parse CRL Distribution Points from DER-encoded extension value.
     */
    private fun ByteArray.parseCrlDistributionPoints(): List<CrlDistributionPoint> = try {
        val octetString = DEROctetString.getInstance(this)
        val crlDistPoint = CRLDistPoint.getInstance(octetString.octets)

        val dps = crlDistPoint.distributionPoints ?: return emptyList()

        dps.mapNotNull { dp ->
            if (dp == null) return@mapNotNull null
            val dpName = dp.distributionPoint
            var uri: String? = null
            if (dpName != null && dpName.type == DistributionPointName.FULL_NAME) {
                val generalNames = GeneralNames.getInstance(dpName.name)
                uri = generalNames.names
                    .firstOrNull { it.tagNo == GeneralName.uniformResourceIdentifier }
                    ?.name?.toString()
            }
            CrlDistributionPoint(uri, null)
        }
    } catch (e: Exception) {
        logger.warn("Failed to parse CRLDistributionPoints: ${e.message}", e)
        emptyList()
    }

    /**
     * Extracts Authority Key Identifier from an X509Certificate.
     *
     * @return [AuthorityKeyIdentifier] or null if extension is not present
     */
    public override fun getAuthorityKeyIdentifier(certificate: X509Certificate): AuthorityKeyIdentifier? = try {
        val akiExtension = certificate.getExtensionValue(Extension.authorityKeyIdentifier.id)
        akiExtension?.parseAuthorityKeyIdentifier()
    } catch (e: Exception) {
        logger.warn("Failed to parse AuthorityKeyIdentifier from certificate: ${e.message}", e)
        null
    }

    /**
     * Helper function to parse Authority Key Identifier from DER-encoded extension value.
     */
    private fun ByteArray.parseAuthorityKeyIdentifier(): AuthorityKeyIdentifier? = try {
        val octetString = DEROctetString.getInstance(this)
        val aki = BcAuthorityKeyIdentifier.getInstance(octetString.octets)

        val keyIdentifier = aki.keyIdentifierOctets?.copyOf()
        val authorityCertSerialNumber = aki.authorityCertSerialNumber?.toByteArray()

        var authorityCertIssuer: List<String>? = null
        aki.authorityCertIssuer?.let { generalNames ->
            authorityCertIssuer = generalNames.names.map { it.name.toString() }
        }

        AuthorityKeyIdentifier(keyIdentifier, authorityCertIssuer, authorityCertSerialNumber)
    } catch (e: Exception) {
        logger.warn("Failed to parse AuthorityKeyIdentifier: ${e.message}", e)
        null
    }

    /**
     * Extracts the certificate serial number.
     *
     * @return [SerialNumber] as a byte array (always positive per RFC 5280)
     */
    public override fun getSerialNumber(certificate: X509Certificate): SerialNumber =
        SerialNumber(certificate.serialNumber.toByteArray())

    /**
     * Extracts the certificate version.
     *
     * @return [Version] (X509Certificate.version returns 1=v1, 2=v2, 3=v3; we convert to 0=v1, 1=v2, 2=v3)
     */
    public override fun getVersion(certificate: X509Certificate): Version =
        Version(certificate.version - 1) // Java returns 1-based, we use 0-based

    /**
     * Extracts subject public key information.
     *
     * @return [PublicKeyInfo] with algorithm, key size, and parameters
     */
    public override fun getSubjectPublicKeyInfo(certificate: X509Certificate): PublicKeyInfo = try {
        val publicKey = certificate.publicKey
        val algorithm = publicKey.algorithm
        val encodedBytes = publicKey.encoded

        // Determine key size based on algorithm type
        val keySize = when (publicKey) {
            is RSAPublicKey -> publicKey.modulus.bitLength()
            is ECPublicKey -> {
                // Try to get key size from params, otherwise use encoded length as fallback
                val paramsSize = publicKey.params?.order?.bitLength()
                if (paramsSize != null) {
                    paramsSize
                } else {
                    // Fallback: estimate from encoded key length
                    // EC keys: ~32 bytes = 256 bits, ~48 bytes = 384 bits, ~66 bytes = 521 bits
                    val encodedLen = encodedBytes.size
                    when {
                        encodedLen >= 100 -> 521
                        encodedLen >= 60 -> 384
                        else -> 256
                    }
                }
            }

            else -> null
        }

        // Get algorithm parameters (e.g., EC curve OID)
        val parameters = try {
            val spki = SubjectPublicKeyInfo.getInstance(encodedBytes)
            val algParams = spki.algorithm?.parameters
            algParams?.toASN1Primitive()?.encoded
        } catch (_: Exception) {
            null
        }

        PublicKeyInfo(algorithm, keySize, parameters)
    } catch (e: Exception) {
        logger.warn("Failed to parse SubjectPublicKeyInfo: ${e.message}", e)
        PublicKeyInfo(certificate.publicKey.algorithm, null, null)
    }
}

internal fun X500Principal.asDistinguishedName(): DistinguishedName {
    val x500Name = X500Name(this.name)
    val attributes = mutableMapOf<String, String>()

    x500Name.rdNs.forEach { rdn ->
        rdn.typesAndValues.forEach { tv ->
            val oid = tv.type.id
            val value = IETFUtils.valueToString(tv.value)
            attributes[oid] = value
        }
    }

    return DistinguishedName(attributes)
}
