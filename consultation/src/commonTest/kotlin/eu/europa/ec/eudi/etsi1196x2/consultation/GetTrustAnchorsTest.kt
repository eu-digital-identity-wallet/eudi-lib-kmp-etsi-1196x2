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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class GetTrustAnchorsTest {

    private class MockAutoCloseableGetTrustAnchors(
        val result: NonEmptyList<String>? = null,
    ) : GetTrustAnchors<String, String>, AutoCloseable {
        var closed = false
        override suspend fun invoke(query: String): NonEmptyList<String>? = result
        override fun close() {
            closed = true
        }
    }

    @Test
    @SensitiveApi
    fun `or combinator closes both sources`() {
        val source1 = MockAutoCloseableGetTrustAnchors()
        val source2 = MockAutoCloseableGetTrustAnchors()
        val combined = source1 or source2

        (combined as AutoCloseable).close()

        assertTrue(source1.closed, "Source 1 should be closed")
        assertTrue(source2.closed, "Source 2 should be closed")
    }

    @Test
    fun `contraMap closes underlying source`() {
        val source = MockAutoCloseableGetTrustAnchors()
        val adapted = source.contraMap<String, String, Int> { it.toString() }

        (adapted as AutoCloseable).close()

        assertTrue(source.closed, "Underlying source should be closed")
    }

    @Test
    fun `cached closes underlying source`() = runTest {
        val source = MockAutoCloseableGetTrustAnchors()
        val cached = source.cached(ttl = Duration.INFINITE, expectedQueries = 1)

        cached.close()

        assertTrue(source.closed, "Underlying source should be closed")
    }

    @Test
    @SensitiveApi
    fun `or combinator returns first source result if present`() = runTest {
        val source1 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor1")) }
        val source2 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor2")) }
        val combined = source1 or source2

        val result = combined("query")
        assertEquals(listOf("anchor1"), result?.list)
    }

    @Test
    @SensitiveApi
    fun `or combinator returns second source result if first is null`() = runTest {
        val source1 = GetTrustAnchors<String, String> { _ -> null }
        val source2 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor2")) }
        val combined = source1 or source2

        val result = combined("query")
        assertEquals(listOf("anchor2"), result?.list)
    }

    @Test
    @SensitiveApi
    fun `or combinator returns null if both sources are null`() = runTest {
        val source1 = GetTrustAnchors<String, String> { _ -> null }
        val source2 = GetTrustAnchors<String, String> { _ -> null }
        val combined = source1 or source2

        val result = combined("query")
        assertNull(result)
    }

    @Test
    fun `contraMap correctly transforms query`() = runTest {
        val originalSource = GetTrustAnchors<Int, String> { query ->
            NonEmptyList(listOf("anchor-$query"))
        }
        val adaptedSource = originalSource.contraMap<Int, String, String> { it.toInt() }

        val result = adaptedSource("123")
        assertEquals(listOf("anchor-123"), result?.list)
    }
}
