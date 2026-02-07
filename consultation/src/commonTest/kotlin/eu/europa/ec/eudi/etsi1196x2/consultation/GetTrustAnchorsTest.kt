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

class GetTrustAnchorsTest {

    @Test
    fun `or combinator returns first source result if present`() = runTest {
        val source1 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor1")) }
        val source2 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor2")) }
        val combined = source1 or source2

        val result = combined("query")
        assertEquals(listOf("anchor1"), result?.list)
    }

    @Test
    fun `or combinator returns second source result if first is null`() = runTest {
        val source1 = GetTrustAnchors<String, String> { _ -> null }
        val source2 = GetTrustAnchors<String, String> { _ -> NonEmptyList(listOf("anchor2")) }
        val combined = source1 or source2

        val result = combined("query")
        assertEquals(listOf("anchor2"), result?.list)
    }

    @Test
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
