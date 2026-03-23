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
package eu.europa.ec.eudi.etsi1196x2.consultation.certs

import kotlin.time.Instant

public interface CertificateOperations<CERT : Any> {
    public fun getBasicConstraints(certificate: CERT): BasicConstraintsInfo
    public fun getQcStatements(certificate: CERT): List<QCStatementInfo>
    public fun getKeyUsage(certificate: CERT): ExtensionInfo<KeyUsageBits>?
    public fun getValidityPeriod(certificate: CERT): ValidityPeriod
    public fun getCertificatePolicies(certificate: CERT): ExtensionInfo<List<String>>?
    public fun isSelfSigned(certificate: CERT): Boolean
    public fun getAiaExtension(certificate: CERT): ExtensionInfo<AuthorityInformationAccess>?
    public fun getSubject(certificate: CERT): DistinguishedName?
    public fun getIssuer(certificate: CERT): DistinguishedName?
    public fun getSubjectAltNames(certificate: CERT): ExtensionInfo<List<SubjectAlternativeName>>?
    public fun getCrlDistributionPoints(certificate: CERT): List<CrlDistributionPoint>
    public fun getAuthorityKeyIdentifier(certificate: CERT): AuthorityKeyIdentifier?
    public fun getSubjectKeyIdentifier(certificate: CERT): ByteArray?
    public fun getSerialNumber(certificate: CERT): SerialNumber
    public fun getVersion(certificate: CERT): Version
    public fun getSubjectPublicKeyInfo(certificate: CERT): PublicKeyInfo
    public fun hasExtension(certificate: CERT, oid: String): Boolean
}

/**
 * The algebra of certificate operations.
 *
 * This sealed interface represents all the operations that can be performed
 * to extract information from a certificate. It serves as the functor in a
 * free monad design, separating the description of operations from their interpretation.
 *
 * @param T the type of value returned when this operation is interpreted
 */
public sealed interface CertificateOperationsAlgebra<out T> {
    /**
     * Extract the basic constraints extension (cA flag and optional pathLenConstraint).
     */
    public data object GetBasicConstraints : CertificateOperationsAlgebra<BasicConstraintsInfo>

    /**
     * Extract the key usage extension bits.
     * Returns null if the extension is not present.
     */
    public data object GetKeyUsage : CertificateOperationsAlgebra<ExtensionInfo<KeyUsageBits>?>

    /**
     * Extract the validity period (notBefore and notAfter dates).
     */
    public data object GetValidity : CertificateOperationsAlgebra<ValidityPeriod>

    /**
     * Extract all certificate policy OIDs.
     * Returns null if the extension is not present.
     */
    public data object GetPolicies : CertificateOperationsAlgebra<ExtensionInfo<List<String>>?>

    /**
     * Check if the certificate is self-signed.
     */
    public data object CheckSelfSigned : CertificateOperationsAlgebra<Boolean>

    /**
     * Extract the Authority Information Access (AIA) extension.
     * Returns null if the extension is not present.
     */
    public data object GetAia : CertificateOperationsAlgebra<ExtensionInfo<AuthorityInformationAccess>?>

    /**
     * Extract QCStatements of a specific type (ETSI EN 319 412-5).
     *
     * @param qcType the OID identifying the QC type (e.g., "0.4.0.194126.1.1" for id-etsi-qct-pid)
     */
    public data class GetQcStatements(val qcType: String) : CertificateOperationsAlgebra<List<QCStatementInfo>>

    /**
     * Extract the subject Distinguished Name.
     */
    public data object GetSubject : CertificateOperationsAlgebra<DistinguishedName?>

    /**
     * Extract the issuer Distinguished Name.
     */
    public data object GetIssuer : CertificateOperationsAlgebra<DistinguishedName?>

    /**
     * Extract Subject Alternative Names.
     * Returns null if the extension is not present.
     */
    public data object GetSubjectAltNames : CertificateOperationsAlgebra<ExtensionInfo<List<SubjectAlternativeName>>?>

    /**
     * Extract CRL Distribution Points.
     */
    public data object GetCrlDistributionPoints : CertificateOperationsAlgebra<List<CrlDistributionPoint>>

