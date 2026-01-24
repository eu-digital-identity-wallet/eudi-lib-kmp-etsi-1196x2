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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.withContext

/**
 * Interface for checking the trustworthiness of a certificate chain
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface IsChainTrusted<in CHAIN : Any, out TRUST_ANCHOR : Any> {

    /**
     * Validates the trustworthiness of a certificate chain
     *
     * @param chain the certificate chain to validate
     * @return the validation outcome
     */
    public suspend operator fun invoke(chain: CHAIN): CertificationChainValidation<TRUST_ANCHOR>

    public companion object {

        /**
         * Creates an instance of IsChainTrusted with the given validation and trust anchor retrieval functions
         *
         * @param validateCertificateChain function to validate a certificate chain
         * @param getTrustAnchors function to retrieve trust anchors
         * @return an instance of IsChainTrusted
         */
        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getTrustAnchors: suspend () -> List<TRUST_ANCHOR>,
        ): IsChainTrusted<CHAIN, TRUST_ANCHOR> =
            IsChainTrusted { chain ->
                withContext(CoroutineName(name = "IsChainTrusted-$chain")) {
                    val trustAnchors = getTrustAnchors()
                    validateCertificateChain(chain, trustAnchors.toSet())
                }
            }
    }
}

public inline fun <C1 : Any, C2 : Any, TC : Any> IsChainTrusted<C1, TC>.contraMap(
    crossinline f: (C2) -> C1,
): IsChainTrusted<C2, TC> = IsChainTrusted { chain -> invoke(f(chain)) }
