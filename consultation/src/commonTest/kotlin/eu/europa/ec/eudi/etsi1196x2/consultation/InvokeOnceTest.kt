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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvokeOnceTest {

    private val invocationCount = atomic(0)

    @Test
    fun `should invoke source only once on single call`() = runTest {
        invocationCount.value = 0
        val source: suspend () -> String = {
            invocationCount.incrementAndGet()
            "result"
        }

        val invokeOnce = InvokeOnce(source)
        val result = invokeOnce()

        assertEquals("result", result)
        assertEquals(1, invocationCount.value)
    }

    @Test
    fun `should invoke source only once on multiple sequential calls`() = runTest {
        invocationCount.value = 0
        val source: suspend () -> String = {
            invocationCount.incrementAndGet()
            "result"
        }

        val invokeOnce = InvokeOnce(source)
        val result1 = invokeOnce()
        val result2 = invokeOnce()
        val result3 = invokeOnce()

        assertEquals("result", result1)
        assertEquals("result", result2)
        assertEquals("result", result3)
        assertEquals(1, invocationCount.value)
    }

    @Test
    fun `should invoke source only once on concurrent calls`() = runTest {
        invocationCount.value = 0
        val source: suspend () -> String = {
            delay(100) // Simulate some async work
            invocationCount.incrementAndGet()
            "result"
        }

        val invokeOnce = InvokeOnce(source)

        // Launch multiple concurrent invocations
        val results = (1..10).map {
            async { invokeOnce() }
        }.awaitAll()

        // All results should be the same
        assertEquals(10, results.size)
        results.forEach { assertEquals("result", it) }

        // Source should only be invoked once despite concurrent calls
        assertEquals(1, invocationCount.value)
    }

    @Test
    fun `should cache the result from first invocation`() = runTest {
        invocationCount.value = 0
        val source: suspend () -> Int = {
            delay(50)
            invocationCount.incrementAndGet()
        }

        val invokeOnce = InvokeOnce(source)

        val result1 = invokeOnce()
        val result2 = invokeOnce()
        val result3 = invokeOnce()

        assertEquals(1, result1)
        assertEquals(1, result2)
        assertEquals(1, result3)
        assertEquals(1, invocationCount.value)
    }

    @Test
    fun `should work with different types`() = runTest {
        data class TestData(val id: Int, val name: String)

        invocationCount.value = 0
        val source: suspend () -> TestData = {
            invocationCount.incrementAndGet()
            TestData(42, "test")
        }

        val invokeOnce = InvokeOnce(source)
        val result1 = invokeOnce()
        val result2 = invokeOnce()

        assertEquals(TestData(42, "test"), result1)
        assertEquals(TestData(42, "test"), result2)
        assertEquals(1, invocationCount.value)
    }

    @Test
    fun `should work with list types`() = runTest {
        invocationCount.value = 0
        val source: suspend () -> List<String> = {
            invocationCount.incrementAndGet()
            listOf("a", "b", "c")
        }

        val invokeOnce = InvokeOnce(source)
        val result1 = invokeOnce()
        val result2 = invokeOnce()

        assertEquals(listOf("a", "b", "c"), result1)
        assertEquals(listOf("a", "b", "c"), result2)
        assertEquals(1, invocationCount.value)
    }
}
