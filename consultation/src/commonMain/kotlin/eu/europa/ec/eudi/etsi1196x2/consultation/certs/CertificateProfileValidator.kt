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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

public interface CertificateProfileValidator<CERT : Any> {
    /**
     * Validates a single constraint against a certificate.
     */
    public suspend fun <T> validate(
        constraint: CertificateConstraint<T>,
        certificate: CERT,
    ): CertificateConstraintEvaluation

    /**

     * Validates all constraints in a profile against a certificate.
     *
     * @param profile the profile to validate
     * @param certificate the certificate to validate
     * @return [CertificateConstraintEvaluation.Met] if all constraints are satisfied,
     *         or [CertificateConstraintEvaluation.Violated] with all violations otherwise
     */
    public suspend fun validate(
        profile: CertificateProfile,
        certificate: CERT,
    ): CertificateConstraintEvaluation

    /**
     * Validates a profile against multiple certificates.
     *
     * @param profile the profile to validate
     * @param certificates the certificates to validate
     * @param getCertificateInfo a function to derive an identifier for each certificate
     * @return a map of certificate identifiers to their evaluation results
     */
    public suspend fun validateAll(
        profile: CertificateProfile,
        certificates: Iterable<CERT>,
        getCertificateInfo: (CERT) -> String,
    ): Map<String, CertificateConstraintEvaluation>

    /**
     * Validates that all certificates meet the profile requirements.
     *
     * @throws IllegalStateException if any certificate does not meet the profile
     */
    public suspend fun ensureAllMet(
        profile: CertificateProfile,
        certificates: Iterable<CERT>,
        getCertificateInfo: (CERT) -> String = { it.toString() },
    )

    public companion object {
        /**
         * Creates a [CertificateProfileValidator] using the provided [CertificateOperations].
         *
         * @param CERT the type representing the certificate
         * @param operations the implementation of certificate operations
         */
        public operator fun <CERT : Any> invoke(
            operations: CertificateOperations<CERT>,
            dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
        ): CertificateProfileValidator<CERT> =
            invoke(CertificateProfileInterpreter(operations, dispatcher))

        /**
         * Creates a [CertificateProfileValidator] using the provided [CertificateOperations].
         *
         * @param CERT the type representing the certificate
         * @param interpreter the implementation of certificate operations
         */
        public operator fun <CERT : Any> invoke(
            interpreter: CertificateProfileInterpreter<CERT>,
        ): CertificateProfileValidator<CERT> =
            DefaultCertificateValidator(interpreter)
    }
}

/**
 * Interprets certificate operations and validates profiles against certificates.
 *
 * This is the platform-specific component that provides the actual implementation
 * for extracting certificate data and executing constraints.
 *
 * @param CERT the type representing the certificate
 */
public interface CertificateProfileInterpreter<CERT : Any> {
    /**
     * Interprets a certificate operation and extracts the corresponding data.
     *
     * @param op the operation to interpret
     * @param certificate the certificate to extract data from
     * @return the extracted data
     */
    public suspend fun <T> interpret(op: CertificateOperationsAlgebra<T>, certificate: CERT): T

    public companion object {
        public operator fun <CERT : Any> invoke(
            operations: CertificateOperations<CERT>,
            dispatcher: CoroutineDispatcher,
        ): CertificateProfileInterpreter<CERT> =
            object : CertificateProfileInterpreter<CERT> {
                override suspend fun <T> interpret(
                    op: CertificateOperationsAlgebra<T>,
                    certificate: CERT,
                ): T = withContext(dispatcher) {
                    @Suppress("UNCHECKED_CAST")
                    when (op) {
                        is CertificateOperationsAlgebra.GetBasicConstraints ->
                            operations.getBasicConstraints(certificate) as T

                        is CertificateOperationsAlgebra.GetKeyUsage ->
                            operations.getKeyUsage(certificate) as T

                        is CertificateOperationsAlgebra.GetValidity ->
                            operations.getValidityPeriod(certificate) as T

                        is CertificateOperationsAlgebra.GetPolicies ->
                            operations.getCertificatePolicies(certificate) as T

                        is CertificateOperationsAlgebra.CheckSelfSigned ->
                            operations.isSelfSigned(certificate) as T

                        is CertificateOperationsAlgebra.GetAia ->
                            operations.getAiaExtension(certificate) as T

                        is CertificateOperationsAlgebra.GetAiaWithSelfSigned ->
                            AiaWithSelfSigned(
                                isSelfSigned = operations.isSelfSigned(certificate),
                                aia = operations.getAiaExtension(certificate),
                            ) as T

                        is CertificateOperationsAlgebra.GetQcStatements ->
                            operations.getQcStatements(certificate)
                                .filter { it.qcType == op.qcType } as T
                    }
                }
            }
    }
}

/**
 * Default implementation of [CertificateProfileInterpreter].
 */
private class DefaultCertificateValidator<CERT : Any>(
    private val interpreter: CertificateProfileInterpreter<CERT>,
) : CertificateProfileValidator<CERT> {
    override suspend fun <T> validate(
        constraint: CertificateConstraint<T>,
        certificate: CERT,
    ): CertificateConstraintEvaluation =
        constraint.validate(interpreter.interpret(constraint.op, certificate))

    override suspend fun validate(
        profile: CertificateProfile,
        certificate: CERT,
    ): CertificateConstraintEvaluation {
        val violations = profile.requirements
            .mapNotNull { constraint ->
                @Suppress("UNCHECKED_CAST")
                val evaluated = validate(constraint as CertificateConstraint<Any>, certificate)
                if (!evaluated.isMet()) evaluated.violations else null
            }
            .flatten()
        return CertificateConstraintEvaluation(violations)
    }

    override suspend fun validateAll(
        profile: CertificateProfile,
        certificates: Iterable<CERT>,
        getCertificateInfo: (CERT) -> String,
    ): Map<String, CertificateConstraintEvaluation> =
        buildMap {
            certificates.forEach { certificate ->
                put(getCertificateInfo(certificate), validate(profile, certificate))
            }
        }

    override suspend fun ensureAllMet(
        profile: CertificateProfile,
        certificates: Iterable<CERT>,
        getCertificateInfo: (CERT) -> String,
    ) {
        val violationsPerCert = validateAll(profile, certificates, getCertificateInfo)
            .mapValues { (_, evaluation) ->
                evaluation as CertificateConstraintEvaluation.Violated
            }

        if (violationsPerCert.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Profile violations on ${violationsPerCert.size} anchors")
                    for ((certInfo, violated) in violationsPerCert) {
                        appendLine("- Certificate: $certInfo")
                        appendLine("- Violations: ")
                        violated.violations.forEachIndexed { index, violation ->
                            appendLine("  ${index + 1}. ${violation.reason}")
                        }
                    }
                },
            )
        }
    }
}
