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

internal class AsyncCache<A : Any, B>(
    coroutineScope: CoroutineScope,
    private val clock: Clock,
    private val ttl: Duration,
    private val maxCacheSize: Int,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B, AutoCloseable {

    private val cacheScope = CoroutineScope(SupervisorJob() + coroutineScope.coroutineContext.minusKey(Job))

    private data class Entry<B>(val deferred: Deferred<B>, val createdAt: Long)

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<A, Entry<B>>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<A, Entry<B>>) =
                size > maxCacheSize
        }

    override suspend fun invoke(key: A): B {
        val now = clock.now().toEpochMilliseconds()
        val entry = mutex.withLock {
            val existing = cache[key]
            if (existing != null && (now - existing.createdAt) < ttl.inWholeMilliseconds) {
                existing
            } else {
                // Launch new computation
                val newDeferred = cacheScope.async {
                    supplier(key)
                }
                Entry(newDeferred, now).also { cache[key] = it }
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

    override fun close() {
        cacheScope.cancel()
        cache.clear()
    }
}
