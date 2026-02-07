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
 * A typealias for [IsChainTrustedForContext] having as context the [VerificationContext]
 */
public typealias IsChainTrustedForEUDIW<CHAIN, TRUST_ANCHOR> = IsChainTrustedForContext<CHAIN, VerificationContext, TRUST_ANCHOR>
