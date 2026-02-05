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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.withContext

/**
 * Represents contexts for validating a certificate chain that are specific
 * to EUDI Wallet
 */
public sealed interface VerificationContext {
    /**
     * Check the wallet provider's signature for a Wallet Instance Attestation (WIA)
     *
     * Can be used by an Authorization Server implementing
     * Attestation-Based Client Authentication
     */
    public data object WalletInstanceAttestation : VerificationContext

    /**
     * Check the wallet provider's signature for a Wallet Unit Attestation (WUA)
     *
     * Can be used by a Credential Issuer, issuing device-bound attestations
     * that require WUA
     */
    public data object WalletUnitAttestation : VerificationContext

    /**
     * Check the wallet provider's signature for the Token Status List that keeps
     * the status of a Wallet Unit Attestation (WUA)
     *
     * Can be used by a Credential Issuer, issuing device-bound attestations
     * to keep track of WUA status
     */
    public data object WalletUnitAttestationStatus : VerificationContext

    /**
     * Check PID Provider's signature for a PID
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     */
    public data object PID : VerificationContext

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of a PID
     *
     * Can be used by Wallets and Verifiers to check the status of a PID
     */
    public data object PIDStatus : VerificationContext

    /**
     * Check the issuer's signature for a Public EAA
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     */
    public data object PubEAA : VerificationContext

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of a Public EAA
     *
     * Can be used by Wallets and Verifiers to check the status of a PUB_EAA
     */
    public data object PubEAAStatus : VerificationContext

    /**
     * Check the issuer's signature for a Qualified EAA
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     */
    public data object QEAA : VerificationContext

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status of
     * a qualified electronic attestation of attributes (QEAA), that supports revocation
     *
     * Can be used by Wallets and Verifiers to check the status of a QEAA
     */
    public data object QEAAStatus : VerificationContext

    /**
     * Check the issuer's signature for an electronic attestation of attributes (EAA)
     *
     * Can be used by Wallets after issuance and Verifiers during presentation verification
     *
     * @param useCase the use case of the EAA
     */
    public data class EAA(val useCase: String) : VerificationContext

    /**
     * Check the signature of a Status Lists or Identifiers List that keeps the status
     * of an electronic attestation of attributes (EAA), that supports revocation
     *
     * Can be used by Wallets and Verifiers to check the status of an EAA
     *
     * @param useCase the use case of the EAA
     */
    public data class EAAStatus(val useCase: String) : VerificationContext

    /**
     * Check the signature of a registration certificate of an Issuer or Verifier
     *
     * Can be used by Wallets to verify the signature of the registration certificate of an Issuer or Verifier, during
     * issuance and presentation respectively.
     */
    public data object WalletRelyingPartyRegistrationCertificate : VerificationContext

    /**
     * Check the access certificate of an Issuer or Verifier
     *
     * Can be used by Wallets to verify the signature of the registration certificate of an Issuer or Verifier, during
     * issuance (signed credential issuer metadata) and presentation respectively (signed authorization request).
     */
    public data object WalletRelyingPartyAccessCertificate : VerificationContext

    /**
     * Custom verification context
     */
    public data class Custom(val useCase: String) : VerificationContext
}

