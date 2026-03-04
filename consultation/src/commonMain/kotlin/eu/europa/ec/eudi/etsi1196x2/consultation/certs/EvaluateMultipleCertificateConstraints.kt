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

/**
 * A composite validator that validates multiple constraints against a certificate.
 *
 * This class aggregates multiple [EvaluateCertificateConstraint] instances and validates
 * them all against a given certificate, collecting any validation failures.
 *
 * @param CERT the type representing the certificate
 *
 * @see EvaluateCertificateConstraint
 */
public class EvaluateMultipleCertificateConstraints<in CERT : Any>(
    internal val constraints: List<EvaluateCertificateConstraint<CERT>>,
) : EvaluateCertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): CertificateConstraintEvaluation {
        val violations =
            constraints.mapNotNull { evaluate ->
                val evaluation = evaluate(certificate)
                if (!evaluation.isMet()) evaluation.violations else null
            }.flatten()
        return CertificateConstraintEvaluation(violations)
    }

    public companion object {
        /**
         * Creates a [EvaluateMultipleCertificateConstraints] from a vararg array of constraints.
         */
        public fun <CERT : Any> of(vararg constraints: EvaluateCertificateConstraint<CERT>): EvaluateMultipleCertificateConstraints<CERT> =
            EvaluateMultipleCertificateConstraints(constraints.toList())

        /**
         * Creates a [EvaluateMultipleCertificateConstraints] from a list of constraints.
         */
        public fun <CERT : Any> fromList(constraints: List<EvaluateCertificateConstraint<CERT>>): EvaluateMultipleCertificateConstraints<CERT> =
            EvaluateMultipleCertificateConstraints(constraints)

        /**
         * Creates an empty validator that always passes.
         */
        public fun <CERT : Any> empty(): EvaluateMultipleCertificateConstraints<CERT> =
            EvaluateMultipleCertificateConstraints(emptyList())
    }
}

/**
 * Combines two [EvaluateMultipleCertificateConstraints] instances into one.
 */
public operator fun <CERT : Any> EvaluateMultipleCertificateConstraints<CERT>.plus(
    other: EvaluateMultipleCertificateConstraints<CERT>,
): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints(
    this.constraints + other.constraints,
)

/**
 * Adds a single constraint to an existing validator.
 */
public operator fun <CERT : Any> EvaluateMultipleCertificateConstraints<CERT>.plus(
    constraint: EvaluateCertificateConstraint<CERT>,
): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints(
    this.constraints + constraint,
)

/**
 * Creates a [EvaluateMultipleCertificateConstraints] from a collection of constraints.
 */
public fun <CERT : Any> Collection<EvaluateCertificateConstraint<CERT>>.toValidator(): EvaluateMultipleCertificateConstraints<CERT> =
    EvaluateMultipleCertificateConstraints(this.toList())
