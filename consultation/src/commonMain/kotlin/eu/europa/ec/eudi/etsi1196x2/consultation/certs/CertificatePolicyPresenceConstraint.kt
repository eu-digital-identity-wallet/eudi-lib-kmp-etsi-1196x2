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
 * Constraint: Validates the presence of the certificatePolicies extension (EN 319 412-2).
 *
 * This constraint checks whether a certificate contains the certificatePolicies extension.
 * Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present and shall contain
 * the identifier of at least one certificate policy which reflects the practices and procedures
 * undertaken by the CA. The specific policy OIDs are TSP-defined and are not validated by this constraint.
 *
 * @param CERT the type representing the certificate
 * @param getCertificatePolicies a function to extract certificate policy OIDs from a certificate
 */
public class CertificatePolicyPresenceConstraint<CERT : Any>(
    private val getCertificatePolicies: suspend (CERT) -> List<String>,
) : EvaluateCertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): CertificateConstraintEvaluation {
        val policies = getCertificatePolicies(certificate)

        val violations = buildList {
            if (policies.isEmpty()) {
                add(missingCertificatePoliciesExtension())
            }
        }
        return CertificateConstraintEvaluation(violations)
    }

    public companion object {
        /**
         * Creates a violation for certificate missing certificatePolicies extension.
         *
         * Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present.
         */
        public fun missingCertificatePoliciesExtension(): CertificateConstraintViolation =
            CertificateConstraintViolation(
                "Certificate does not contain certificatePolicies extension. " +
                    "Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present " +
                    "with at least one TSP-defined policy OID.",
            )

        /**
         * Creates a constraint that validates the presence of certificatePolicies extension.
         *
         * This is suitable for PID and Wallet Provider certificates per EN 319 412-2 §4.3.3.
         * The specific policy OIDs are TSP-defined and are not validated.
         */
        public fun <CERT : Any> requirePresence(
            getCertificatePolicies: suspend (CERT) -> List<String>,
        ): CertificatePolicyPresenceConstraint<CERT> = CertificatePolicyPresenceConstraint(
            getCertificatePolicies = getCertificatePolicies,
        )
    }
}
