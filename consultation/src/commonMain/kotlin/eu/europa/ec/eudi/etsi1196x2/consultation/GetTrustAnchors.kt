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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A functional source for retrieving trust anchors from a trusted provider based on a specific query.
 *
 * This component defines the core contract for trust anchor discovery. It can be implemented to fetch
 * anchors from various backends such as local keystores, trusted lists (LOTL/TL), or remote services.
 *
 * This source can be:
 * - **Combined**: Using the [or] operator to chain multiple sources together.
 * - **Transformed**: Using [contraMap] to adapt the query type to a different dialect.
 * - **Cached**: Using [cached] to improve performance by storing previously retrieved results.
 *
 * @param QUERY the type of the query used to locate trust anchors
 * @param TRUST_ANCHOR the type of the trust anchors returned
 */
public fun interface GetTrustAnchors<in QUERY : Any, out TRUST_ANCHOR : Any> {

    /**
     * Retrieves trust anchors for the given [query].
     *
     * @param query the search criteria for locating trust anchors
     * @return a non-empty list of trust anchors if found, or `null` if the provider has no anchors
     *         matching the query or if the query itself is not supported
     */
    public suspend operator fun invoke(query: QUERY): NonEmptyList<TRUST_ANCHOR>?

    public companion object {
        /**
         * The default scope for background operations, such as cache maintenance.
         * Uses [Dispatchers.Default] combined with a [SupervisorJob].
         */
        public val DEFAULT_SCOPE: CoroutineScope get() = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

public fun <Q : Any, TA : Any, Q1 : Any> GetTrustAnchors<Q, TA>.transform(
    transformation: Map<Q1, Q>,
): GetTrustAnchorsForSupportedQueries<Q1, TA> =
    GetTrustAnchorsForSupportedQueries.transform(this, transformation)

/**
 * Combines this source with [other] to create a fallback chain.
 *
 * When the resulting source is invoked:
 * 1. It first attempts to retrieve anchors from the current instance.
 * 2. If the current instance returns `null`, it falls back to the [other] source.
 * 3. The final result is the first non-null list of anchors found in the chain.
 *
 * @param other the fallback source to use if this one yields no results
 * @param Q the query type
 * @param TA the trust anchor type
 * @return a composite source that implements fallback logic
 */
@SensitiveApi
public infix fun <Q : Any, TA : Any> GetTrustAnchors<Q, TA>.or(
    other: GetTrustAnchors<Q, TA>,
): GetTrustAnchors<Q, TA> =
    GetTrustAnchorsWithAlternative(this, other)

/**
 * Adapts this source to a different query type [Q2].
 *
 * This creates a new source that accepts queries of type [Q2], transforms them into
 * the original type [Q] using the provided [transformation], and then delegates to
 * the original source.
 *
 * @param transformation a function that maps the new query type back to the original dialect
 * @return a new source adapted for queries of type [Q2]
 *
 * @param Q the original query type
 * @param Q2 the new query type
 * @param TA the trust anchor type
 */
public fun <Q : Any, TA : Any, Q2 : Any> GetTrustAnchors<Q, TA>.contraMap(
    transformation: (Q2) -> Q,
): GetTrustAnchorsTransformingQuery<Q2, Q, TA> =
    GetTrustAnchorsTransformingQuery(this, transformation)

/**
 * Decorates this source with transparent caching of results.
 *
 * The resulting source will store the outcome of each unique query in memory.
 * Subsequent calls with the same query will return the cached value if it has not
 * yet expired according to the [ttl].
 *
 * The caching logic is powered by [AsyncCache], which ensures that:
 * - Concurrent requests for the same query result in a single backend invocation.
 * - Expired entries are automatically refreshed upon next access.
 * - Resources are managed within the provided [coroutineScope].
 *
 *  **Resource Management**: This class implements [AutoCloseable] and must be closed when no longer needed
 *  to release cached resources and cancel background operations. Failure to close may result in
 *  memory leaks and continued background processing.
 *
 * @param coroutineScope the scope in which the cache maintenance tasks will run
 * @param clock the provider of current time for expiration checks
 * @param ttl the maximum age of a cached entry before it must be refreshed
 * @param expectedQueries the estimated number of unique queries to optimize internal storage
 * @receiver the source whose results should be cached
 *
 * @return a cached version of this source
 */
public fun <Q : Any, TA : Any> GetTrustAnchors<Q, TA>.cached(
    coroutineScope: CoroutineScope = DEFAULT_SCOPE,
    clock: Clock = Clock.System,
    ttl: Duration,
    expectedQueries: Int,
): GetTrustAnchorsCachedSource<Q, TA> =
    GetTrustAnchorsCachedSource(coroutineScope, clock, ttl, expectedQueries, this)

/**
 * A cached implementation of [GetTrustAnchors] that stores results in memory for performance optimization.
 *
 * This implementation wraps a delegate [GetTrustAnchors] source and provides transparent caching:
 * - Results are stored in memory and expire after the configured [ttl]
 * - Concurrent requests for the same query result in a single invocation of the underlying source
 * - Cache size is bounded by the [expectedQueries] parameter
 *
 * **Resource Management**: This class implements [AutoCloseable] and must be closed when no longer needed
 * to release cached resources and cancel background operations. Failure to close may result in
 * memory leaks and continued background processing.
 *
 * @param QUERY the type of the query used to locate trust anchors
 * @param TRUST_ANCHOR the type of the trust anchors returned
 * @property delegate the underlying [GetTrustAnchors] source that provides the actual trust anchors
 */
public class GetTrustAnchorsCachedSource<in QUERY : Any, out TRUST_ANCHOR : Any>(
    scope: CoroutineScope = DEFAULT_SCOPE,
    clock: Clock = Clock.System,
    ttl: Duration,
    expectedQueries: Int,
    private val delegate: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
) : GetTrustAnchors<QUERY, TRUST_ANCHOR>, AutoCloseable {

    private val cached: AsyncCache<QUERY, NonEmptyList<TRUST_ANCHOR>?> =
        AsyncCache(scope, clock, ttl, expectedQueries) { trustSource ->
            delegate(trustSource)
        }

    override suspend fun invoke(query: QUERY): NonEmptyList<TRUST_ANCHOR>? = cached(query)

    override fun close() {
        cached.close()
        delegate.closeIfNeeded()
    }
}

@SensitiveApi
public class GetTrustAnchorsWithAlternative<in QUERY : Any, out TRUST_ANCHOR : Any>(
    private val delegate: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    private val alternative: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
) : GetTrustAnchors<QUERY, TRUST_ANCHOR>, AutoCloseable {

    override suspend fun invoke(query: QUERY): NonEmptyList<TRUST_ANCHOR>? =
        delegate.invoke(query) ?: alternative.invoke(query)

    override fun close() {
        delegate.closeIfNeeded()
        alternative.closeIfNeeded()
    }
}

public class GetTrustAnchorsTransformingQuery<in QUERY2 : Any, in QUERY : Any, out TRUST_ANCHOR : Any>(
    private val delegate: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    private val transformation: (QUERY2) -> QUERY,
) : GetTrustAnchors<QUERY2, TRUST_ANCHOR>, AutoCloseable {

    override suspend fun invoke(query: QUERY2): NonEmptyList<TRUST_ANCHOR>? =
        delegate.invoke(transformation(query))

    override fun close() {
        delegate.closeIfNeeded()
    }
}

private fun Any?.closeIfNeeded() {
    (this as? AutoCloseable)?.close()
}
