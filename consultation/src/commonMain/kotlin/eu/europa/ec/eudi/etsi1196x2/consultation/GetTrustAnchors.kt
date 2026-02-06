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

import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors.Companion.DEFAULT_SCOPE
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Represents a function that retrieves trust anchors from a source, using a query
 *
 * @param QUERY the type of the query parameter
 * @param TRUST_ANCHOR the type of the trust anchors returned by this function
 */
public fun interface GetTrustAnchors<in QUERY : Any, out TRUST_ANCHOR : Any> {

    /**
     * Retrieves trust anchors from a trusted source, using a query
     * @param query the query parameter
     *
     * @return a list of trust anchors, or null if no trust anchors could not be retrieved
     */
    public suspend operator fun invoke(query: QUERY): NonEmptyList<TRUST_ANCHOR>?

    public companion object {
        /**
         * The default scope for [GetTrustAnchors] instances.
         * [Dispatchers.Default] + [SupervisorJob]
         */
        public val DEFAULT_SCOPE: CoroutineScope get() = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

/**
 * Create a [GetTrustAnchors] that uses the [other]
 * if no trust anchors could be retrieved from the current instance.
 *
 * @param other the other [GetTrustAnchors] instance to combine with
 * @param Q the query type
 * @param TA the trust anchor type
 */
public infix fun <Q : Any, TA : Any> GetTrustAnchors<Q, TA>.or(
    other: GetTrustAnchors<Q, TA>,
): GetTrustAnchors<Q, TA> =
    GetTrustAnchors { source -> this.invoke(source) ?: other(source) }

/**
 * Changes the query dialect
 *
 * @param transformation the transformation between queries
 * @return a new instance of [GetTrustAnchors] that uses the new query dialect
 *
 * @param Q the current query dialect
 * @param Q2 the new query dialect
 * @param TA the trust anchor type
 */
public fun <Q : Any, TA : Any, Q2 : Any> GetTrustAnchors<Q, TA>.contraMap(
    transformation: (Q2) -> Q,
): GetTrustAnchors<Q2, TA> = GetTrustAnchors { source -> invoke(transformation(source)) }

/**
 * Returns a new instance that caches invocations per query
 *
 * @param coroutineScope the overall scope of the resulting [GetTrustAnchors]. By default, adds [SupervisorJob]
 *       Defaults to [DEFAULT_SCOPE]
 * @param clock the clock used to retrieve the current time.
 *        Defaults to [Clock.System]
 * @param expectedQueries the name of distinct queries.
 * @param ttl the time-to-live to keep the outcome of the query in the cache.
 * @receiver The [GetTrustAnchors] instance to cache
 *
 * @return the [GetTrustAnchors] instance that caches invocations
 */
public fun <Q : Any, TA : Any> GetTrustAnchors<Q, TA>.cached(
    coroutineScope: CoroutineScope = DEFAULT_SCOPE,
    clock: Clock = Clock.System,
    ttl: Duration,
    expectedQueries: Int,
): GetTrustAnchors<Q, TA> =
    GetTrustAnchorsCachedSource(coroutineScope, clock, ttl, expectedQueries, this)

internal class GetTrustAnchorsCachedSource<in QUERY : Any, out TRUST_ANCHOR : Any>(
    scope: CoroutineScope,
    clock: Clock,
    ttl: Duration,
    expectedQueries: Int,
    val proxied: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
) : GetTrustAnchors<QUERY, TRUST_ANCHOR> {

    private val cached: AsyncCache<QUERY, NonEmptyList<TRUST_ANCHOR>?> =
        AsyncCache(scope, clock, ttl, expectedQueries) { trustSource ->
            proxied(trustSource)
        }

    override suspend fun invoke(query: QUERY): NonEmptyList<TRUST_ANCHOR>? = cached(query)
}
