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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.AsyncCache
import eu.europa.ec.eudi.etsi1196x2.consultation.Disposable
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.spi.DSSUtils
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.DataLoader
import eu.europa.esig.dss.spi.exception.DSSDataLoaderMultipleException
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strategy for calculating cache filenames from URLs.
 */
public fun interface CacheFilenameStrategy {
    /**
     * Calculates the cache filename for the given URL.
     * @param url the URL to generate a cache filename for
     * @return the cache filename (without a directory path)
     */
    public fun calculateFilename(url: String): String

    public companion object {
        /**
         * DSS normalization-based strategy.
         * Uses [DSSUtils.getNormalizedString] to create human-readable filenames.
         */
        public val DSS: CacheFilenameStrategy = CacheFilenameStrategy { url ->
            DSSUtils.getNormalizedString(url)
                ?: throw IllegalArgumentException("URL cannot be null")
        }

        /**
         * SHA-256 digest-based strategy.
         * Creates fixed-length filenames using SHA-256 hash of the URL.
         */
        public val Sha256: CacheFilenameStrategy = CacheFilenameStrategy { url ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(StandardCharsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * A thread-safe [DataLoader] with dual-layer caching for concurrent LOTL/TL fetching.
 *
 * Provides:
 * - **In-memory cache** with TTL to deduplicate concurrent requests for the same URL
 * - **File system cache** with expiration for offline capability
 * - **Per-URL mutex** to serialize file writes and prevent cache corruption
 * - **Atomic file writes** (write to temp, then atomic move)
 *
 * Use this loader when multiple coroutines may concurrently fetch trust lists,
 * especially in high-concurrency scenarios where DSS's [eu.europa.esig.dss.service.http.commons.FileCacheDataLoader] may
 * experience race conditions.
 *
 * File cache expiration uses the system clock to ensure consistency with filesystem timestamps.
 *
 * @param httpLoader the underlying HTTP loader for fetching remote resources
 * @param fileCacheExpiration duration after which cached files are considered stale
 * @param cacheDirectory directory for file cache; defaults to system temp directory
 * @param cacheDispatcher coroutine dispatcher for cache operations
 * @param httpCacheTtl TTL for in-memory HTTP response cache
 * @param maxCacheSize maximum number of entries in the in-memory cache
 * @param filenameStrategy strategy for calculating cache filenames; defaults to [CacheFilenameStrategy.DSS]
 *
 * @see eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
 */
public class ConcurrentCacheDataLoader(
    private val httpLoader: DataLoader,
    private val fileCacheExpiration: Duration,
    cacheDirectory: Path? = null,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
    httpCacheTtl: Duration = 5.seconds,
    maxCacheSize: Int = 100,
    private val filenameStrategy: CacheFilenameStrategy = CacheFilenameStrategy.DSS,
    private val httpCache: AsyncCache<String, ByteArray> = AsyncCache(
        cacheDispatcher = cacheDispatcher,
        clock = Clock.System,
        ttl = httpCacheTtl,
        maxCacheSize = maxCacheSize,
    ) { url -> httpLoader.get(url) },
) : DataLoader, DSSCacheFileLoader, Disposable by httpCache {

    init {
        require(fileCacheExpiration.isPositive() && fileCacheExpiration != Duration.INFINITE) {
            "fileCacheExpiration must be positive and not infinite"
        }
    }

    private val fileCacheDirectory: Path =
        (cacheDirectory ?: Paths.get(System.getProperty("java.io.tmpdir")))
            .also { Files.createDirectories(it) }

    private val mutexes = ConcurrentHashMap<String, MutexHolder>()

    override fun get(url: String): ByteArray = get(url, refresh = false)

    override fun get(urlStrings: List<String>): DataLoader.DataAndUrl {
        if (urlStrings.isEmpty()) {
            throw DSSExternalResourceException("Cannot process the GET call. List of URLs is empty!")
        }
        val exceptions = mutableMapOf<String, Throwable>()
        for (url in urlStrings) {
            try {
                val bytes = get(url)
                if (bytes.isNotEmpty()) {
                    return DataLoader.DataAndUrl(url, bytes)
                }
            } catch (e: Exception) {
                exceptions[url] = e
            }
        }
        throw DSSDataLoaderMultipleException(exceptions)
    }

    override fun post(url: String, content: ByteArray): ByteArray = httpLoader.post(url, content)

    override fun setContentType(contentType: String) {
        httpLoader.setContentType(contentType)
    }

    public fun get(url: String, refresh: Boolean): ByteArray = runBlocking {
        val holder = mutexes.compute(url) { _, existing ->
            val current = existing ?: MutexHolder()
            current.refCount.incrementAndGet()
            current
        }
        try {
            checkNotNull(holder).mutex.withLock {
                val cacheFile = cacheFileFor(url)
                if (!refresh) {
                    readIfFresh(cacheFile)?.let { return@withLock it }
                }
                val bytes = httpCache.invoke(url)
                if (bytes.isEmpty()) {
                    throw DSSExternalResourceException("Cannot retrieve data from url [$url]. Empty content is obtained!")
                }
                writeAtomically(cacheFile, bytes)
                bytes
            }
        } finally {
            mutexes.computeIfPresent(url) { _, current ->
                if (current.refCount.decrementAndGet() == 0) null else current
            }
        }
    }

    override fun getDocument(url: String): DSSDocument = getDocument(url, refresh = false)

    override fun getDocument(url: String, refresh: Boolean): DSSDocument {
        val cacheFile = cacheFileFor(url)
        val bytes = get(url, refresh)
        if (bytes.isEmpty()) {
            throw DSSExternalResourceException("Cannot retrieve data from url [$url]. Empty content is obtained!")
        }
        return FileDocument(cacheFile.toFile())
    }

    override fun getDocumentFromCache(url: String): DSSDocument? =
        cacheFileFor(url)
            .takeIf { Files.exists(it) }
            ?.let { FileDocument(it.toFile()) }

    override fun remove(url: String): Boolean {
        val cacheFile = cacheFileFor(url)
        return try {
            Files.deleteIfExists(cacheFile)
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheFileFor(url: String): Path {
        val filename = filenameStrategy.calculateFilename(url)
        return fileCacheDirectory.resolve("cache-$filename")
    }

    private fun readIfFresh(file: Path): ByteArray? {
        if (!Files.exists(file)) return null

        val lastModified = Files.getLastModifiedTime(file).toMillis()
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastModified >= fileCacheExpiration.inWholeMilliseconds) {
            return null
        }
        return Files.readAllBytes(file)
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val tmpFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        try {
            Files.write(tmpFile, bytes)
            try {
                Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            throw e
        }
    }

    private class MutexHolder(
        val mutex: Mutex = Mutex(),
        val refCount: AtomicInteger = AtomicInteger(0),
    )
}
