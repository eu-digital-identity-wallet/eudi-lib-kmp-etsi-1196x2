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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.withContext

/**
 * Interface for checking the trustworthiness of a certificate chain
 *
 * - To get an instance of [IsChainTrusted], use the [IsChainTrusted.Companion.invoke] function.
 *
 * Combinators:
 * - [or]: combine two instances of IsChainTrusted into a single one, which will be used as a fallback if the first one fails
 * - [contraMap]: change the chain of certificates representation
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public sealed interface IsChainTrusted<in CHAIN : Any, out TRUST_ANCHOR : Any> {

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
            getTrustAnchors: GetTrustAnchors<TRUST_ANCHOR>,
        ): IsChainTrusted<CHAIN, TRUST_ANCHOR> = IsChainTrustedDefault(validateCertificateChain, getTrustAnchors)
    }
}

/**
 * Transforms the input type of the IsChainTrusted instance using the provided function.
 * @receiver The original IsChainTrusted instance.
 * @param transform The transformation function that maps from C2 to C1.
 * @return A new IsChainTrusted instance with the input type transformed.
 */
public fun <C2 : Any, C1 : Any, TA : Any> IsChainTrusted<C1, TA>.contraMap(
    transform: (C2) -> C1,
): IsChainTrusted<C2, TA> = IsChainTrustedContraMap(this, transform)

/**
 * Combines two IsChainTrusted instances into a single one, where the second one is used as a fallback if the first one fails.
 * @receiver The primary IsChainTrusted instance.
 * @param other The fallback IsChainTrusted instance.
 * @return A new IsChainTrusted instance that combines the primary and fallback validators.
 */
public infix fun <C1 : Any, TA : Any> IsChainTrusted<C1, TA>.or(
    other: IsChainTrusted<C1, TA>,
): IsChainTrusted<C1, TA> = IsChainTrustedWithAlternative(this, other)

//
// Implementations
//
private class IsChainTrustedDefault<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val getTrustAnchors: GetTrustAnchors<TRUST_ANCHOR>,
) : IsChainTrusted<CHAIN, TRUST_ANCHOR> {

    override suspend fun invoke(chain: CHAIN): CertificationChainValidation<TRUST_ANCHOR> =
        withContext(CoroutineName(name = "IsChainTrusted")) {
            val trustAnchors = getTrustAnchors()
            validateCertificateChain(chain, trustAnchors.toSet())
        }
}

private class IsChainTrustedContraMap<in CHAIN2 : Any, CHAIN1 : Any, out TRUST_ANCHOR : Any>(
    private val base: IsChainTrusted<CHAIN1, TRUST_ANCHOR>,
    private val transform: (CHAIN2) -> CHAIN1,
) : IsChainTrusted<CHAIN2, TRUST_ANCHOR> {

    override suspend fun invoke(chain: CHAIN2): CertificationChainValidation<TRUST_ANCHOR> =
        base(transform(chain))
}

private class IsChainTrustedWithAlternative<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val primary: IsChainTrusted<CHAIN, TRUST_ANCHOR>,
    private val fallback: IsChainTrusted<CHAIN, TRUST_ANCHOR>,
) : IsChainTrusted<CHAIN, TRUST_ANCHOR> {

    override suspend fun invoke(chain: CHAIN): CertificationChainValidation<TRUST_ANCHOR> =
        when (val validation = primary(chain)) {
            is CertificationChainValidation.Trusted<TRUST_ANCHOR> -> validation
            is CertificationChainValidation.NotTrusted -> fallback(chain)
        }
}
