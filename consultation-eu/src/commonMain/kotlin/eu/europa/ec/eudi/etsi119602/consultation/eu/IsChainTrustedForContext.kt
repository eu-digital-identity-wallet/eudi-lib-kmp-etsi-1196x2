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

/**
 * Interface for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface IsChainTrustedForContext<in CHAIN : Any, out TRUST_ANCHOR : Any> {

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
    ): CertificationChainValidation<TRUST_ANCHOR>?

    public companion object {

        public fun <CHAIN : Any, TRUST_ANCHOR : Any> default(
            trust: Map<VerificationContext, IsChainTrusted<CHAIN, TRUST_ANCHOR>>,
        ): DefaultIsChainTrustedForContext<CHAIN, TRUST_ANCHOR> = DefaultIsChainTrustedForContext(trust)
    }
}

public class DefaultIsChainTrustedForContext<CHAIN : Any, TRUST_ANCHOR : Any>(
    private val trust: Map<VerificationContext, IsChainTrusted<CHAIN, TRUST_ANCHOR>>,
) : IsChainTrustedForContext<CHAIN, TRUST_ANCHOR> {

    override suspend fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): CertificationChainValidation<TRUST_ANCHOR>? = trust[verificationContext]?.invoke(chain)

    public operator fun plus(
        other: DefaultIsChainTrustedForContext<CHAIN, TRUST_ANCHOR>,
    ): DefaultIsChainTrustedForContext<CHAIN, TRUST_ANCHOR> =
        DefaultIsChainTrustedForContext(trust + other.trust)
}

public inline fun <C1 : Any, TA : Any, C2 : Any> IsChainTrustedForContext<C1, TA>.contraMap(crossinline f: (C2) -> C1): IsChainTrustedForContext<C2, TA> =
    IsChainTrustedForContext { chain, verificationContext -> invoke(f(chain), verificationContext) }
