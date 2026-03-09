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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DisposableContainerTest {

    /**
     * Test helper to track disposable behavior.
     */
    private class TestDisposable(
        val name: String,
        private val disposeException: Throwable? = null,
        private val disposeAction: () -> Unit = {},
    ) : Disposable {
        var disposed = false
            private set
        var disposeCount = 0
            private set

        override fun dispose() {
            disposeCount++
            disposeException?.let { throw it }
            disposeAction()
            disposed = true
        }
    }

    @Test
    fun addRegistersDisposable() {
        val container = DisposableContainer()
        val disposable = TestDisposable("test")

        container.add(disposable)

        assertFalse(disposable.disposed)
        container.dispose()
        assertTrue(disposable.disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun disposeCalledOnceWhenContainerDisposed() {
        val container = DisposableContainer()
        val disposables = List(3) { TestDisposable("item-$it") }

        disposables.forEach { container.add(it) }
        container.dispose()

        disposables.forEach {
            assertTrue(it.disposed)
            assertEquals(1, it.disposeCount)
        }
    }

    @Test
    fun disposablesDisposedInReverseOrder() {
        val container = DisposableContainer()
        val disposalOrder = mutableListOf<String>()

        val d1 = TestDisposable("first") { disposalOrder.add("first") }
        val d2 = TestDisposable("second") { disposalOrder.add("second") }
        val d3 = TestDisposable("third") { disposalOrder.add("third") }

        container.add(d1)
        container.add(d2)
        container.add(d3)
        container.dispose()

        assertEquals(listOf("third", "second", "first"), disposalOrder)
    }

    @Test
    fun exceptionInDisposeIsPropagated() {
        val container = DisposableContainer()
        val exception = RuntimeException("dispose failed")
        val disposable = TestDisposable("test", disposeException = exception)

        container.add(disposable)

        val thrown = assertFailsWith<RuntimeException> { container.dispose() }
        assertEquals(exception, thrown)
        // Disposable should NOT be marked as disposed when exception occurs
        assertFalse(disposable.disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun subsequentExceptionsAreSuppressed() {
        val container = DisposableContainer()
        val exception1 = RuntimeException("first failure")
        val exception2 = RuntimeException("second failure")

        val d1 = TestDisposable("first", disposeException = exception1)
        val d2 = TestDisposable("second", disposeException = exception2)

        container.add(d1)
        container.add(d2)

        val thrown = assertFailsWith<RuntimeException> { container.dispose() }
        // Disposables are disposed in reverse order, so d2 (second) is disposed first
        assertEquals("second failure", thrown.message)
        assertEquals(1, thrown.suppressed.size)
        assertEquals("first failure", thrown.suppressed[0].message)
    }

    @Test
    fun disposalContinuesAfterException() {
        val container = DisposableContainer()
        val disposalOrder = mutableListOf<String>()

        val d1 = TestDisposable("first") {
            disposalOrder.add("first-cleanup")
        }
        // d2 throws an exception BEFORE running disposeAction, so it won't be added to disposalOrder
        val d2 = TestDisposable("second", RuntimeException("fail")) { disposalOrder.add("second-cleanup") }
        val d3 = TestDisposable("third") { disposalOrder.add("third-cleanup") }

        container.add(d1)
        container.add(d2)
        container.add(d3)

        val thrown = assertFailsWith<RuntimeException> { container.dispose() }
        assertEquals("fail", thrown.message)

        // All disposables should have been attempted
        assertEquals(1, d1.disposeCount)
        assertEquals(1, d2.disposeCount)
        assertEquals(1, d3.disposeCount)

        // d2 failed, so it should NOT be marked as disposed
        assertFalse(d2.disposed)

        // d1 and d3 succeeded
        assertTrue(d1.disposed)
        assertTrue(d3.disposed)

        // Verify disposal order (reverse order, d1's action never runs due to exception)
        assertContentEquals(
            listOf("third-cleanup", "first-cleanup"),
            disposalOrder,
        )
    }

    // endregion

    // region Idempotency Tests

    @Test
    fun disposeIsIdempotent() {
        val container = DisposableContainer()
        val disposable = TestDisposable("test")

        container.add(disposable)
        container.dispose()
        container.dispose()
        container.dispose()

        assertTrue(disposable.disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun addAfterDisposeImmediatelyDisposes() {
        val container = DisposableContainer()
        container.dispose()

        val disposable = TestDisposable("test")
        container.add(disposable)

        assertTrue(disposable.disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun multipleAddAfterDisposeImmediatelyDisposes() {
        val container = DisposableContainer()
        container.dispose()

        val d1 = TestDisposable("first")
        val d2 = TestDisposable("second")

        container.add(d1)
        container.add(d2)

        assertTrue(d1.disposed)
        assertTrue(d2.disposed)
        assertEquals(1, d1.disposeCount)
        assertEquals(1, d2.disposeCount)
    }

    @Test
    fun concurrentAddOperationsAreThreadSafe() = runTest {
        val container = DisposableContainer()
        val count = 100
        val disposables = List(count) { TestDisposable("item-$it") }

        val jobs = disposables.map { d ->
            launch {
                container.add(d)
            }
        }
        jobs.joinAll()

        container.dispose()

        disposables.forEach {
            assertTrue(it.disposed, "Disposable ${it.name} should be disposed")
            assertEquals(1, it.disposeCount, "Disposable ${it.name} should be disposed once")
        }
    }

    @Test
    fun concurrentAddAndDisposeAreSafe() = runTest {
        val container = DisposableContainer()
        val count = 50
        val disposables = List(count) { TestDisposable("item-$it") }

        val addJobs = disposables.map { d ->
            launch {
                container.add(d)
            }
        }

        val disposeJob = launch {
            container.dispose()
        }

        addJobs.joinAll()
        disposeJob.join()

        // All disposables should be disposed (either by container or immediately on add)
        disposables.forEach {
            assertTrue(it.disposed, "Disposable ${it.name} should be disposed")
            assertEquals(1, it.disposeCount, "Disposable ${it.name} should be disposed once")
        }
    }

    @Test
    fun raceConditionBetweenAddAndDispose() = runTest {
        val container = DisposableContainer()

        repeat(10) { iteration ->
            val disposables = List(10) { TestDisposable("iter-$iteration-item-$it") }

            val addJobs = disposables.map { d ->
                launch {
                    container.add(d)
                }
            }

            val disposeJob = launch {
                container.dispose()
            }

            addJobs.joinAll()
            disposeJob.join()

            disposables.forEach { d ->
                assertTrue(d.disposed, "Disposable ${d.name} should be disposed in iteration $iteration")
                assertEquals(1, d.disposeCount, "Disposable ${d.name} should be disposed once in iteration $iteration")
            }
        }
    }

    @Test
    fun useResourcesDisposesOnSuccess() {
        var disposed = false
        val disposable = TestDisposable("test", disposeAction = { disposed = true })

        val result = useResources {
            disposable.bind()
            "success"
        }

        assertEquals("success", result)
        assertTrue(disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun useResourcesDisposesOnException() {
        var disposed = false
        val disposable = TestDisposable("test", disposeAction = { disposed = true })
        val exception = RuntimeException("block failed")

        val thrown = assertFailsWith<RuntimeException> {
            useResources {
                disposable.bind()
                throw exception
            }
        }

        assertEquals("block failed", thrown.message)
        assertTrue(disposed)
        assertEquals(1, disposable.disposeCount)
    }

    @Test
    fun useResourcesPropagatesException() {
        val disposable = TestDisposable("test")
        val exception = IllegalStateException("propagated")

        val thrown = assertFailsWith<IllegalStateException> {
            useResources {
                disposable.bind()
                throw exception
            }
        }

        assertEquals("propagated", thrown.message)
        assertTrue(disposable.disposed)
    }

    @Test
    fun useResourcesDisposesInReverseOrder() {
        val disposalOrder = mutableListOf<String>()

        val result = useResources {
            TestDisposable("first", disposeAction = { disposalOrder.add("first") }).bind()
            TestDisposable("second", disposeAction = { disposalOrder.add("second") }).bind()
            TestDisposable("third", disposeAction = { disposalOrder.add("third") }).bind()
            "result"
        }

        assertEquals("result", result)
        assertEquals(listOf("third", "second", "first"), disposalOrder)
    }

    @Test
    fun useResourcesHandlesExceptionInDisposal() {
        val exception = RuntimeException("disposal failed")

        val thrown = assertFailsWith<RuntimeException> {
            useResources {
                TestDisposable("failing", exception).bind()
            }
        }

        assertEquals("disposal failed", thrown.message)
        assertTrue(thrown.suppressed.isEmpty())
    }
}