    /**
     * Extract the Authority Key Identifier extension.
     */
    public data object GetAuthorityKeyIdentifier : CertificateOperationsAlgebra<AuthorityKeyIdentifier?>

    /**
     * Extract the Subject Key Identifier extension.
     * Returns null if the extension is not present.
     */
    public data object GetSubjectKeyIdentifier : CertificateOperationsAlgebra<ByteArray?>

    /**
     * Extract the certificate serial number.
     */
    public data object GetSerialNumber : CertificateOperationsAlgebra<SerialNumber>

    /**
     * Extract the certificate version.
     */
    public data object GetVersion : CertificateOperationsAlgebra<Version>

    /**
     * Extract the subject public key information.
     */
    public data object GetSubjectPublicKeyInfo : CertificateOperationsAlgebra<PublicKeyInfo>

    /**
     * Extract all QCStatements from the certificate (ETSI EN 319 412-5).
     *
     * Unlike [GetQcStatements], this returns all QC statements regardless of type.
     */
    public data object GetAllQcStatements : CertificateOperationsAlgebra<List<QCStatementInfo>>

    /**
     * Check if the certificate contains a specific extension.
     */
    public data class HasExtension(val oid: String) : CertificateOperationsAlgebra<Boolean>

    public data class GetCombined<A, B, out C>(
        val first: CertificateOperationsAlgebra<A>,
        val second: CertificateOperationsAlgebra<B>,
        val combine: (A, B) -> C,
    ) : CertificateOperationsAlgebra<C>
}

/**
 * Information about the basicConstraints extension of a certificate.
 *
 * @param isCa whether the certificate is a CA certificate (cA=TRUE) or end-entity (cA=FALSE)
 * @param pathLenConstraint the maximum number of non-self-issued intermediate certificates that may follow this certificate in a valid certification path
 */
public data class BasicConstraintsInfo(
    val isCa: Boolean,
    val pathLenConstraint: Int?,
)

/**
 * Represents the key usage bits in a certificate (RFC 5280).
 *
 * @param digitalSignature bit 0 - digitalSignature
 * @param nonRepudiation bit 1 - nonRepudiation (contentCommitment)
 * @param keyEncipherment bit 2 - keyEncipherment
 * @param dataEncipherment bit 3 - dataEncipherment
 * @param keyAgreement bit 4 - keyAgreement
 * @param keyCertSign bit 5 - keyCertSign
 * @param crlSign bit 6 - cRLSign
 * @param encipherOnly bit 7 - encipherOnly
 * @param decipherOnly bit 8 - decipherOnly
 */
public data class KeyUsageBits(
    val digitalSignature: Boolean = false,
    val nonRepudiation: Boolean = false,
    val keyEncipherment: Boolean = false,
    val dataEncipherment: Boolean = false,
    val keyAgreement: Boolean = false,
    val keyCertSign: Boolean = false,
    val crlSign: Boolean = false,
    val encipherOnly: Boolean = false,
    val decipherOnly: Boolean = false,
)

/**
 * Information about a QCStatement in a certificate (ETSI EN 319 412-5).
 *
 * @param qcType the OID identifying the type of QCStatement (e.g., id-etsi-qct-pid)
 * @param qcCompliance whether the certificate is compliant with the QC type
 */
public data class QCStatementInfo(
    val qcType: String, // OID
    val qcCompliance: Boolean,
)

/**
 * Information about the validity period of a certificate.
 *
 * @param notBefore the date before which the certificate is not valid
 * @param notAfter the date after which the certificate is not valid
 */
public data class ValidityPeriod(
    val notBefore: Instant,
    val notAfter: Instant,
)

/**
 * Information about the Authority Information Access (AIA) extension of a certificate (RFC 5280).
 *
 * The AIA extension provides locations where the issuer's CA certificate and/or OCSP responder
 * can be found.
 *
 * @param caIssuersUri URI where the CA certificate can be retrieved (id-ad-caIssuers)
 * @param ocspUri URI of the OCSP responder (id-ad-ocsp)
 *
 * @see [RFC 5280 Section 4.2.2.1 - Authority Information Access](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)
 */
public data class AuthorityInformationAccess(
    val caIssuersUri: String?,
    val ocspUri: String?,
)

