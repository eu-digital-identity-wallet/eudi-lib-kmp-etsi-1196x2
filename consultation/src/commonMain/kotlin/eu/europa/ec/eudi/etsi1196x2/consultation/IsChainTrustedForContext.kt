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
 * An [IsChainTrustedForContextF] for a set of supported contexts, that
 * - share the same [GetTrustAnchors] source
 * - share the same [ValidateCertificateChain]
 *
 * That's the elementary aggregation unit of [ComposeChainTrust].
 */
public class IsChainTrustedForContext<in CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any>(
    public val supportedContexts: Set<CTX>,
    getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR>,
    public val validateCertificateChain: ValidateCertificateChain<CHAIN, TRUST_ANCHOR>,
) : IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

    public val getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR> =
        @Suppress("ObjectLiteralToLambda")
        object : GetTrustAnchors<CTX, TRUST_ANCHOR> by getTrustAnchors {
            override suspend fun invoke(query: CTX): NonEmptyList<TRUST_ANCHOR>? =
                when (query) {
                    in supportedContexts -> getTrustAnchors(query)
                    else -> null
                }
        }

    override suspend fun invoke(chain: CHAIN, verificationContext: CTX): CertificationChainValidation<TRUST_ANCHOR>? =
        getTrustAnchors(verificationContext)?.let { validateCertificateChain(chain, it) }

    public operator fun contains(ctx: CTX): Boolean = ctx in supportedContexts

    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>,
    ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> =
        ComposeChainTrust.of(this, other)

    public fun <C1 : Any> contraMap(transformation: (C1) -> CHAIN): IsChainTrustedForContext<C1, CTX, TRUST_ANCHOR> =
        IsChainTrustedForContext(supportedContexts, getTrustAnchors, validateCertificateChain.contraMap(transformation))

    public companion object {

        internal operator fun <CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any> invoke(
            other: Triple<Set<CTX>, GetTrustAnchors<CTX, TRUST_ANCHOR>, ValidateCertificateChain<CHAIN, TRUST_ANCHOR>>,
        ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
            val (supportedCtx, getTrustAnchors, validateCertificateChain) = other
            return IsChainTrustedForContext(supportedCtx, getTrustAnchors, validateCertificateChain)
        }
    }
}

@Throws(IllegalArgumentException::class)
public fun <CHAIN, CTX, TA> Iterable<IsChainTrustedForContext<CHAIN, CTX, TA>>.compose(): ComposeChainTrust<CHAIN, CTX, TA>
        where CHAIN : Any, CTX : Any, TA : Any =
    fold(ComposeChainTrust.empty()) { acc, next -> acc + next }