/**
 * A class for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * Combinators:
 * - [plus]: combine two instances of IsChainTrustedForContext into a single one
 * - [recoverWith]: combine two instances of IsChainTrustedForContext into a single one,
 *   where the second one is used as a fallback if the first one fails, conditionally
 * - [or]: combine two instances of IsChainTrustedForContext into a single one,
 *   where the second one is used as a fallback if the first one fails
 * - [contraMap]: change the chain of certificates representation
 *
 * @param validateCertificateChain the certificate chain validation function
 * @param getTrustAnchorsByContext the supported verification contexts and their corresponding trust anchors sources
 * @param getRecoveryTrustAnchors the supported verification contexts and their corresponding recovery functions for
 *        failed certificate chain validation attempts. The recovery functions are used to generate alternative
 *        validation functions in the context of an error.
 *        Defaults to [noRecovery]
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public class IsChainTrustedForContext<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val getTrustAnchorsByContext: Map<VerificationContext, GetTrustAnchors<TRUST_ANCHOR>>,
    private val getRecoveryTrustAnchors: Map<VerificationContext, ((Throwable) -> GetTrustAnchors<TRUST_ANCHOR>?)> = noRecovery(),
) {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check. A null value indicates that the given [verificationContext] has not been configured
     */
    public suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        withContext(CoroutineName(name = "IsChainTrustedForContext - $verificationContext")) {
            getTrustAnchorsByContext[verificationContext]?.let { getTrustAnchors ->
                val trustAnchors = getTrustAnchors()
                when (val validation = validateCertificateChain(chain, trustAnchors.toSet())) {
                    is CertificationChainValidation.Trusted<TRUST_ANCHOR> -> validation
                    is CertificationChainValidation.NotTrusted ->
                        tryToRecover(chain, verificationContext, validation) ?: validation
                }
            }
        }

    private suspend fun tryToRecover(
        chain: CHAIN,
        verificationContext: VerificationContext,
        notTrusted: CertificationChainValidation.NotTrusted,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        getRecoveryTrustAnchors[verificationContext]
            ?.let { fallBack -> fallBack(notTrusted.cause) }
            ?.let { getFallbackTrustAnchors -> validateCertificateChain(chain, getFallbackTrustAnchors().toSet()) }

    /**
     * Combines two IsChainTrustedForContext instances into a single one
     *
     * ```kotlin
     * val a : IsChainTrustedForContext<CertificateChain, TrustAnchor> = ...
     * val b : IsChainTrustedForContext<CertificateChain, TrustAnchor> = ...
     * val combined = a + b
     * ```
     *
     * @param other another IsChainTrustedForContext instance
     * @return new IsChainTrustedForContext instance with combined trust
     */
    public operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, @UnsafeVariance TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            validateCertificateChain,
            this.getTrustAnchorsByContext + other.getTrustAnchorsByContext,
            combine(getRecoveryTrustAnchors, other.getRecoveryTrustAnchors),
        )

    /**
     * Changes the chain of certificates representation
     *
     * ```kotlin
     * val a : IsChainTrustedForContext<List<Cert>, TrustAnchor> = ...
     * fun fromDer(der: ByteArray): Cert =
     * val b : IsChaintTrustedForContext<List<ByteArray>, TrustAnchor> = a.contraMap{ it.map(fromDer) }
     * ```
     *
     * @param transform transformation function
     * @return new IsChainTrustedForContext accepting the new chain representation
     * @param C1 the new representation of the certificate chain
     */
    public fun <C1 : Any> contraMap(transform: (C1) -> CHAIN): IsChainTrustedForContext<C1, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            validateCertificateChain.contraMap(transform),
            getTrustAnchorsByContext,
            getRecoveryTrustAnchors,
        )

    /**
     * Extends the existing validation performed per `VerificationContext` with the passed alternative validation.
     *
     * Please be careful when using this operator. Ideally, prefer using the [recoverWith].
     *
     * @param alternative another `IsChainTrusted` instance that will be added as an alternative validation for every `VerificationContext`.
     * @return a new `IsChainTrustedForContext` instance that its existing validation per `VerificationContext` is extended with an
     *        alternative validation.
     */
    public fun or(
        alternative: GetTrustAnchors<@UnsafeVariance TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        or { _ -> alternative }

    /**
     * Given a function that takes a `VerificationContext` and returns a `IsChainTrusted` instance (optionally),
     * extends the existing validation performed per `VerificationContext` with alternative validation.
     *
     *  Please be careful when using this operator. Ideally, prefer using the [recoverWith].
     *
     * @param alternative a function that takes a `VerificationContext` and returns optionally an
     *        `IsChainTrusted` instance, which will be used as an alternative validator to the existing
     *        validation for a specific `VerificationContext`.
     * @return a new `IsChainTrustedForContext` instance that is extended with alternative validations
     *        per `VerificationContext`.
     *
     */
    public fun or(
        alternative: (VerificationContext) -> GetTrustAnchors<@UnsafeVariance TRUST_ANCHOR>?,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        recoverWith { ctx -> { alternative(ctx) } }

    /**
     * Extends the current instance by adding recovery logic for each `VerificationContext`.
     * Allows defining a recovery function that generates alternative validations based on thrown exceptions during
     * chain trust verification.
     *
     * @param recovery a function that takes a `VerificationContext` and returns another function. This returned function
     *        maps a `Throwable` to an optional `IsChainTrusted` instance, which provides alternative validation logic
     *        in the context of an error.
     * @return a new `IsChainTrustedForContext` instance that applies the specified recovery logic in addition to the current
     *         validation logic.
     */
    public fun recoverWith(
        recovery: (VerificationContext) -> ((Throwable) -> GetTrustAnchors<@UnsafeVariance TRUST_ANCHOR>?)?,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> {
        val otherRecovery = buildMap {
            for (ctx in getTrustAnchorsByContext.keys) {
                val fallback = recovery(ctx)
                if (fallback != null) {
                    put(ctx, fallback)
                }
            }
        }
        return IsChainTrustedForContext(
            validateCertificateChain,
            getTrustAnchorsByContext,
            combine(getRecoveryTrustAnchors, otherRecovery),
        )
    }

    public companion object {
        /**
         * No recovery
         */
        public fun <TRUST_ANCHOR : Any> noRecovery(): Map<VerificationContext, ((Throwable) -> GetTrustAnchors<TRUST_ANCHOR>?)> =
            emptyMap()

        internal fun <TRUST_ANCHOR : Any> combine(
            a: Map<VerificationContext, ((Throwable) -> GetTrustAnchors<TRUST_ANCHOR>?)>,
            b: Map<VerificationContext, ((Throwable) -> GetTrustAnchors<TRUST_ANCHOR>?)>,
        ): Map<VerificationContext, ((Throwable) -> GetTrustAnchors<TRUST_ANCHOR>?)> =
            buildMap {
                val ctxs = a.keys + b.keys
                for (ctx in ctxs) {
                    val fallbackA = a[ctx]
                    val fallbackB = b[ctx]
                    val combined = { cause: Throwable ->
                        fallbackA?.invoke(cause) ?: fallbackB?.invoke(cause)
                    }
                    put(ctx, combined)
                }
            }
    }
}
