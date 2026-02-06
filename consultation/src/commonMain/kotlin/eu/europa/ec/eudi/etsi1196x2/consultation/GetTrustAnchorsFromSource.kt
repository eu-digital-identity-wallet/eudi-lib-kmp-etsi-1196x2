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

import eu.europa.ec.eudi.etsi1196x2.consultation.AsyncCache.Entry
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsFromSource.Companion.DEFAULT_SCOPE
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration

public fun interface GetTrustAnchorsFromSource<in SRC : Any, out TRUST_ANCHOR : Any> {
    public suspend operator fun invoke(source: SRC): List<TRUST_ANCHOR>

    public companion object {
        /**
         * The default scope for [GetTrustAnchorsFromSource] instances.
         * [Dispatchers.Default] + [SupervisorJob]
         */
        public val DEFAULT_SCOPE: CoroutineScope get() = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

public fun <SRC : Any, TA : Any, SRC2 : Any> GetTrustAnchorsFromSource<SRC, TA>.contraMap(
    transformation: (SRC2) -> SRC,
): GetTrustAnchorsFromSource<SRC2, TA> = GetTrustAnchorsFromSource { source -> invoke(transformation(source)) }

/**
 * Creates a [GetTrustAnchorsFromSource] instance that caches invocations.
 *
 * @param coroutineScope the overall scope of the resulting [GetTrustAnchorsFromSource]. By default, adds [SupervisorJob]
 *       Defaults to [DEFAULT_SCOPE]
 * @param clock the clock used to retrieve the current time. Defaults to [Clock.System]
 * @param expectedSources the expected number of trust sources
 * @param ttl the time-to-live duration for caching the certificate source.
 * @receiver The [GetTrustAnchorsFromSource] instance to cache
 *
 * @return the [GetTrustAnchorsFromSource] instance that caches invocations
 */
public fun <SRC : Any, TRUST_ANCHOR : Any> GetTrustAnchorsFromSource<SRC, TRUST_ANCHOR>.cached(
    coroutineScope: CoroutineScope = DEFAULT_SCOPE,
    clock: Clock = Clock.System,
    ttl: Duration,
    expectedSources: Int,
): GetTrustAnchorsFromSource<SRC, TRUST_ANCHOR> =
    GetTrustAnchorsCachedSource(coroutineScope, clock, ttl, expectedSources, this)

internal class GetTrustAnchorsCachedSource<in SRC : Any, out TRUST_ANCHOR : Any>(
    scope: CoroutineScope,
    clock: Clock,
    ttl: Duration,
    expectedTrustSourceNo: Int,
    val original: GetTrustAnchorsFromSource<SRC, TRUST_ANCHOR>,
) : GetTrustAnchorsFromSource<SRC, TRUST_ANCHOR> {

    private val cached: AsyncCache<SRC, List<TRUST_ANCHOR>> =
        AsyncCache(scope, clock, ttl, expectedTrustSourceNo) { trustSource ->
            original(trustSource)
        }

    override suspend fun invoke(source: SRC): List<TRUST_ANCHOR> = cached(source)
}

internal class AsyncCache<A : Any, B>(
    coroutineScope: CoroutineScope,
    private val clock: Clock,
    private val ttl: Duration,
    private val maxCacheSize: Int,
    private val supplier: suspend (A) -> B,
) : suspend (A) -> B {

    private val scope = coroutineScope + SupervisorJob(coroutineScope.coroutineContext[Job.Key])

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
                val newDeferred = scope.async {
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
}
