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

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator

/**
 * An abstraction for validating a certificate chain, given a set of trust anchors
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface ValidateCertificateChain<in CHAIN : Any, TRUST_ANCHOR : Any> {

    /**
     * Validates a certificate chain against a set of trust anchors
     * @param chain the certificate chain to validate
     * @param trustAnchors the set of trust anchors
     * @return the outcome of the validation
     */
    public suspend operator fun invoke(
        chain: CHAIN,
        trustAnchors: NonEmptyList<TRUST_ANCHOR>,
    ): CertificationChainValidation<TRUST_ANCHOR>

    public companion object
}

/**
 * A functional interface for validating certificate chains using PKIX (X.509 Public Key Infrastructure).
 *
 * PKIX validation performs cryptographic verification including:
 * - Signature verification for each certificate in the chain
 * - Path building from the leaf certificate to a trust anchor
 * - Validity period checking
 * - Certificate revocation checking (if enabled)
 * - Extension and constraint validation
 *
 * Implementations should use standard PKIX algorithms as defined in RFC 5280.
 *
 * @param CHAIN the type representing the certificate chain to validate
 * @param TRUST_ANCHOR the type representing a trust anchor
 *
 * @see ValidateCertificateChain
 * @see java.security.cert.CertPathValidator
 */
public fun interface ValidateCertificateChainUsingPKIX<in CHAIN : Any, TRUST_ANCHOR : Any> :
    ValidateCertificateChain<CHAIN, TRUST_ANCHOR>

public class ValidateCertificateChainUsingDirectTrust<in CHAIN : Any, TRUST_ANCHOR : Any, in CERT_ID : Any>(
    private val headCertificateId: (CHAIN) -> CERT_ID,
    private val trustToCertificateId: (TRUST_ANCHOR) -> CERT_ID,
) : ValidateCertificateChain<CHAIN, TRUST_ANCHOR> {
    override suspend fun invoke(
        chain: CHAIN,
        trustAnchors: NonEmptyList<TRUST_ANCHOR>,
    ): CertificationChainValidation<TRUST_ANCHOR> {
        val head = headCertificateId(chain)
        val trustAnchor = trustAnchors.list.firstOrNull { trustToCertificateId(it) == head }
        return if (trustAnchor != null) {
            CertificationChainValidation.Trusted(trustAnchor)
        } else {
            CertificationChainValidation.NotTrusted(IllegalStateException("Not trusted"))
        }
    }
}

/**
 * Represents the outcome of the validation
 *
 * - [Trusted] if the chain is trusted, with the trust anchor
 * - [NotTrusted] if the chain is not trusted, with the cause of the failure
 *
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public sealed interface CertificationChainValidation<out TRUST_ANCHOR : Any> {
    /**
     * The chain is trusted
     *
     * @param trustAnchor the trust anchor that matched the chain
     */
    public data class Trusted<out TRUST_ANCHOR : Any>(val trustAnchor: TRUST_ANCHOR) :
        CertificationChainValidation<TRUST_ANCHOR>

    /**
     * The chain is not trusted
     *
     * @param cause the cause of the failure
     */
    public data class NotTrusted(val cause: Throwable) : CertificationChainValidation<Nothing>
}

/**
 * Changes the representation of the certificate chain
 *
 * @param transform the transformation function
 * @return a new [ValidateCertificateChain] instance
 *
 * @param C1 type representing the input certificate chain
 * @param C2 type representing the output certificate chain
 * @param TA type representing a trust anchor
 */
public fun <C1 : Any, TA : Any, C2 : Any> ValidateCertificateChain<C1, TA>.contraMap(
    transform: (C2) -> C1,
): ValidateCertificateChain<C2, TA> = ValidateCertificateChainContraMap(this, transform)

private class ValidateCertificateChainContraMap<C1 : Any, TA : Any, C2 : Any>(
    private val delegate: ValidateCertificateChain<C1, TA>,
    private val transform: (C2) -> C1,
) : ValidateCertificateChain<C2, TA> {
    override suspend fun invoke(chain: C2, trustAnchors: NonEmptyList<TA>): CertificationChainValidation<TA> =
        delegate(transform(chain), trustAnchors)
}

public infix fun <C : Any, TA : Any> ValidateCertificateChain<C, TA>.or(
    other: ValidateCertificateChain<C, TA>,
): ValidateCertificateChain<C, TA> =
    ValidateCertificateChainUsingAlternatives(this, other)