/**
 * Represents a Distinguished Name (DN) from a certificate subject or issuer.
 *
 * A Distinguished Name is a sequence of Relative Distinguished Name (RDN) components,
 * each containing attribute type-value pairs.
 *
 * @param attributes Map of attribute OID or short name to attribute value.
 *                   Common attributes include:
 *                   - "2.5.4.6" or "CN" -> countryName (e.g., "US", "DE")
 *                   - "2.5.4.3" or "CN" -> commonName
 *                   - "2.5.4.7" or "O" -> organizationName
 *                   - "2.5.4.97" or "organizationIdentifier" -> organizationIdentifier
 *                   - "2.5.4.4" or "SN" -> surname
 *                   - "2.5.4.42" or "G" -> givenName
 *
 * @see [RFC 5280 Section 4.1.2.4 - Issuer](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.4)
 * @see [RFC 5280 Section 4.1.2.6 - Subject](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.6)
 */
public data class DistinguishedName(
    val attributes: Map<String, String>,
) {
    public val commonName: String? get() = attributes[X500OIDs.COMMON_NAME]
    public val serialNumber: String? get() = attributes[X500OIDs.SERIAL_NUMBER]
    public val country: String? get() = attributes[X500OIDs.COUNTRY]
    public val locality: String? get() = attributes[X500OIDs.LOCALITY]
    public val state: String? get() = attributes[X500OIDs.STATE]
    public val street: String? get() = attributes[X500OIDs.STREET]
    public val organization: String? get() = attributes[X500OIDs.ORGANIZATION]
    public val organizationUnit: String? get() = attributes[X500OIDs.ORG_UNIT]
    public val surname: String? get() = attributes[X500OIDs.SURNAME]
    public val givenName: String? get() = attributes[X500OIDs.GIVEN_NAME]
    public val pseudonym: String? get() = attributes[X500OIDs.PSEUDONYM]
    public val organizationIdentifier: String? get() = attributes[X500OIDs.ORG_IDENTIFIER]

    public fun hasAttribute(oid: String): Boolean = attributes.containsKey(oid)

    public operator fun get(oid: String): String? = attributes[oid]

    public object X500OIDs {
        public const val COMMON_NAME: String = "2.5.4.3"
        public const val SERIAL_NUMBER: String = "2.5.4.5"
        public const val COUNTRY: String = "2.5.4.6"
        public const val LOCALITY: String = "2.5.4.7"
        public const val STATE: String = "2.5.4.8"
        public const val STREET: String = "2.5.4.9"
        public const val ORGANIZATION: String = "2.5.4.10"
        public const val ORG_UNIT: String = "2.5.4.11"
        public const val SURNAME: String = "2.5.4.4"
        public const val GIVEN_NAME: String = "2.5.4.42"
        public const val PSEUDONYM: String = "2.5.4.65"
        public const val ORG_IDENTIFIER: String = "2.5.4.97"
    }
}

/**
 * Wrapper for a certificate serial number.
 *
 * Per RFC 5280 Section 4.1.2.2, the serial number MUST be a positive integer.
 *
 * @param value the serial number as a byte array (must represent a positive integer)
 *
 * @see [RFC 5280 Section 4.1.2.2 - Serial Number](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.2)
 */
public data class SerialNumber(val value: ByteArray) {
    init {
        require(value.isNotEmpty()) { "Serial number must not be empty" }
        // Check that the number is positive (most significant bit must be 0)
        require(value[0].toInt() and 0x80 == 0) { "Serial number must be positive (MSB must be 0)" }
    }

    /**
     * Converts the serial number to a hexadecimal string representation.
     */
    public fun toHexString(): String = value.joinToString("") { "%02x".format(it) }

    /**
     * Checks if this serial number equals another.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SerialNumber) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = toHexString().uppercase()
}

/**
 * Represents the X.509 certificate version.
 *
 * X.509 versions:
 * - v1: version = 0 (no extensions)
 * - v2: version = 1 (issuerUniqueID, subjectUniqueID)
 * - v3: version = 2 (extensions)
 *
 * Note: The version field in X.509 is zero-based, so v3 is represented as 2.
 *
 * @param value the version number (0=v1, 1=v2, 2=v3)
 *
 * @see [RFC 5280 Section 4.1.2.1 - Version](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.1)
 */
public data class Version(val value: Int) {
    init {
        require(value >= 0) { "Version must be non-negative" }
    }

