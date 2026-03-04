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
 * Constraint: Validates the Authority Information Access (AIA) extension of a certificate.
 *
 * Per ETSI TS 119 412-6 PID-4.4.3-01, CA-issued certificates MUST have an AIA extension
 * with the id-ad-caIssuers access method pointing to the intermediate CA certificate.
 *
 * @param CERT the type representing the certificate
 * @param requiredForCaIssued whether AIA is required (true for CA-issued certificates)
 * @param isSelfSigned a function to check if the certificate is self-signed
 * @param getAiaExtension a function to extract AIA information from a certificate
 *
 * @see [ETSI TS 119 412-6 PID-4.4.3-01](https://www.etsi.org/deliver/etsi_ts/119400_119499/11941206/)
 * @see [RFC 5280 Section 4.2.2.1](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)
 */
public class EvaluateAuthorityInformationAccessConstraint<in CERT : Any>(
    private val requiredForCaIssued: Boolean,
    private val isSelfSigned: suspend (CERT) -> Boolean,
    private val getAiaExtension: suspend (CERT) -> AuthorityInformationAccess?,
) : EvaluateCertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): CertificateConstraintEvaluation {
        val violations = buildList {
            val aiaInfo = getAiaExtension(certificate)
            val selfSigned = isSelfSigned(certificate)

            when {
                !requiredForCaIssued || selfSigned -> {
                    // AIA not required (e.g., self-signed certificate or not specified)
                }

                aiaInfo == null -> {
                    add(
                        CertificateConstraintViolation(
                            "CA-issued certificate missing Authority Information Access (AIA) extension",
                        ),
                    )
                }

                aiaInfo.caIssuersUri == null -> {
                    add(
                        CertificateConstraintViolation(
                            "AIA extension missing id-ad-caIssuers access method (CA certificate URI)",
                        ),
                    )
                }

                else -> {}
            }
        }
        return CertificateConstraintEvaluation(violations)
    }

    public companion object {
        /**
         * Creates a constraint requiring AIA for CA-issued certificates.
         *
         * @param isSelfSigned function to check if the certificate is self-signed
         * @param getAiaExtension function to extract AIA information from a certificate
         */
        public fun <CERT : Any> requireForCaIssued(
            isSelfSigned: suspend (CERT) -> Boolean,
            getAiaExtension: suspend (CERT) -> AuthorityInformationAccess?,
        ): EvaluateAuthorityInformationAccessConstraint<CERT> = EvaluateAuthorityInformationAccessConstraint(
            requiredForCaIssued = true,
            isSelfSigned = isSelfSigned,
            getAiaExtension = getAiaExtension,
        )

        /**
         * Creates a constraint where AIA is optional.
         *
         * @param getAiaExtension function to extract AIA information from a certificate
         */
        public fun <CERT : Any> optional(
            getAiaExtension: suspend (CERT) -> AuthorityInformationAccess?,
        ): EvaluateAuthorityInformationAccessConstraint<CERT> = EvaluateAuthorityInformationAccessConstraint(
            requiredForCaIssued = false,
            isSelfSigned = { false },
            getAiaExtension = getAiaExtension,
        )
    }
}
