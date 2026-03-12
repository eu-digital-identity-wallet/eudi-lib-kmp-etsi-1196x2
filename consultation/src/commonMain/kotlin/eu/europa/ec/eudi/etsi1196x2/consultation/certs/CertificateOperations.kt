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
    public fun getKeyUsage(certificate: CERT): KeyUsageBits?
    public fun getValidityPeriod(certificate: CERT): ValidityPeriod
    public fun getCertificatePolicies(certificate: CERT): List<String>
    public fun isSelfSigned(certificate: CERT): Boolean
    public fun getAiaExtension(certificate: CERT): AuthorityInformationAccess?
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
    public data object GetKeyUsage : CertificateOperationsAlgebra<KeyUsageBits?>

    /**
     * Extract the validity period (notBefore and notAfter dates).
     */
    public data object GetValidity : CertificateOperationsAlgebra<ValidityPeriod>

    /**
     * Extract all certificate policy OIDs.
     */
    public data object GetPolicies : CertificateOperationsAlgebra<List<String>>

    /**
     * Check if the certificate is self-signed.
     */
    public data object CheckSelfSigned : CertificateOperationsAlgebra<Boolean>

    /**
     * Extract the Authority Information Access (AIA) extension.
     * Returns null if the extension is not present.
     */
    public data object GetAia : CertificateOperationsAlgebra<AuthorityInformationAccess?>

    /**
     * Extract AIA extension combined with self-signed status.
     *
     * This operation is used for validating AIA requirements that only apply
     * to CA-issued (non-self-signed) certificates.
     */
    public data object GetAiaWithSelfSigned : CertificateOperationsAlgebra<AiaWithSelfSigned>

    /**
     * Extract QCStatements of a specific type (ETSI EN 319 412-5).
     *
     * @param qcType the OID identifying the QC type (e.g., "0.4.0.194126.1.1" for id-etsi-qct-pid)
     */
    public data class GetQcStatements(val qcType: String) : CertificateOperationsAlgebra<List<QCStatementInfo>>
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
 * Information about AIA extension combined with self-signed status.
 *
 * Used for validating AIA requirements that only apply to CA-issued (non-self-signed) certificates.
 *
 * @param isSelfSigned whether the certificate is self-signed
 * @param aia the AIA extension content (null if not present)
 */
public data class AiaWithSelfSigned(
    val isSelfSigned: Boolean,
    val aia: AuthorityInformationAccess?,
)