    /**
     * Returns the X.509 version name (v1, v2, or v3).
     */
    public val versionName: String
        get() = when (value) {
            0 -> "v1"
            1 -> "v2"
            2 -> "v3"
            else -> "v${value + 1}"
        }

    /**
     * Checks if this is X.509 version 3 (required for extensions).
     */
    public fun isV3(): Boolean = value == 2

    override fun toString(): String = versionName
}

/**
 * A set of acceptable public key options.
 *
 * A certificate's public key is compliant if it satisfies AT LEAST ONE of the requirements
 * (i.e., its algorithm matches and its key size is >= the required minimum).
 *
 * @param algorithmOptions the list of acceptable algorithm/key-size combinations
 */
public data class PublicKeyAlgorithmOptions(
    val algorithmOptions: List<AlgorithmRequirement>,
) {
    /**
     * A specific public key algorithm and minimum key size requirement.
     *
     * @param algorithm the algorithm name (e.g., "RSA", "EC", "ECDSA")
     * @param minimumKeySize the minimum acceptable key size in bits
     */
    public data class AlgorithmRequirement(
        val algorithm: String,
        val minimumKeySize: Int,

    ) {
        public companion object {
            public val RSA_2048: AlgorithmRequirement get() = AlgorithmRequirement("RSA", 2048)
            public val EC_256: AlgorithmRequirement get() = AlgorithmRequirement("EC", 256)
            public val ECDSA_256: AlgorithmRequirement get() = AlgorithmRequirement("ECDSA", 256)
        }
    }

    public companion object {
        /** Convenience factory accepting vararg requirements. */
        public fun of(vararg algorithmOptions: AlgorithmRequirement): PublicKeyAlgorithmOptions =
            PublicKeyAlgorithmOptions(algorithmOptions.toList())
    }
}

/**
 * Information about a certificate's public key.
 *
 * @param algorithm the public key algorithm (e.g., "RSA", "EC", "ECDSA")
 * @param keySize the key size in bits (e.g., 2048 for RSA, 256 for EC P-256)
 * @param parameters algorithm-specific parameters (e.g., EC curve OID)
 *
 * @see [RFC 5280 Section 4.1.2.7 - Subject Public Key Info](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.7)
 */
public data class PublicKeyInfo(
    val algorithm: String,
    val keySize: Int?,
    val parameters: ByteArray? = null,
) {
    /**
     * Checks if the algorithm is one of the specified algorithms.
     *
     * @param algorithms list of algorithm names to check against
     */
    public fun isAlgorithm(vararg algorithms: String): Boolean =
        algorithm.uppercase() in algorithms.map { it.uppercase() }

    /**
     * Checks if the key size meets a minimum requirement.
     *
     * @param minSize the minimum key size in bits
     */
    public fun hasMinimumKeySize(minSize: Int): Boolean =
        keySize != null && keySize >= minSize

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicKeyInfo) return false
        if (algorithm != other.algorithm) return false
        if (keySize != other.keySize) return false
        if (parameters != null) {
            if (other.parameters == null) return false
            if (!parameters.contentEquals(other.parameters)) return false
        } else if (other.parameters != null) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + (keySize ?: 0)
        result = 31 * result + (parameters?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Sealed interface representing Subject Alternative Name types.
 *
 * Per RFC 5280 Section 4.2.1.6, the subjectAltName extension can contain
 * various types of names bound to the subject.
 *
 * @see [RFC 5280 Section 4.2.1.6 - Subject Alternative Name](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6)
 */
public sealed interface SubjectAlternativeName {
    /**
     * Uniform Resource Identifier (URI).
     *
     * @param uri the URI value (e.g., "https://example.com")
     */
    public data class Uri(val uri: String) : SubjectAlternativeName

    /**
     * Email address (RFC 822 name).
     *
     * @param email the email address (e.g., "user@example.com")
     */
    public data class Email(val email: String) : SubjectAlternativeName

    /**
     * Telephone number.
     *
     * @param number the telephone number (e.g., "+1-555-555-5555")
     */
    public data class Telephone(val number: String) : SubjectAlternativeName

    /**
     * DNS name.
     *
     * @param dnsName the DNS name (e.g., "www.example.com")
     */
    public data class DNSName(val dnsName: String) : SubjectAlternativeName

    /**
     * IP address (IPv4 or IPv6).
     *
     * @param address the IP address as a byte array (4 bytes for IPv4, 16 bytes for IPv6)
     */
    public data class IPAddress(val address: ByteArray) : SubjectAlternativeName {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IPAddress) return false
            return address.contentEquals(other.address)
        }

        override fun hashCode(): Int = address.contentHashCode()

        override fun toString(): String =
            address.joinToString(".") { it.toUByte().toString() }
    }

