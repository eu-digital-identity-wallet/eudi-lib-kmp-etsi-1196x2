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

internal typealias RecoverF<TRUST_ANCHOR> = (CertificationChainValidation.NotTrusted) -> GetTrustAnchors<TRUST_ANCHOR>?

/**
 * A class for checking the trustworthiness of a certificate chain
 * in the context of a specific [verification][VerificationContext]
 *
 *
 * @param validateCertificateChain the certificate chain validation function
 * @param getTrustAnchorsByContext the supported verification contexts and their corresponding trust anchors sources
 * @param getRecoveryTrustAnchors the supported verification contexts and their corresponding recovery functions for
 *        failed certificate chain validation attempts. The recovery functions are used to generate alternative
 *        validation functions in the context of an error.
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
@SensitiveApi
internal class UnsafeIsChainTrustedForContext<in CHAIN : Any, out TRUST_ANCHOR : Any>(
    private val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
    private val getTrustAnchorsByContext: Map<VerificationContext, GetTrustAnchors<TRUST_ANCHOR>>,
    private val getRecoveryTrustAnchors: Map<VerificationContext, RecoverF<TRUST_ANCHOR>>,
) : IsChainTrustedForContextF<CHAIN, TRUST_ANCHOR> {

    /**
     * Check certificate chain is trusted in the context of
     * specific verification
     *
     * @param chain certificate chain to check
     * @param verificationContext verification context
     * @return outcome of the check. A null value indicates that the given [verificationContext] has not been configured
     */
    override suspend operator fun invoke(
        chain: CHAIN,
        verificationContext: VerificationContext,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        withContext(CoroutineName(name = "UnsafeIsChainTrustedForContext - $verificationContext")) {
            getTrustAnchorsByContext[verificationContext]?.let { getTrustAnchors ->
                val trustAnchors = getTrustAnchors()
                when (val validation = validateCertificateChain(chain, trustAnchors.toSet())) {
                    is CertificationChainValidation.Trusted<TRUST_ANCHOR> -> validation
                    is CertificationChainValidation.NotTrusted ->
                        tryToRecover(chain, verificationContext, validation) ?: validation
                }
            }
        }

    private suspend fun tryToRecover(
        chain: CHAIN,
        verificationContext: VerificationContext,
        notTrusted: CertificationChainValidation.NotTrusted,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        getRecoveryTrustAnchors[verificationContext]
            ?.let { fallBack -> fallBack(notTrusted) }
            ?.let { getFallbackTrustAnchors -> validateCertificateChain(chain, getFallbackTrustAnchors().toSet()) }

    companion object {
        operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
            getTrustAnchorsByContext: Map<VerificationContext, GetTrustAnchors<TRUST_ANCHOR>>,
            recovery: (VerificationContext) -> RecoverF<@UnsafeVariance TRUST_ANCHOR>?,
        ): UnsafeIsChainTrustedForContext<CHAIN, TRUST_ANCHOR> {
            val getRecoveryTrustAnchors =
                buildMap {
                    for (ctx in getTrustAnchorsByContext.keys) {
                        val fallback = recovery(ctx)
                        if (fallback != null) {
                            put(ctx, fallback)
                        }
                    }
                }
            return UnsafeIsChainTrustedForContext(
                validateCertificateChain,
                getTrustAnchorsByContext,
                getRecoveryTrustAnchors,
            )
        }
    }
}
