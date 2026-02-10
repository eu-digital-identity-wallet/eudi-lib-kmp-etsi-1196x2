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

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GetTrustAnchorsForSupportedQueriesTest {

    private fun mockSource(value: String): GetTrustAnchors<String, String> = GetTrustAnchors { _ ->
        NonEmptyList(listOf(value))
    }

    @Test
    fun testBasicInvoke() = runTest {
        val queries = setOf("q1", "q2")
        val source = mockSource("v1")
        val supportedQueries = GetTrustAnchorsForSupportedQueries(queries, source)

        val result1 = supportedQueries("q1")
        assertIs<GetTrustAnchorsForSupportedQueries.Outcome.Found<String>>(result1)
        assertEquals("v1", result1.trustAnchors.list.first())

        val result3 = supportedQueries("q3")
        assertEquals(GetTrustAnchorsForSupportedQueries.Outcome.QueryNotSupported, result3)
    }

    @Test
    fun testPlus() = runTest {
        val s1 = GetTrustAnchorsForSupportedQueries(setOf("q1"), mockSource("v1"))
        val s2 = GetTrustAnchorsForSupportedQueries(setOf("q2"), mockSource("v2"))
        val combined = s1 plus s2

        val r1 = combined("q1")
        assertIs<GetTrustAnchorsForSupportedQueries.Outcome.Found<String>>(r1)
        assertEquals("v1", r1.trustAnchors.list.first())

        val r2 = combined("q2")
        assertIs<GetTrustAnchorsForSupportedQueries.Outcome.Found<String>>(r2)
        assertEquals("v2", r2.trustAnchors.list.first())
    }

    @Test
    fun testPlusOverlapInvariant() {
        val s1 = GetTrustAnchorsForSupportedQueries(setOf("q1"), mockSource("v1"))
        val s2 = GetTrustAnchorsForSupportedQueries(setOf("q1"), mockSource("v2"))

        assertFailsWith<IllegalArgumentException> {
            s1 plus s2
        }
    }

    @Test
    fun testNotFound() = runTest {
        // A source that claims to support a query but returns null
        val source = GetTrustAnchors<String, String> { _ -> null }
        val supportedQueries = GetTrustAnchorsForSupportedQueries(setOf("q1"), source)

        val result = supportedQueries("q1")
        assertEquals(GetTrustAnchorsForSupportedQueries.Outcome.NotFound, result)
    }
}
