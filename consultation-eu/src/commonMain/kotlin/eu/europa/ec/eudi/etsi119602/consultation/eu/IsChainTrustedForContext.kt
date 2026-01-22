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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi119602.consultation.TrustSource
import eu.europa.ec.eudi.etsi119602.consultation.ValidateCertificateChain

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 */
public fun interface IsChainTrustedForContext<in CHAIN : Any> {

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
    ): ValidateCertificateChain.Outcome

    public companion object {

        public operator fun <CHAIN : Any> invoke(
            trustSourcePerVerificationContext: (VerificationContext) -> TrustSource,
            isChainTrusted: IsChainTrusted<CHAIN, TrustSource>,
        ): IsChainTrustedForContext<CHAIN> =
            IsChainTrustedForContext { chain, verificationContext ->
                val trustSource = trustSourcePerVerificationContext(verificationContext)
                isChainTrusted(chain, trustSource)
            }
    }
}
