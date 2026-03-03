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

import kotlin.time.Instant

/**
 * Result of validating a certificate constraint.
 */
public sealed interface ConstraintValidationResult {
    /**
     * The constraint is satisfied.
     */
    public data object Valid : ConstraintValidationResult

    /**
     * The constraint is not satisfied.
     *
     * @param reason a human-readable description of why the constraint failed
     * @param cause the underlying cause of the failure, if any
     */
    public data class Invalid(val reason: String, val cause: Throwable? = null) : ConstraintValidationResult
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
 * A constraint that can be validated against a certificate.
 *
 * This is a functional interface that allows validating specific aspects of a certificate,
 * such as basic constraints, key usage, QCStatements, etc.
 *
 * @param CERT the type representing the certificate (e.g., X509Certificate, ByteArray, etc.)
 *
 * @see CertificateConstraintValidator
 */
public fun interface CertificateConstraint<CERT : Any> {
    /**
     * Validates the constraint against the given certificate.
     *
     * @param certificate the certificate to validate
     * @return the result of the validation
     */
    public suspend operator fun invoke(certificate: CERT): ConstraintValidationResult
}
