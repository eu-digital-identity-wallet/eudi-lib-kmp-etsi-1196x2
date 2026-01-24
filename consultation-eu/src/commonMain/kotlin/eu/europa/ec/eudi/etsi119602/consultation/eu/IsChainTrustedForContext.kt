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

import eu.europa.ec.eudi.etsi119602.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi119602.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi119602.consultation.TrustSource

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 */
public fun interface IsChainTrustedForContext<in CHAIN : Any> {

    public sealed interface Outcome {
        public data object Trusted : Outcome
        public data class NotTrusted(val cause: Throwable) : Outcome
        public data object UnsupportedVerificationContext : Outcome
    }

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
    ): Outcome

    public companion object {

        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            trustSourcePerVerificationContext: (VerificationContext) -> TrustSource?,
            sources: Map<TrustSource, IsChainTrusted<CHAIN, TRUST_ANCHOR>>,
        ): IsChainTrustedForContext<CHAIN> =
            IsChainTrustedForContext { chain, verificationContext ->
                when (val trustSource = trustSourcePerVerificationContext(verificationContext)) {
                    null -> Outcome.UnsupportedVerificationContext
                    else -> {
                        when (val isChainTrusted = sources[trustSource]) {
                            null -> Outcome.UnsupportedVerificationContext
                            else -> when (val validation = isChainTrusted(chain)) {
                                is CertificationChainValidation.Trusted -> Outcome.Trusted
                                is CertificationChainValidation.NotTrusted -> Outcome.NotTrusted(validation.cause)
                            }
                        }
                    }
                }
            }
    }
}

public inline fun <C1 : Any, C2 : Any> IsChainTrustedForContext<C1>.contraMap(crossinline f: (C2) -> C1): IsChainTrustedForContext<C2> =
    IsChainTrustedForContext { chain, verificationContext -> invoke(f(chain), verificationContext) }
