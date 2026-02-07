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
 * An interface for checking the trustworthiness of a certificate chain
 * in the context of a specific verification context
 *
 * @param CHAIN type representing a certificate chain
 * @param CTX type representing the verification context
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface IsChainTrustedForContextF<in CHAIN : Any, in CTX : Any, out TRUST_ANCHOR : Any> {

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
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>?
}

/**
 * A default implementation of [IsChainTrustedForContextF]
 *
 * @param validateCertificateChain the certificate chain validation function
 * @param getTrustAnchorsByContext the supported verification contexts and their corresponding trust anchors sources
 *
 * @param CHAIN type representing a certificate chain
 * @param CTX type representing the verification context
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public class IsChainTrustedForContext<in CHAIN : Any, CTX : Any, out TRUST_ANCHOR : Any>(
    private val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val getTrustAnchorsByContext: GetTrustAnchorsForSupportedQueries<CTX, TRUST_ANCHOR>,
) : IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check. A null value indicates that the given [verificationContext] has not been configured
     * @throws IllegalStateException if the given [verificationContext] was configured but underlying source didn't return anchors
     */
    @Throws(IllegalStateException::class)
    public override suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        withContext(CoroutineName(name = "IsChainTrustedForContext - $verificationContext")) {
            when (val outcome = getTrustAnchorsByContext(verificationContext)) {
                is GetTrustAnchorsForSupportedQueries.Outcome.Found<TRUST_ANCHOR> -> validateCertificateChain(chain, outcome.trustAnchors)
                GetTrustAnchorsForSupportedQueries.Outcome.MisconfiguredSource -> error("Could not get trust anchors for verification context $verificationContext")
                GetTrustAnchorsForSupportedQueries.Outcome.QueryNotSupported -> null
            }
        }

    /**
     * Changes the chain of certificates representation
     *
     * ```kotlin
     * val a : IsChainTrustedForContext<List<Cert>, VerificationContext, TrustAnchor> = ...
     * fun fromDer(der: ByteArray): Cert =
     * val b : IsChainTrustedForContext<List<ByteArray>, VerificationContext, TrustAnchor> = a.contraMap{ it.map(fromDer) }
     * ```
     *
     * @param transform transformation function
     * @return new instance, accepting the new chain representation
     * @param C1 the new representation of the certificate chain
     */
    public fun <C1 : Any> contraMap(transform: (C1) -> CHAIN): IsChainTrustedForContext<C1, CTX, TRUST_ANCHOR> =
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
     *     [CTX] and a [CertificationChainValidation.NotTrusted] result.
     * @return a new instance that applies the specified recovery logic in addition to the current
     *         validation logic.
     */
    @SensitiveApi
    public fun recoverWith(
        recovery: (CertificationChainValidation.NotTrusted) -> GetTrustAnchorsForSupportedQueries<CTX, @UnsafeVariance TRUST_ANCHOR>?,
    ): IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> =
        UnsafeIsChainTrustedForContext(this) { notTrusted ->
            recovery(notTrusted)?.let {
                IsChainTrustedForContext(validateCertificateChain, it)
            }
        }

    public companion object
}
