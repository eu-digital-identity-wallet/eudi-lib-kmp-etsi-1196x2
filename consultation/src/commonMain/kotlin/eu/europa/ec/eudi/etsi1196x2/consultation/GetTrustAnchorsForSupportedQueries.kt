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
 * A composite source for retrieving trust anchors, specialized for handling a predefined set of supported queries.
 *
 * Unlike a simple [GetTrustAnchors] that may attempt to resolve any given query (possibly through a sequential
 * search of multiple backends), this component maintains an explicit [mapping][sources] between sets of supported queries
 * and their respective providers. This allows for:
 * - **Efficient Routing**: Invocations are directly routed to the appropriate provider based on the query.
 * - **Explicit Capability**: The component knows exactly which queries it can satisfy, returning a clear
 *   indication when a query is not supported.
 * - **Strict Invariants**: It ensures that each query is handled by at most one [GetTrustAnchors], preventing
 *   ambiguous or overlapping definitions.
 *
 * When [combining][plus] multiple instances or [transforming][transform] their queries, these invariants are strictly enforced:
 * - **Exclusivity**: Combining two instances via the `plus` operator is only allowed if their supported
 *   query sets are disjoint.
 * - **Uniqueness**: Transforming queries must maintain a one-to-one mapping; if a transformation causes
 *   previously distinct queries to collide or overlap across providers, the operation will fail.
 *
 * Note: This class owns the lifecycle of its underlying sources.
 * When this instance is closed, all internal sources that implement [AutoCloseable] will also be closed.
 *
 * @param QUERY the type of the query
 * @param TRUST_ANCHOR the type of the trust anchors returned by the source
 */
public class GetTrustAnchorsForSupportedQueries<QUERY : Any, out TRUST_ANCHOR : Any> internal constructor(
    private val sources: Map<Set<QUERY>, GetTrustAnchors<QUERY, TRUST_ANCHOR>>,
) : AutoCloseable {

    public constructor(
        supportedQueries: Set<QUERY>,
        getTrustAnchors: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    ) : this(mapOf(supportedQueries to getTrustAnchors))

    /**
     * Executes a query to retrieve trust anchors.
     *
     * The execution flow is as follows:
     * 1. Check if the [query] is among the supported queries.
     * 2. If supported, route the query to the specific provider associated with it.
     * 3. Return the result from the provider, or a misconfiguration error if the provider returns no anchors.
     *
     * @param query the query to execute
     * @return the outcome of the query
     */
    public suspend operator fun invoke(query: QUERY): Outcome<TRUST_ANCHOR> =
        findSource(query)
            ?.let { getTrustAnchors ->
                getTrustAnchors(query)
                    ?.let { Outcome.Found(it) }
                    ?: Outcome.NotFound
            }
            ?: Outcome.QueryNotSupported

    private fun findSource(query: QUERY): GetTrustAnchors<QUERY, TRUST_ANCHOR>? =
        sources.entries
            .find { (supportedQueries, _) -> query in supportedQueries }
            ?.value

    /**
     * Transforms the query type of this source while preserving internal routing logic.
     *
     * This operation performs a bidirectional mapping of queries and contra-maps the underlying
     * providers. It is subject to the following constraints:
     * - **Injective Mapping**: Each original query must map to a unique new query within its provider.
     * - **Global Disjointness**: The resulting sets of supported queries across all providers must
     *   remain disjoint.
     *
     * @param contraMapF function to map a new query back to the original type for the providers
     * @param mapF function to map an original query to the new type
     * @return a new instance with transformed query types
     * @throws IllegalArgumentException if the transformation violates uniqueness or disjointness invariants
     * @param Q2 the new representation of the query
     */
    @Throws(IllegalArgumentException::class)
    public fun <Q2 : Any> transform(
        contraMapF: (Q2) -> QUERY,
        mapF: (QUERY) -> Q2,
    ): GetTrustAnchorsForSupportedQueries<Q2, TRUST_ANCHOR> {
        val newSourcesList = sources.entries.map { (queries, source) ->
            val mappedQueries = queries.map(mapF).toSet()
            require(mappedQueries.size == queries.size) {
                "Invalid transformation: current queries = ${queries.size}, mapped queries = ${mappedQueries.size}"
            }
            mappedQueries to source.contraMap(contraMapF)
        }
        val allMappedQueries = newSourcesList.flatMap { it.first }
        require(allMappedQueries.size == allMappedQueries.toSet().size) {
            "Invalid transformation: result has overlapping queries"
        }
        return GetTrustAnchorsForSupportedQueries(newSourcesList.toMap())
    }

    /**
     * Combines this source with an additional query set and [GetTrustAnchors].
     *
     * @param other a pair consisting of a set of supported queries and the corresponding [GetTrustAnchors]
     * @return a combined source
     * @throws IllegalArgumentException if the new queries overlap with existing ones
     */
    @Throws(IllegalArgumentException::class)
    public infix fun plus(
        other: Pair<Set<QUERY>, GetTrustAnchors<@UnsafeVariance QUERY, @UnsafeVariance TRUST_ANCHOR>>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> =
        this + GetTrustAnchorsForSupportedQueries(other.first, other.second)

    /**
     * Combines two sources into one.
     *
     * This operation merges the routing tables of both sources. It requires that the sets of
     * supported queries in both sources are completely disjoint to ensure unambiguous routing.
     *
     * @param other the other source to combine with
     * @return a combined source
     * @throws IllegalArgumentException if there are overlapping queries between the two sources
     */
    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: GetTrustAnchorsForSupportedQueries<QUERY, @UnsafeVariance TRUST_ANCHOR>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> {
        val common = this.supportedQueries.intersect(other.supportedQueries)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return GetTrustAnchorsForSupportedQueries(this.sources + other.sources)
    }

    private val supportedQueries: Set<QUERY> by lazy { sources.keys.flatten().toSet() }

    override fun close() {
        for (source in sources.values) {
            (source as? AutoCloseable)?.close()
        }
    }

    /**
     * Represents the result of a trust anchor retrieval operation.
     *
     * @param TRUST_ANCHOR the type of the trust anchors returned by the source
     */
    public sealed interface Outcome<out TRUST_ANCHOR> {

        /**
         * The source returned trust anchors
         */
        public data class Found<out TRUST_ANCHOR>(val trustAnchors: NonEmptyList<TRUST_ANCHOR>) : Outcome<TRUST_ANCHOR>

        /**
         * A query that is supported but didn't return any trust anchors.
         */
        public data object NotFound : Outcome<Nothing>

        /**
         * The source did not support the query
         */
        public data object QueryNotSupported : Outcome<Nothing>
    }

    public companion object {

        public fun <Q1 : Any, TA : Any, Q2 : Any> transform(
            getTrustAnchors: GetTrustAnchors<Q1, TA>,
            transformation: Map<Q2, Q1>,
        ): GetTrustAnchorsForSupportedQueries<Q2, TA> {
            val doubleQueries = transformation.values.groupBy { it }.filterValues { it.size > 1 }.keys
            require(doubleQueries.isEmpty()) { "Queries must be unique: $doubleQueries" }

            return GetTrustAnchorsForSupportedQueries(
                supportedQueries = transformation.keys,
                getTrustAnchors = getTrustAnchors.contraMap { checkNotNull(transformation[it]) },
            )
        }
    }
}
