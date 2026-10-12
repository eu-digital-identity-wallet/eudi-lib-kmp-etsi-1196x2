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

import kotlin.collections.mapValues
import kotlin.collections.plus

/**
 * Default implementation of [IsChainTrustedForContextF]
 *
 * It provides the means to combine multiple instances of trusted anchors and validation logic.
 * Combinators:
 * - [plus] combines two sources into one
 * - [contraMap] transforms the chain representation
 *
 * @param CHAIN the type of the certificate chain to be validated
 * @param CTX the type of the verification context
 * @param TRUST_ANCHOR the type of the trust anchor to be used for validation
 */
public class ComposeChainTrust<in CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any>
private constructor(
    private val sources: Map<Set<CTX>, IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>>,
) : IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> {

    /**
     * The contexts supported
     */
    public val supportedContexts: Set<CTX> by lazy { sources.keys.flatten().toSet() }

    /**
     * A function for retrieving trust anchors for a given context.
     */
    public val getTrustAnchors: GetTrustAnchors<CTX, TRUST_ANCHOR> =
        @Suppress("ObjectLiteralToLambda")
        object : GetTrustAnchors<CTX, TRUST_ANCHOR> {
            // Do not convert to lamda. Crashes in JVM due to NonEmptyList being a value class
            override suspend fun invoke(query: CTX): NonEmptyList<TRUST_ANCHOR>? = findSource(query)?.getTrustAnchors(query)
        }

    override suspend fun invoke(
        chain: CHAIN,
        verificationContext: CTX,
    ): CertificationChainValidation<TRUST_ANCHOR>? = findSource(verificationContext)?.invoke(chain, verificationContext)

    private fun findSource(ctx: CTX): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>? = sources.values.firstOrNull { ctx in it }

    /**
     * Combines two instances of [ComposeChainTrust] into one.
     * It is required that the two instances have disjoint sets of supported contexts.
     *
     * @param other the other source to combine with
     * @return a combined source
     * @throws IllegalArgumentException if the two instances have overlapping sets of supported contexts
     */
    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: ComposeChainTrust<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>,
    ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> {
        val common = this.supportedContexts.intersect(other.supportedContexts)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return ComposeChainTrust(this.sources + other.sources)
    }

    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: IsChainTrustedForContext<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>,
    ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> {
        val common = this.supportedContexts.intersect(other.supportedContexts)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return ComposeChainTrust(this.sources + (other.supportedContexts to other))
    }

    /**
     * Creates a new [ComposeChainTrust]
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
        recovery: (CTX, CertificationChainValidation.NotTrusted) -> IsChainTrustedForContext<@UnsafeVariance CHAIN, CTX, TRUST_ANCHOR>?,
    ): IsChainTrustedForContextF<CHAIN, CTX, TRUST_ANCHOR> = UnsafeIsChainTrustedForContext(this, recovery)

    public fun <C2 : Any> contraMap(transformation: (C2) -> CHAIN): ComposeChainTrust<C2, CTX, TRUST_ANCHOR> = ComposeChainTrust(
        sources.mapValues { (_, src) -> src.contraMap(transformation) },
    )

    public companion object {

        public fun <CHAIN : Any, CTX : Any, TA : Any> empty(): ComposeChainTrust<CHAIN, CTX, TA> = ComposeChainTrust(emptyMap())

        public operator fun <CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any> invoke(
            source: IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>,
        ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> = ComposeChainTrust(mapOf(source.supportedContexts to source))

        @Throws(IllegalArgumentException::class)
        public fun <CHAIN : Any, CTX : Any, TA : Any> of(
            vararg sources: IsChainTrustedForContext<CHAIN, CTX, TA>,
        ): ComposeChainTrust<CHAIN, CTX, TA> = sources.fold(empty()) { acc, next -> acc + next }
    }
}
