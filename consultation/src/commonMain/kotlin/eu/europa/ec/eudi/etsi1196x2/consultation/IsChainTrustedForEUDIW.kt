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
     * Check the wallet provider's signature for a Wallet Instance Attestation (WIA) and/or Key Attestation (KA)
     *
     * Can be used by an Authorization Server implementing
     * Attestation-Based Client Authentication to check the signature of a Wallet Instance Attestation (WIA).
     * In addition, can be used by a Credential issuer to check the signature of a Key Attestation (KA), during issuance
     * of device-bound attestations that require Key Attestation (KA)
     */
    public data object WalletProviderAttestation : VerificationContext

    /**
     * Check the wallet provider's signature for the Token Status List that keeps
     * the status of the wallet, located under `client_status` in Wallet Unit Attestation (WUA) or
     * the status of the keystorage, located under `key_storage.status` in a Key Attestation (KA)
     *
     * Can be used by Credential issuer to check the `client_status` of the Wallet or to check the `key_storage.status`
     * during issuance of device-bound attestations, that require Key Attestation (KA)
     */
    public data object WalletOrKeyStorageStatus : VerificationContext

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
     * Check the signature of a Status Lists that keeps the status of a registration certificate of an Issuer or Verifier
     *
     * Can be used by Wallets to verify the signature of the Token Status List that keeps the status of a registration
     * certificate of an Issuer or Verifier, during issuance and presentation respectively.
     */
    public data object WalletRelyingPartyRegistrationCertificateStatus : VerificationContext

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
 * A typealias for [IsChainTrustedForContextF] having as context the [VerificationContext]
 */
public typealias IsChainTrustedForEUDIW<CHAIN, TRUST_ANCHOR> = IsChainTrustedForContextF<CHAIN, VerificationContext, TRUST_ANCHOR>

public data class SupportedLists<out INFO : Any>(
    val pidProviders: INFO? = null,
    val walletProviders: INFO? = null,
    val wrpacProviders: INFO? = null,
    val wrprcProviders: INFO? = null,
    val pubEaaProviders: INFO? = null,
    val qeaProviders: INFO? = null,
    val eaaProviders: Map<String, INFO> = emptyMap(),
) : Iterable<INFO> {

    override fun iterator(): Iterator<INFO> =
        buildList {
            add(pidProviders)
            add(walletProviders)
            add(wrpacProviders)
            add(wrprcProviders)
            add(pubEaaProviders)
            add(qeaProviders)
            addAll(eaaProviders.values)
        }.filterNotNull().iterator()

    public companion object {

        public fun <L1 : Any, L2 : Any, L3 : Any> combine(
            s1: SupportedLists<L1>,
            s2: SupportedLists<L2>,
            combine: (L1, L2) -> L3,
        ): SupportedLists<L3> {
            val combineNullables = combine.forNullables()
            return SupportedLists(
                pidProviders = combineNullables(s1.pidProviders, s2.pidProviders),
                walletProviders = combineNullables(s1.walletProviders, s2.walletProviders),
                wrpacProviders = combineNullables(s1.wrpacProviders, s2.wrpacProviders),
                wrprcProviders = combineNullables(s1.wrprcProviders, s2.wrprcProviders),
                pubEaaProviders = combineNullables(s1.pubEaaProviders, s2.pubEaaProviders),
                qeaProviders = combineNullables(s1.qeaProviders, s2.qeaProviders),
                eaaProviders = s1.eaaProviders.mapNotNull { (useCase, l1) ->
                    val l2 = s2.eaaProviders[useCase]
                    combineNullables(l1, l2)?.let { useCase to it }
                }.toMap(),
            )
        }

        private fun <A : Any, B : Any, C : Any> ((A, B) -> C).forNullables(): (A?, B?) -> C? =
            { a, b -> a?.let { na -> b?.let { nb -> this(na, nb) } } }
    }
}
