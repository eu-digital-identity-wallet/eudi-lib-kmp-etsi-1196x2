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

public class GetTrustAnchorsForSupportedQueries<QUERY : Any, out TRUST_ANCHOR : Any>(
    private val getTrustAnchors: GetTrustAnchors<QUERY, TRUST_ANCHOR>,
    private val queries: Set<QUERY>,
) {

    @Throws(IllegalStateException::class)
    public suspend operator fun invoke(query: QUERY): Outcome<TRUST_ANCHOR> =
        when (query) {
            in queries -> {
                when (val trustAnchors = getTrustAnchors(query)) {
                    null -> Outcome.MisconfiguredSource
                    else -> Outcome.Found(trustAnchors)
                }
            }

            else -> Outcome.QueryNotSupported
        }

    @Throws(IllegalArgumentException::class)
    public fun <Q2 : Any> transform(
        contraMapF: (Q2) -> QUERY,
        mapF: (QUERY) -> Q2,
    ): GetTrustAnchorsForSupportedQueries<Q2, TRUST_ANCHOR> {
        val qs = queries.map(mapF).toSet()
        require(qs.size == queries.size) { "Queries must be unique: $qs" }
        return GetTrustAnchorsForSupportedQueries(
            getTrustAnchors.contraMap(contraMapF),
            qs,
        )
    }

    @Throws(IllegalArgumentException::class)
    public infix operator fun plus(
        other: GetTrustAnchorsForSupportedQueries<@UnsafeVariance QUERY, @UnsafeVariance TRUST_ANCHOR>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> {
        val common = this.queries.intersect(other.queries)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return GetTrustAnchorsForSupportedQueries(
            queries = queries + other.queries,
            getTrustAnchors = this.getTrustAnchors or other.getTrustAnchors,
        )
    }

    public sealed interface Outcome<out T> {
        public data class Found<out T>(val trustAnchors: NonEmptyList<T>) : Outcome<T>
        public data object QueryNotSupported : Outcome<Nothing>
        public data object MisconfiguredSource : Outcome<Nothing>
    }

    public companion object
}
