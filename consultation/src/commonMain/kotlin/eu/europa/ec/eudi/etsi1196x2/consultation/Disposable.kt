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

/**
 * Represents a disposable resource that can be released.
 */
public interface Disposable {
    /**
     * Releases the resource.
     */
    public fun dispose()
}

/**
 * Represents a scope that can be used to manage disposable resources.
 */
public interface DisposableScope : Disposable {
    /**
     * Binds a disposable resource to this scope.
     */
    public fun <T : Disposable> T.bind(): T
}

/**
 * Executes the given [block] function and automatically disposes all resources created within its scope.
 *
 * Example usage:
 * ```
 * useResources {
 *    val resource1 = SomeDisposable().bind()
 *    val resource2 = SomeOtherDisposable().bind()
 *
 *    // use resources
 * }
 * ```
 *
 * @param block The block of code to execute within the disposable scope.
 */
public inline fun <T> useResources(block: DisposableScope.() -> T): T {
    val scope = DisposableContainer()
    return try {
        scope.block()
    } finally {
        scope.dispose()
    }
}

/**
 * A concrete implementation of [DisposableScope] that manages a collection of [Disposable] resources.
 * It ensures that all registered resources are disposed when [dispose] is called,
 * even if some of them throw exceptions during disposal.
 */
public open class DisposableContainer : DisposableScope {
    private val disposables = atomic<List<Disposable>>(emptyList())
    private val isDisposed = atomic(false)

    public fun add(disposable: Disposable) {
        while (true) {
            if (isDisposed.value) {
                disposable.dispose()
                return
            }

            val current = disposables.value
            val next = current + disposable

            if (disposables.compareAndSet(current, next)) {
                return
            }
        }
    }

    override fun dispose() {
        if (!isDisposed.compareAndSet(expect = false, update = true)) return

        val toDispose = disposables.getAndSet(emptyList())

        var primaryError: Throwable? = null
        val suppressed = mutableListOf<Throwable>()

        toDispose.asReversed().forEach {
            try {
                it.dispose()
            } catch (e: Exception) {
                if (primaryError == null) {
                    primaryError = e
                } else {
                    suppressed.add(e)
                }
            }
        }
        suppressed.forEach { primaryError?.addSuppressed(it) }
        primaryError?.let { throw it }
    }

    override fun <T : Disposable> T.bind(): T = apply { add(this) }
}
