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

public fun interface GetTrustAnchorsFromSource<in QUERY : Any, out TRUST_ANCHOR : Any> {
    public suspend operator fun invoke(query: QUERY): List<TRUST_ANCHOR>?

    public companion object {
        /**
         * The default scope for [GetTrustAnchorsFromSource] instances.
         * [Dispatchers.Default] + [SupervisorJob]
         */
        public val DEFAULT_SCOPE: CoroutineScope get() = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

public infix fun <Q : Any, TA : Any> GetTrustAnchorsFromSource<Q, TA>.or(
    other: GetTrustAnchorsFromSource<Q, TA>,
): GetTrustAnchorsFromSource<Q, TA> =
    GetTrustAnchorsFromSource { source -> this.invoke(source) ?: other(source) }

public fun <Q : Any, TA : Any, Q2 : Any> GetTrustAnchorsFromSource<Q, TA>.contraMap(
    transformation: (Q2) -> Q,
): GetTrustAnchorsFromSource<Q2, TA> = GetTrustAnchorsFromSource { source -> invoke(transformation(source)) }

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
public fun <QUERY : Any, TRUST_ANCHOR : Any> GetTrustAnchorsFromSource<QUERY, TRUST_ANCHOR>.cached(
    coroutineScope: CoroutineScope = DEFAULT_SCOPE,
    clock: Clock = Clock.System,
    ttl: Duration,
    expectedSources: Int,
): GetTrustAnchorsFromSource<QUERY, TRUST_ANCHOR> =
    GetTrustAnchorsCachedSource(coroutineScope, clock, ttl, expectedSources, this)

internal class GetTrustAnchorsCachedSource<in QUERY : Any, out TRUST_ANCHOR : Any>(
    scope: CoroutineScope,
    clock: Clock,
    ttl: Duration,
    expectedTrustSourceNo: Int,
    val original: GetTrustAnchorsFromSource<QUERY, TRUST_ANCHOR>,
) : GetTrustAnchorsFromSource<QUERY, TRUST_ANCHOR> {

    private val cached: AsyncCache<QUERY, List<TRUST_ANCHOR>?> =
        AsyncCache(scope, clock, ttl, expectedTrustSourceNo) { trustSource ->
            original(trustSource)
        }

    override suspend fun invoke(query: QUERY): List<TRUST_ANCHOR>? = cached(query)
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

public class GetTrustAnchorsForSupportedQueries<QUERY : Any, out TRUST_ANCHOR : Any>(
    private val getTrustAnchorsFromSource: GetTrustAnchorsFromSource<QUERY, TRUST_ANCHOR>,
    private val queries: Set<QUERY>,
) {

    public suspend operator fun invoke(query: QUERY): List<TRUST_ANCHOR>? =
        if (query in queries) {
            val trustAnchors = getTrustAnchorsFromSource(query)
            checkNotNull(trustAnchors) { "No trust anchors found for supported $query" }
        } else {
            null
        }

    public fun <Q2 : Any> transform(
        contraMapF: (Q2) -> QUERY,
        mapF: (QUERY) -> Q2,
    ): GetTrustAnchorsForSupportedQueries<Q2, TRUST_ANCHOR> {
        return GetTrustAnchorsForSupportedQueries(
            getTrustAnchorsFromSource.contraMap(contraMapF),
            queries.map(mapF).toSet(),
        )
    }

    public infix operator fun plus(
        other: GetTrustAnchorsForSupportedQueries<@UnsafeVariance QUERY, @UnsafeVariance TRUST_ANCHOR>,
    ): GetTrustAnchorsForSupportedQueries<QUERY, TRUST_ANCHOR> {
        val common = this.queries.intersect(other.queries)
        require(common.isEmpty()) { "Sources have overlapping queries: $common" }
        return GetTrustAnchorsForSupportedQueries(
            queries = queries + other.queries,
            getTrustAnchorsFromSource = this.getTrustAnchorsFromSource or other.getTrustAnchorsFromSource,
        )
    }
}
