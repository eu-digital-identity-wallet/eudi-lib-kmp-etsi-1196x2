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
 * An interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface IsChainTrustedForContextF<in CHAIN : Any, out TRUST_ANCHOR : Any> {

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
    ): CertificationChainValidation<TRUST_ANCHOR>?
}

/**
 * A default implementation of [IsChainTrustedForContextF]
 *
 * Combinators:
 * - [plus]: combine two instances of IsChainTrustedForContext into a single one
 * - [contraMap]: change the chain of certificates representation
 *
 * @param validateCertificateChain the certificate chain validation function
 * @param getTrustAnchorsByContext the supported verification contexts and their corresponding trust anchors sources

 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public class IsChainTrustedForContext<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val getTrustAnchorsByContext: Map<VerificationContext, GetTrustAnchors<TRUST_ANCHOR>>,
) : IsChainTrustedForContextF<CHAIN, TRUST_ANCHOR> {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check. A null value indicates that the given [verificationContext] has not been configured
     */
    public override suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        withContext(CoroutineName(name = "IsChainTrustedForContext - $verificationContext")) {
            getTrustAnchorsByContext[verificationContext]?.let { getTrustAnchors ->
                val trustAnchors = getTrustAnchors()
                validateCertificateChain(chain, trustAnchors.toSet())
            }
        }

    /**
     * Combines two instances into a single one
     *
     * ```kotlin
     * val a : IsChainTrustedForContext<CertificateChain, TrustAnchor> = ...
     * val b : IsChainTrustedForContext<CertificateChain, TrustAnchor> = ...
     * val combined = a + b
     * ```
     *
     * @param other another IsChainTrustedForContext instance
     * @return new instance with combined sources
     */
    public operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, @UnsafeVariance TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            validateCertificateChain,
            this.getTrustAnchorsByContext + other.getTrustAnchorsByContext,
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
     * @return new instance, accepting the new chain representation
     * @param C1 the new representation of the certificate chain
     */
    public fun <C1 : Any> contraMap(transform: (C1) -> CHAIN): IsChainTrustedForContext<C1, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            validateCertificateChain.contraMap(transform),
            getTrustAnchorsByContext,
        )

    /**
     * Creates a new [IsChainTrustedForContext]
     * that applies the specified recovery logic in addition to the current
     *
     * Do not use this method unless you know what you are doing.
     *
     * @param recovery  a recovery function that generates alternative validations based on a
     *     [VerificationContext] and a [CertificationChainValidation.NotTrusted] result.
     * @return a new instance that applies the specified recovery logic in addition to the current
     *         validation logic.
     */
    @SensitiveApi
    public fun recoverWith(
        recovery: (VerificationContext) -> ((CertificationChainValidation.NotTrusted) -> GetTrustAnchors<@UnsafeVariance TRUST_ANCHOR>?),
    ): IsChainTrustedForContextF<CHAIN, TRUST_ANCHOR> =
        UnsafeIsChainTrustedForContext(validateCertificateChain, getTrustAnchorsByContext, recovery)

    public companion object {

        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getTrustAnchorsFromSource: GetTrustAnchorsFromSource<VerificationContext, TRUST_ANCHOR>,
            supportedContexts: Set<VerificationContext>,
        ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> {
            val getTrustAnchorsByContext: Map<VerificationContext, GetTrustAnchors<TRUST_ANCHOR>> =
                supportedContexts.associateWith { GetTrustAnchors { getTrustAnchorsFromSource(it) } }
            return IsChainTrustedForContext(validateCertificateChain, getTrustAnchorsByContext)
        }
    }
}