    /**
     * Registered ID (OID).
     *
     * @param oid the registered ID (e.g., "1.2.3.4")
     */
    public data class RegisteredId(val oid: String) : SubjectAlternativeName

    /**
     * Other name (application-defined).
     *
     * @param oid the OID identifying the other name type
     * @param value the other name value
     */
    public data class OtherName(val oid: String, val value: String) : SubjectAlternativeName
}

/**
 * Information about the Authority Key Identifier extension.
 *
 * Per RFC 5280 Section 4.2.1.1, the authorityKeyIdentifier extension provides
 * a means of identifying the public key corresponding to the private key used
 * to sign this certificate.
 *
 * @param keyIdentifier the key identifier (hash of the public key)
 * @param authorityCertIssuer the issuer of the CA certificate (if present)
 * @param authorityCertSerialNumber the serial number of the CA certificate (if present)
 *
 * @see [RFC 5280 Section 4.2.1.1 - Authority Key Identifier](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.1)
 */
public data class AuthorityKeyIdentifier(
    val keyIdentifier: ByteArray?,
    val authorityCertIssuer: List<String>? = null,
    val authorityCertSerialNumber: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthorityKeyIdentifier) return false
        if (keyIdentifier != null) {
            if (other.keyIdentifier == null) return false
            if (!keyIdentifier.contentEquals(other.keyIdentifier)) return false
        } else if (other.keyIdentifier != null) {
            return false
        }
        if (authorityCertIssuer != other.authorityCertIssuer) return false
        if (authorityCertSerialNumber != null) {
            if (other.authorityCertSerialNumber == null) return false
            if (!authorityCertSerialNumber.contentEquals(other.authorityCertSerialNumber)) return false
        } else if (other.authorityCertSerialNumber != null) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = keyIdentifier?.contentHashCode() ?: 0
        result = 31 * result + (authorityCertIssuer ?: emptyList()).hashCode()
        result = 31 * result + (authorityCertSerialNumber?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String = buildString {
        append("AuthorityKeyIdentifier(")
        if (keyIdentifier != null) {
            append("keyIdentifier=${keyIdentifier.joinToString("") { "%02x".format(it) }}")
        }
        if (authorityCertSerialNumber != null) {
            append(", authorityCertSerialNumber=${authorityCertSerialNumber.joinToString("") { "%02x".format(it) }}")
        }
        append(")")
    }
}

/**
 * Information about a CRL Distribution Point.
 *
 * Per RFC 5280 Section 4.2.1.13, the cRLDistributionPoints extension identifies
 * how CRL information is obtained.
 *
 * @param distributionPointUri URI where the CRL can be retrieved
 * @param crlIssuer list of issuer names (if different from certificate issuer)
 *
 * @see [RFC 5280 Section 4.2.1.13 - CRL Distribution Points](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.13)
 */
public data class CrlDistributionPoint(
    val distributionPointUri: String?,
    val crlIssuer: List<String>? = null,
)

/**
 * Wrapper for extension data with criticality flag.
 *
 * Per RFC 5280 Section 4.2, each extension has a criticality flag indicating
 * whether the extension must be processed by conforming implementations.
 *
 * @param T the type of the extension data
 * @param value the extension data
 * @param isCritical true if the extension is marked critical
 *
 * @see [RFC 5280 Section 4.2 - Certificate Extensions](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2)
 */
public data class ExtensionInfo<out T>(
    val value: T,
    val isCritical: Boolean,
)
