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

/**
 * A composite validator that validates multiple constraints against a certificate.
 *
 * This class aggregates multiple [CertificateConstraint] instances and validates
 * them all against a given certificate, collecting any validation failures.
 *
 * @param CERT the type representing the certificate
 *
 * @see CertificateConstraint
 */
public class CertificateConstraintValidator<CERT : Any>(
    internal val constraints: List<CertificateConstraint<CERT>>,
) {
    /**
     * Validates all constraints against the given certificate.
     *
     * @param certificate the certificate to validate
     * @return a list of validation failures (empty if all constraints are satisfied)
     */
    public suspend fun validate(certificate: CERT): List<ConstraintValidationResult.Invalid> {
        return constraints.mapNotNull { constraint ->
            when (val result = constraint(certificate)) {
                is ConstraintValidationResult.Invalid -> result
                is ConstraintValidationResult.Valid -> null
            }
        }
    }

    /**
     * Checks if the certificate passes all constraints.
     *
     * @param certificate the certificate to validate
     * @return true if all constraints are satisfied, false otherwise
     */
    public suspend fun isValid(certificate: CERT): Boolean = validate(certificate).isEmpty()

    public companion object {
        /**
         * Creates a [CertificateConstraintValidator] from a vararg array of constraints.
         */
        public fun <CERT : Any> of(vararg constraints: CertificateConstraint<CERT>): CertificateConstraintValidator<CERT> =
            CertificateConstraintValidator(constraints.toList())

        /**
         * Creates a [CertificateConstraintValidator] from a list of constraints.
         */
        public fun <CERT : Any> fromList(constraints: List<CertificateConstraint<CERT>>): CertificateConstraintValidator<CERT> =
            CertificateConstraintValidator(constraints)

        /**
         * Creates an empty validator that always passes.
         */
        public fun <CERT : Any> empty(): CertificateConstraintValidator<CERT> =
            CertificateConstraintValidator(emptyList())
    }
}

/**
 * Combines two [CertificateConstraintValidator] instances into one.
 */
public operator fun <CERT : Any> CertificateConstraintValidator<CERT>.plus(
    other: CertificateConstraintValidator<CERT>,
): CertificateConstraintValidator<CERT> = CertificateConstraintValidator(
    this.constraints + other.constraints,
)

/**
 * Adds a single constraint to an existing validator.
 */
public operator fun <CERT : Any> CertificateConstraintValidator<CERT>.plus(
    constraint: CertificateConstraint<CERT>,
): CertificateConstraintValidator<CERT> = CertificateConstraintValidator(
    this.constraints + constraint,
)

/**
 * Creates a [CertificateConstraintValidator] from a collection of constraints.
 */
public fun <CERT : Any> Collection<CertificateConstraint<CERT>>.toValidator(): CertificateConstraintValidator<CERT> =
    CertificateConstraintValidator(this.toList())
