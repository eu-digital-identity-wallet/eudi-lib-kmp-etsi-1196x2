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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Instant

/**
 * The constraint is not satisfied.
 *
 * @param reason a human-readable description of why the constraint failed
 * @param cause the underlying cause of the failure, if any
 */
public data class CertificateConstraintViolation(val reason: String, val cause: Throwable? = null)

/**
 * Result of validating a certificate constraint.
 */
public sealed interface CertificateConstraintEvaluation {
    /**
     * The constraint is satisfied.
     */
    public data object Met : CertificateConstraintEvaluation

    /**
     * The constraint is not satisfied.
     */
    public data class Violated(public val violations: List<CertificateConstraintViolation>) :
        CertificateConstraintEvaluation {
        init {
            require(violations.isNotEmpty()) { "At least one violation is required" }
        }
    }

    public companion object {
        public operator fun invoke(violations: List<CertificateConstraintViolation>): CertificateConstraintEvaluation =
            if (violations.isEmpty()) Met else Violated(violations)
    }
}

@OptIn(ExperimentalContracts::class)
public fun CertificateConstraintEvaluation.isMet(): Boolean {
    contract {
        returns(true) implies (this@isMet is CertificateConstraintEvaluation.Met)
        returns(false) implies (this@isMet is CertificateConstraintEvaluation.Violated)
    }
    return this is CertificateConstraintEvaluation.Met
}

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
 * A constraint that can be validated against a certificate.
 *
 * This is a functional interface that allows validating specific aspects of a certificate,
 * such as basic constraints, key usage, QCStatements, etc.
 *
 * @param CERT the type representing the certificate (e.g., X509Certificate, ByteArray, etc.)
 *
 * @see EvaluateMultipleCertificateConstraints
 */
public fun interface EvaluateCertificateConstraint<in CERT : Any> {
    /**
     * Validates the constraint against the given certificate.
     *
     * @param certificate the certificate to validate
     * @return the result of the validation
     */
    public suspend operator fun invoke(certificate: CERT): CertificateConstraintEvaluation
}

/**
 * Checks if the specified certificate satisfies the evaluated constraint.
 *
 * @param certificate the certificate to validate against the constraint
 * @return true if the certificate satisfies the constraint, false otherwise
 * @param CERT the type representing the certificate
 */
public suspend fun <CERT : Any> EvaluateCertificateConstraint<CERT>.isValid(
    certificate: CERT,
): Boolean =
    invoke(certificate).isMet()

/**
 * Evaluates the specified constraint against a collection of certificates and maps each certificate
 * to its evaluation result.
 *
 * @param CERT the type representing the certificate (e.g., X509Certificate, ByteArray, etc.)
 * @param getCertificateInfo a suspend function used to derive a unique identifier or descriptor
 *                           for each certificate
 * @param certificates an iterable collection of certificates to be evaluated
 * @return a map where the keys are the values produced by the `getCertificateInfo` function for
 *         each certificate, and the values are the evaluation results of the corresponding
 *         certificates
 */
public suspend fun <CERT : Any> EvaluateCertificateConstraint<CERT>.evaluateAll(
    getCertificateInfo: suspend (CERT) -> String,
    certificates: Iterable<CERT>,
): Map<String, CertificateConstraintEvaluation> = buildMap {
    certificates.forEach { certificate ->
        val evaluation = invoke(certificate)
        put(getCertificateInfo(certificate), evaluation)
    }
}

/**
 * Ensures that all certificates meet the specified constraint.
 * Checks do not stop at the first violation.
 *
 * @param certificates an iterable collection of certificates to be evaluated
 * @param getCertificateInfo a suspend function used to derive a unique identifier or descriptor
 *                           for each certificate
 * @param CERT the type representing the certificate
 * @throws IllegalStateException if any certificate does not meet the constraint
 */
@Throws(IllegalStateException::class)
public suspend fun <CERT : Any> EvaluateCertificateConstraint<CERT>.ensureAllMet(
    certificates: Iterable<CERT>,
    getCertificateInfo: suspend (CERT) -> String = { it.toString() },
) {
    val violationsPerCert = buildMap {
        val evaluations = evaluateAll(getCertificateInfo, certificates)
        for ((certInfo, evaluation) in evaluations) {
            if (!evaluation.isMet()) {
                put(certInfo, evaluation)
            }
        }
    }

    fun error() = buildString {
        appendLine("Profile violations on ${violationsPerCert.size}  anchors")
        for ((certInfo, violated) in violationsPerCert) {
            appendLine("- Certificate: $certInfo")
            appendLine("- Violations: ")
            violated.violations.forEachIndexed { index, violation ->
                appendLine("  ${index + 1}. ${violation.reason}")
            }
        }
    }

    check(violationsPerCert.isEmpty()) { error() }
}
