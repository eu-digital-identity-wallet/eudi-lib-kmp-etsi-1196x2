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
 * A function that retrieves trust anchors for a given query.
 *
 * @param sources a way to retrieve from a source trust anchors for a given query set
 *
 * @param QUERY the type of the query
 * @param TRUST_ANCHOR the type of the trust anchors returned by the source
 */
public class GetTrustAnchorsForSupportedQueries<QUERY : Any, out TRUST_ANCHOR : Any> internal constructor(
    private val sources: Map<Set<QUERY>, GetTrustAnchors<QUERY, TRUST_ANCHOR>>,
) {

    public constructor(
        supportedQueries: Set<QUERY>,
        getTrustAnchors: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    ) : this(mapOf(supportedQueries to getTrustAnchors))

    /**
     * Queries the source for trust anchors matching the given [query].
     * @param query the query to execute
     * @return the outcome of the query
     */
    public suspend operator fun invoke(query: QUERY): Outcome<TRUST_ANCHOR> =
        findSource(query)
            ?.let { getTrustAnchors ->
                getTrustAnchors(query)
                    ?.let { Outcome.Found(it) }
                    ?: Outcome.MisconfiguredSource
            }
            ?: Outcome.QueryNotSupported

    private fun findSource(query: QUERY): GetTrustAnchors<QUERY, TRUST_ANCHOR>? =
        sources.entries
            .find { (supportedQueries, _) -> query in supportedQueries }
            ?.value

    /**
     * Change the representation of the queries.
     *
     * @param contraMapF the function to apply to the transformed queries
     * @param mapF the function to apply to the original queries
     * @return a new instance of [GetTrustAnchorsForSupportedQueries] with the transformed queries
     * @throws IllegalArgumentException if [Q2] is a type, that doesn't implement proper equality
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

    @Throws(IllegalArgumentException::class)
    public infix fun plus(
        other: Pair<Set<QUERY>, GetTrustAnchors<@UnsafeVariance QUERY, @UnsafeVariance TRUST_ANCHOR>>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> =
        this + GetTrustAnchorsForSupportedQueries(other.first, other.second)

    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: GetTrustAnchorsForSupportedQueries<QUERY, @UnsafeVariance TRUST_ANCHOR>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> {
        val common = this.supportedQueries.intersect(other.supportedQueries)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return GetTrustAnchorsForSupportedQueries(this.sources + other.sources)
    }

    private val supportedQueries: Set<QUERY> by lazy { sources.keys.flatten().toSet() }

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
         * The source did not support the query
         */
        public data object QueryNotSupported : Outcome<Nothing>

        /**
         * A query that is supported didn't return any trust anchors.
         * This indicates a misconfiguration of the source.
         */
        public data object MisconfiguredSource : Outcome<Nothing>
    }

    public companion object
}
