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
 * @param getTrustAnchors a way to retrieve from a source trust anchors for a given query
 * @param supportedQueries the queries supported by the source
 *
 * @param QUERY the type of the query
 * @param TRUST_ANCHOR the type of the trust anchors returned by the source
 */
public class GetTrustAnchorsForSupportedQueries<QUERY : Any, out TRUST_ANCHOR : Any>(
    private val getTrustAnchors: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    private val supportedQueries: Set<QUERY>,
) {

    /**
     * Queries the source for trust anchors matching the given [query].
     * @param query the query to execute
     * @return the outcome of the query
     */
    public suspend operator fun invoke(query: QUERY): Outcome<TRUST_ANCHOR> =
        when (query) {
            in supportedQueries -> {
                when (val trustAnchors = getTrustAnchors(query)) {
                    null -> Outcome.MisconfiguredSource
                    else -> Outcome.Found(trustAnchors)
                }
            }

            else -> Outcome.QueryNotSupported
        }

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
        val mappedQueries = supportedQueries.map(mapF).toSet()
        require(mappedQueries.size == supportedQueries.size) {
            "Invalid transformation: current queries = ${supportedQueries.size}, mapped queries = ${mappedQueries.size}"
        }

        return GetTrustAnchorsForSupportedQueries(
            getTrustAnchors.contraMap(contraMapF),
            mappedQueries,
        )
    }

    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: GetTrustAnchorsForSupportedQueries<@UnsafeVariance QUERY, @UnsafeVariance TRUST_ANCHOR>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> {
        val common = this.supportedQueries.intersect(other.supportedQueries)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return GetTrustAnchorsForSupportedQueries(
            supportedQueries = supportedQueries + other.supportedQueries,
            getTrustAnchors = this.getTrustAnchors or other.getTrustAnchors,
        )
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