private class ValidateCertificateChainUsingAlternatives<CHAIN : Any, TRUST_ANCHOR : Any>(
    private val primary: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val alternative: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
) : ValidateCertificateChain<CHAIN, TRUST_ANCHOR> {
    override suspend fun invoke(
        chain: CHAIN,
        trustAnchors: NonEmptyList<TRUST_ANCHOR>,
    ): CertificationChainValidation<TRUST_ANCHOR> =
        when (val primary = primary(chain, trustAnchors)) {
            is CertificationChainValidation.Trusted -> primary
            is CertificationChainValidation.NotTrusted -> alternative(chain, trustAnchors)
        }
}

/**
 * Wraps this validator to first validate end-entity certificate constraints
 * before performing chain validation.
 *
 * This is a convenience overload for [ValidateCertificateChain] with `CHAIN = List<CERT>`.
 * Extracts the first certificate from the chain as the end-entity certificate.
 *
 * @param certificateProfileValidator validator to use for end-entity certificates
 * @param endEntityCertificateProfile constraint evaluator for end-entity certificates
 * @return a new validator that enforces constraints before chain validation
 *
 * @see withEndEntityProfile general version with custom extractor
 */
public fun <TA : Any, CERT : Any> ValidateCertificateChain<List<CERT>, TA>.withEndEntityProfile(
    certificateProfileValidator: CertificateProfileValidator<CERT>,
    endEntityCertificateProfile: CertificateProfile,
): ValidateCertificateChain<List<CERT>, TA> =
    withEndEntityProfile(certificateProfileValidator, endEntityCertificateProfile) { chain ->
        requireNotNull(chain.firstOrNull()) { "Chain cannot be empty" }
    }

/**
 * Wraps this validator to first validate end-entity certificate constraints
 * before performing chain validation.
 * @param endEntityCertificateProfile profile to validate against
 * @param endEntityCertificateOf function to extract the end-entity certificate from the chain
 * @param certificateProfileValidator validator to use for end-entity certificates
 * @return a new validator that enforces constraints before chain validation
 * @see withEndEntityProfile general version with custom extractor
 */
public fun <CHAIN : Any, TA : Any, CERT : Any> ValidateCertificateChain<CHAIN, TA>.withEndEntityProfile(
    certificateProfileValidator: CertificateProfileValidator<CERT>,
    endEntityCertificateProfile: CertificateProfile,
    endEntityCertificateOf: (CHAIN) -> CERT,
): ValidateCertificateChain<CHAIN, TA> =
    ValidateCertificateChainWithEndEntityProfile(
        this,
        endEntityCertificateProfile,
        certificateProfileValidator,
        endEntityCertificateOf,
    )

private class ValidateCertificateChainWithEndEntityProfile<CHAIN : Any, TRUST_ANCHOR : Any, CERT : Any>(
    private val primary: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val endEntityCertificateProfile: CertificateProfile,
    private val certificateProfileValidator: CertificateProfileValidator<CERT>,
    private val endEntityCertificateOf: (CHAIN) -> CERT,
) : ValidateCertificateChain<CHAIN, TRUST_ANCHOR> {

    override suspend fun invoke(
        chain: CHAIN,
        trustAnchors: NonEmptyList<TRUST_ANCHOR>,
    ): CertificationChainValidation<TRUST_ANCHOR> {
        val endEntityCertificate = endEntityCertificateOf(chain)
        return when (val evaluation = evaluateCertificateConstraints(endEntityCertificate)) {
            is CertificateConstraintEvaluation.Met -> primary(chain, trustAnchors)
            is CertificateConstraintEvaluation.Violated -> {
                noTrustedDueToViolations(evaluation)
            }
        }
    }

    private suspend fun evaluateCertificateConstraints(certificate: CERT): CertificateConstraintEvaluation =
        certificateProfileValidator.validate(endEntityCertificateProfile, certificate)

    private fun noTrustedDueToViolations(
        violated: CertificateConstraintEvaluation.Violated,
    ): CertificationChainValidation.NotTrusted {
        val msg =
            "End-entity Certificate violates constraints: " + violated.violations.joinToString(separator = "\n") { it.reason }
        return CertificationChainValidation.NotTrusted(IllegalStateException(msg))
    }
}
