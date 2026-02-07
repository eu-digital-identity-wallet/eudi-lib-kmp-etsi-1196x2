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
 * A class for checking the trustworthiness of a certificate chain
 * in the context of a specific verification context
 *
 *
 * @param CHAIN type representing a certificate chain
 * @param CTX type representing the verification context
 * @param TRUST_ANCHOR type representing a trust anchor
 */
@SensitiveApi
internal class UnsafeIsChainTrustedForContext<in CHAIN : Any, CTX : Any, out TRUST_ANCHOR : Any>(
    private val primary: IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>,
    private val recovery: (CertificationChainValidation.NotTrusted) -> IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>?,
) : IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

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
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        when (val validation = primary(chain, verificationContext)) {
            null -> null
            is CertificationChainValidation.Trusted<TRUST_ANCHOR> -> validation
            is CertificationChainValidation.NotTrusted ->
                withContext(CoroutineName(name = "Recovering - $verificationContext")) {
                    tryToRecover(chain, verificationContext, validation)
                }
        }

    private suspend fun tryToRecover(
        chain: CHAIN,
        verificationContext: CTX,
        notTrusted: CertificationChainValidation.NotTrusted,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        recovery(notTrusted)?.let { fallback -> fallback(chain, verificationContext) }
}
