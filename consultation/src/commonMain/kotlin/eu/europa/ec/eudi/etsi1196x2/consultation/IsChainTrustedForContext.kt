/*
 * Copyright (c) 2023 European Commission
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

public sealed interface VerificationContext {
    /**
     * Check the wallet provider's signature for a WIA
     * Can be used by an Authorization Server implementing
     * Attestation-Based Client Authentication
     */
    public data object WalletInstanceAttestation : VerificationContext

    /**
     * Check the wallet provider's signature for a WUA
     * Can be used by a Credential Issuer, issuing device-bound attestations
     * that require WUA
     */
    public data object WalletUnitAttestation : VerificationContext

    /**
     * Check the wallet provider's signature for the Token Status List that keeps the status of a WUA
     *
     * Can be used by a Credential Issuer, issuing device-bound attestations to keep track of WUA status
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

    public data class EAA(val case: String) : VerificationContext
    public data class EAAStatus(val case: String) : VerificationContext
}

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public class IsChainTrustedForContext<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val trust: Map<VerificationContext, IsChainTrusted<CHAIN, TRUST_ANCHOR>>,
) {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check
     */
    public suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): CertificationChainValidation<TRUST_ANCHOR>? = trust[verificationContext]?.invoke(chain)

    /**
     * Combines two IsChainTrustedForContext instances into a single one
     *
     * @param other another IsChainTrustedForContext instance
     * @return new IsChainTrustedForContext instance with combined trust
     */
    public operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, @UnsafeVariance TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        IsChainTrustedForContext(trust + other.trust)

    /**
     * Changes the chain of certificates representation
     *
     * @param transform transformation function
     * @return new IsChainTrustedForContext accecpting the new chain representation
     * @param C1 the new representation of the certificate chain
     */
    public fun <C1 : Any> contraMap(transform: (C1) -> CHAIN): IsChainTrustedForContext<C1, TRUST_ANCHOR> =
        IsChainTrustedForContext(
            trust.mapValues { (_, isChainTrusted) ->
                isChainTrusted.contraMap(transform)
            },
        )

    public companion object
}
