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

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration

public class AsyncCache<A : Any, B>(
    cacheDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val ttl: Duration,
    private val maxCacheSize: Int,
    cleanupExpired: Boolean = true,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B, Disposable {

    private val cacheScope = CoroutineScope(SupervisorJob() + cacheDispatcher)

    private data class Entry<B>(val deferred: Deferred<B>, val createdAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<A, Entry<B>>()

    init {
        if (cleanupExpired && ttl.isPositive() && ttl != Duration.INFINITE) {
            launchCleanup()
        }
    }

    override suspend fun invoke(key: A): B {
        check(cacheScope.isActive) { "AsyncCache has been closed" }
        val now = clock.now().toEpochMilliseconds()
        val entry = mutex.withLock {
            val existing = cache[key]
            if (existing != null && (now - existing.createdAt) < ttl.inWholeMilliseconds) {
                cache.remove(key)
                cache[key] = existing
                existing
            } else {
                // Launch new computation
                val newDeferred = cacheScope.async {
                    supplier(key)
                }
                val newEntry = Entry(newDeferred, now)

                // Maintain LRU size
                if (cache.size >= maxCacheSize && !cache.containsKey(key)) {
                    val eldestKey = cache.keys.firstOrNull()
                    if (eldestKey != null) {
                        cache.remove(eldestKey)
                    }
                }
                cache[key] = newEntry
                newEntry
            }
        }
        return try {
            entry.deferred.await()
        } catch (e: Exception) {
            handleFailure(key, entry)
            throw e
        }
    }

    private suspend fun handleFailure(key: A, entry: Entry<B>) {
        mutex.withLock {
            if (cache[key] === entry) {
                cache.remove(key)
            }
        }
    }

    /**
     * Closes this cache and cancels all in-flight computations.
     *
     * This method:
     * - Cancels the internal coroutine scope, which cancels all ongoing computations
     * - Clears the cache entries
     * - Prevents any further invocations (will throw [IllegalStateException])
     *
     * **Important:** This method provides immediate cancellation semantics.
     * All in-flight computations are cancelled when the coroutine scope is cancelled.
     * If you need to wait for in-flight operations to complete gracefully,
     * you should track and await them before calling close().
     */
    override fun dispose() {
        if (cacheScope.isActive) {
            cacheScope.cancel()
            cache.clear()
        }
    }

    private fun launchCleanup() {
        check(ttl.isPositive() && ttl != Duration.INFINITE) { "TTL must be positive and not infinite" }
        cacheScope.launch {
            while (isActive) {
                delay(ttl)
                val now = clock.now().toEpochMilliseconds()
                mutex.withLock {
                    val iterator = cache.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next().value
                        if ((now - entry.createdAt) >= ttl.inWholeMilliseconds) {
                            iterator.remove()
                        }
                    }
                }
            }
        }
    }
}
