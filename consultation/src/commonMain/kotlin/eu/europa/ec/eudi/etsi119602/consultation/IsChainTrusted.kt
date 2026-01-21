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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.profile.*

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 */
public fun interface IsChainTrusted<in CHAIN : Any> {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     */
    public suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): ValidateCertificateChain.Outcome

    public companion object {
        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getTrustAnchorsByVerificationContext: GetTrustAnchorsByVerificationContext<TRUST_ANCHOR>,
        ): IsChainTrusted<CHAIN> =
            IsChainTrusted { chain, signatureVerification ->
                val trustAnchors = getTrustAnchorsByVerificationContext(signatureVerification)
                validateCertificateChain(chain, trustAnchors)
            }

        public fun <CHAIN : Any, TRUST_ANCHOR : Any> usingLoTE(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getListByProfile: GetListByProfile,
            trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
        ): IsChainTrusted<CHAIN> =
            invoke(validateCertificateChain, GetTrustAnchorsByVerificationContext.usingLoTE(getListByProfile, trustAnchorCreatorByVerificationContext))
    }
}

public inline fun <C : Any, C1 : Any> IsChainTrusted<C>.contraMap(crossinline f: (C1) -> C): IsChainTrusted<C1> =
    IsChainTrusted { c1, sv -> invoke(f(c1), sv) }
