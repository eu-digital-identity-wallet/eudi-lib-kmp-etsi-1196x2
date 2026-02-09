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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Tests for [AsyncCache] verifying its concurrency and eviction logic.
 */
@ExperimentalCoroutinesApi
class AsyncCacheTest {

    private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
    }

    @Test
    fun failureInOneKeyDoesNotCancelScopeAndAffectOtherKeys() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val ttl = 1000.milliseconds
        val clock = TestClock(testScheduler)

        val cache = AsyncCache<String, String>(testDispatcher, clock, ttl, 10) { key ->
            if (key == "fail") {
                throw RuntimeException("Planned failure")
            }
            "value-$key"
        }

        // 1. Trigger a failing call
        try {
            cache("fail")
        } catch (_: RuntimeException) {
        }

        // 2. Try another call. If the scope was cancelled, this might fail or hang
        // depending on how the scope is used. In our case, scope.async will fail immediately if cancelled.
        val result = cache("success")
        assertEquals("value-success", result)
    }

    @Test
    fun failingTaskDoesNotEvictNewerValidEntry() = runTest {
        var supplierCalls = 0
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val ttl = 100.milliseconds
        val clock = TestClock(testScheduler)

        val cache = AsyncCache<String, String>(testDispatcher, clock, ttl, 10) { key ->
            supplierCalls++
            if (key == "fail" && supplierCalls == 1) {
                delay(200) // Longer than TTL
                throw RuntimeException("Planned failure")
            }
            "value"
        }

        // 1. Trigger a failing call
        val job1 = launch {
            try {
                cache("fail")
            } catch (_: Throwable) {
                // Ignore expected failure
            }
        }

        // Advance a bit to start job1
        advanceTimeBy(10)
        runCurrent()

        // 2. Wait for TTL to pass
        advanceTimeBy(150)
        runCurrent()

        // 3. Trigger a successful call for same key (since it's expired)
        // This will replace the "fail" entry in cache
        val value = cache("fail")
        assertEquals("value", value)
        assertEquals(2, supplierCalls)

        // At this point, the cache has the SUCCESSFUL entry.

        // 4. Advance time to let the FIRST (failing) call complete its delay and trigger handleFailure
        advanceTimeBy(40) // 160 + 40 = 200ms
        runCurrent()
        job1.join()

        // 5. Check if the SUCCESSFUL entry is still there or was wrongly evicted
        // At 200ms, Call 2's entry (created at 160ms) is still valid (TTL 100)
        cache("fail")
        assertEquals(2, supplierCalls, "Supplier should NOT have been called again - entry was wrongly evicted!")
    }
}
