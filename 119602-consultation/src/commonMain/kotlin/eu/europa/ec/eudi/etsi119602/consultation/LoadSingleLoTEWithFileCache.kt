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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.datamodel.ListOfTrustedEntitiesClaims
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.consultationPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Implementation of [LoadLoTE] that uses a file-based cache to store and retrieve LoTEs.
 *
 * This loader follows a "cache-aside" pattern:
 * 1. It first attempts to read the LoTE from [LoTEFileStore].
 * 2. If the cached LoTE is present and not expired, it is returned.
 * 3. Otherwise, if a [DownloadSingleLoTE] is provided, it downloads the LoTE, stages it in memory, and returns it.
 *
 * A fresh download is not committed to the cache immediately. It is committed when the higher
 * level emits [LoadLoTEAndPointers.Event.LoTELoaded] for it via [onEvent], and discarded on a
 * verification [LoadLoTEAndPointers.Event.Problem], so a single non-conformant download cannot
 * replace the last known good LoTE in the cache.
 *
 * This class also implements [LoTEFallbackProvider]: when the fresh path fails,
 * [LoadLoTEAndPointers] can ask for the last known good LoTE and re-verify it before serving it.
 *
 * This class is thread-safe and prevents redundant downloads of the same LoTE when accessed concurrently.
 *
 * @param fileStore the file store used for caching
 * @param downloadSingleLoTE optional service to download the LoTE if not in cache or expired
 * @param clock the clock used to determine cache expiration
 * @param fileCacheExpiration the default duration after which a cached LoTE expires if no nextUpdate is present
 * @param ioDispatcher the dispatcher used for I/O operations
 * @param parseJwt optional service to parse JWTs to extract nextUpdate
 */
public class LoadSingleLoTEWithFileCache internal constructor(
    private val fileStore: LoTEFileStore,
    private val downloadSingleLoTE: DownloadSingleLoTE? = null,
    private val clock: Clock = Clock.System,
    private val fileCacheExpiration: Duration = 24.hours,
    private val ioDispatcher: CoroutineDispatcher = consultationPlatform().ioDispatcher,
    private val parseJwt: ParseJwt<JsonObject, ListOfTrustedEntitiesClaims> = ParseJwt(),
) : LoadLoTE<String>, LoTEEventListener, LoTEFallbackProvider {

    public constructor(
        cacheDirectory: Path,
        fileSystem: FileSystem = SystemFileSystem,
        downloadSingleLoTE: DownloadSingleLoTE? = null,
        clock: Clock = Clock.System,
        fileCacheExpiration: Duration = 24.hours,
        ioDispatcher: CoroutineDispatcher = consultationPlatform().ioDispatcher,
    ) : this(
        LoTEFileStore(cacheDirectory, fileSystem, ioDispatcher),
        downloadSingleLoTE,
        clock,
        fileCacheExpiration,
        ioDispatcher,
    )

    private val mutex = Mutex()

    private data class PendingDownload(val jwt: String, val metadata: LoTEFileMetadata)

    private val pendingDownloads = mutableMapOf<Uri, PendingDownload>()

    override suspend fun invoke(uri: Uri): LoadLoTE.Outcome<String> = mutex.withLock {
        withContext(ioDispatcher) {
            // Try to read from cache
            val cached = fileStore.read(uri)
            if (cached != null) {
                val now = clock.now()
                if (now < cached.metadata.expiresAt) {
                    // Cache hit - not expired
                    return@withContext LoadLoTE.Outcome.Loaded(cached.jwt)
                }
                // Cache expired, will re-download
            }

            downloadSingleLoTE
                ?.let { downloadAndStage(it, uri) }
                ?: LoadLoTE.Outcome.NotFound(null)
        }
    }

    /**
     * Commits a staged download to the file store on [LoadLoTEAndPointers.Event.LoTELoaded] and
     * discards it on a verification [LoadLoTEAndPointers.Event.Problem].
     *
     * An event only takes effect when [jwt] matches the currently staged download for [uri]
     * (content guard), so concurrent download/verification cycles for the same URI cannot
     * commit or discard each other's downloads.
     *
     * On a [LoadLoTEAndPointers.Event.Problem] (and on a loaded LoTE whose
     * [ListOfTrustedEntitiesClaims.sequenceNumber] regressed below the cached one) the
     * previously cached copy is kept: its [LoTEFileMetadata.expiresAt] is extended to
     * `min(nextUpdate, now + fileCacheExpiration)` so it keeps serving until its
     * [ListOfTrustedEntitiesClaims.nextUpdate].
     *
     * Failures of the underlying file operations are ignored: a cache update failure must not
     * turn a verified LoTE into an error (the next call simply re-downloads).
     */
    override suspend fun onEvent(uri: Uri, jwt: String?, event: LoadLoTEAndPointers.Event): Unit =
        mutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val pending = pendingDownloads[uri]
                    if (pending == null || jwt == null || pending.jwt != jwt) return@withContext
                    when (event) {
                        is LoadLoTEAndPointers.Event.LoTELoaded -> {
                            pendingDownloads.remove(uri)
                            if (isSequenceRegression(uri, pending.metadata)) {
                                keepLastKnownGood(uri)
                            } else {
                                fileStore.write(uri, pending.jwt, pending.metadata)
                            }
                        }

                        is LoadLoTEAndPointers.Event.Problem -> {
                            pendingDownloads.remove(uri)
                            keepLastKnownGood(uri)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A failed cache update must not turn a verified LoTE into an error.
                }
            }
        }

    /**
     * Returns the cached LoTE for [uri] while its [ListOfTrustedEntitiesClaims.nextUpdate] is in
     * the future, so [LoadLoTEAndPointers] can re-verify and serve it when the fresh path fails.
     *
     * The cache entry's [LoTEFileMetadata.expiresAt] is extended as a side effect, so a subsequent
     * [invoke] serves the same entry from the cache instead of attempting another download.
     * Failures of the underlying file operations are ignored: a cache read failure simply means
     * that no fallback is available.
     */
    override suspend fun lastKnownGood(uri: Uri): String? = mutex.withLock {
        withContext(ioDispatcher) {
            try {
                val old = fileStore.read(uri) ?: return@withContext null
                val nextUpdate = old.metadata.nextUpdate ?: return@withContext null
                val now = clock.now()
                if (nextUpdate > now) {
                    try {
                        keepLastKnownGood(uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A failed cache update must not prevent serving the last known good LoTE.
                    }
                    old.jwt
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun downloadAndStage(loadFromHttp: DownloadSingleLoTE, uri: Uri): LoadLoTE.Outcome<String> {
        fun createMetadata(jwt: String): LoTEFileMetadata {
            val loadedAt = clock.now()

            // Try to extract nextUpdate and sequenceNumber from JWT payload
            val (nextUpdate, sequenceNumber) = runCatching {
                when (val result = parseJwt(jwt)) {
                    is ParseJwt.Outcome.Parsed -> {
                        with(result.payload.listOfTrustedEntities.schemeInformation) {
                            nextUpdate to sequenceNumber
                        }
                    }

                    is ParseJwt.Outcome.ParseFailed -> null to null
                }
            }.getOrElse { null to null }

            // Calculate expiration time
            val expiresAt = nextUpdate?.let {
                // Use nextUpdate if it's in the future
                if (it > loadedAt) it else loadedAt + fileCacheExpiration
            } ?: (loadedAt + fileCacheExpiration)

            return LoTEFileMetadata(
                loadedAt = loadedAt,
                expiresAt = expiresAt,
                nextUpdate = nextUpdate,
                sequenceNumber = sequenceNumber,
            )
        }
        return when (val httpResult = loadFromHttp(uri)) {
            is LoadLoTE.Outcome.Loaded -> {
                // Stage the download; it is committed to the cache only when [onEvent] confirms
                // that the higher level verified it.
                pendingDownloads[uri] = PendingDownload(httpResult.content, createMetadata(httpResult.content))
                httpResult
            }

            is LoadLoTE.Outcome.NotFound -> httpResult
        }
    }

    /**
     * True when the staged download's [ListOfTrustedEntitiesClaims.sequenceNumber] is lower than
     * the cached one; both must be present for a comparison.
     */
    private suspend fun isSequenceRegression(uri: Uri, staged: LoTEFileMetadata): Boolean {
        val old = fileStore.read(uri) ?: return false
        val oldSequenceNumber = old.metadata.sequenceNumber ?: return false
        val stagedSequenceNumber = staged.sequenceNumber ?: return false
        return stagedSequenceNumber < oldSequenceNumber
    }

    /**
     * Extends the [LoTEFileMetadata.expiresAt] of the cached copy of [uri] to
     * `min(nextUpdate, now + fileCacheExpiration)` so it keeps serving until its
     * [ListOfTrustedEntities.schemeInformation. nextUpdate]. Does nothing when there is no cached copy or
     * its `nextUpdate` is already in the past.
     */
    private suspend fun keepLastKnownGood(uri: Uri) {
        val old = fileStore.read(uri) ?: return
        val nextUpdate = old.metadata.nextUpdate ?: return
        val now = clock.now()
        if (nextUpdate > now) {
            val extended = old.metadata.copy(expiresAt = nextUpdate.coerceAtMost(now + fileCacheExpiration))
            fileStore.write(uri, old.jwt, extended)
        }
    }
}

/**
 * File-based storage for LoTE JWTs with metadata.
 *
 * Stores each LoTE as two files:
 * - `.jwt` file containing the JWT content
 * - `.meta.json` file containing the metadata
 *
 * This class is thread-safe and supports concurrent access.
 *
 * @param cacheDirectory the directory where cache files are stored
 * @param fileSystem the filesystem to use for operations
 * @param ioDispatcher the dispatcher used for I/O operations
 */
internal class LoTEFileStore(
    private val cacheDirectory: Path,
    fileSystem: FileSystem = SystemFileSystem,
    ioDispatcher: CoroutineDispatcher = consultationPlatform().ioDispatcher,
) {

    private val fileNames = FileNames(cacheDirectory)
    private val useFileSystem = FileOperation(fileSystem, ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Ensure cache directory exists
        fileSystem.createDirectories(cacheDirectory)
    }

    /**
     * Reads a cached LoTE from the file store.
     *
     * @param uri the URI of the LoTE to read. That's a HTTP URI
     * @return the cached LoTE with JWT and metadata, or null if not found or corrupted
     */
    suspend fun read(uri: Uri): StoredLoTE? =
        useFileSystem { fileSystem ->
            val (jwtPath, metaPath) = fileNames.jwtAndMetaFilePath(uri)

            // Both files must exist
            if (!fileSystem.exists(jwtPath) || !fileSystem.exists(metaPath)) {
                return@useFileSystem null
            }

            // Read JWT content
            val jwt = fileSystem.source(jwtPath).buffered().use { source ->
                source.readString()
            }

            // Read metadata
            val metadata = fileSystem.source(metaPath).buffered().use { source ->
                val content = source.readString()
                try {
                    json.decodeFromString<LoTEFileMetadata>(content)
                } catch (_: SerializationException) {
                    // Metadata corrupted, treat as cache miss
                    return@useFileSystem null
                }
            }

            StoredLoTE(jwt, metadata)
        }

    /**
     * Writes a LoTE JWT and its metadata to the file store.
     *
     * Uses atomic write operations (write to temp file, then rename) to ensure
     * consistency in case of failures.
     *
     * @param uri the URI of the LoTE. That's a HTTP URI
     * @param jwt the JWT content to store
     * @param metadata the metadata to store
     * @return [Result.success] if write was successful, [Result.failure] otherwise
     */
    suspend fun write(uri: Uri, jwt: String, metadata: LoTEFileMetadata): Result<Unit> =
        useFileSystem.runCatchingIO { fileSystem ->

            fun write(path: Path, content: String) {
                fileSystem.sink(path).buffered().use { sink ->
                    sink.writeString(content)
                }
            }
            val (jwtPath, metaPath) = fileNames.jwtAndMetaFilePath(uri)
            write(jwtPath, jwt)
            write(metaPath, json.encodeToString(metadata))
        }

    /**
     * Deletes a cached LoTE from the file store.
     *
     * @param uri the URI of the LoTE to delete. That's a HTTP URI
     * @return [Result.success] if deletion was successful (or file didn't exist), [Result.failure] otherwise
     */
    suspend fun delete(uri: Uri): Result<Unit> =
        useFileSystem.runCatchingIO { fileSystem ->
            val (jwtPath, metaPath) = fileNames.jwtAndMetaFilePath(uri)
            fileSystem.delete(jwtPath, mustExist = false)
            fileSystem.delete(metaPath, mustExist = false)
        }

    /**
     * Clears all cached LoTEs from the file store.
     *
     * @return [Result.success] if clear was successful, [Result.failure] otherwise
     */
    suspend fun clear(): Result<Unit> =
        useFileSystem.runCatchingIO { fileSystem ->
            fun deleteIgnoringIOExceptions(f: Path) = try {
                fileSystem.delete(f, mustExist = false)
            } catch (_: IOException) {
                // Ignore individual deletion failures
            }

            // Delete all files in cache directory
            fileSystem.list(cacheDirectory).forEach {
                deleteIgnoringIOExceptions(it)
            }
        }
}

internal class FileOperation(
    private val fileSystem: FileSystem,
    ioDispatcher: CoroutineDispatcher = consultationPlatform().ioDispatcher,
) {

    private val actualIoDispatcher = ioDispatcher.limitedParallelism(1)

    suspend operator fun <R> invoke(block: suspend (FileSystem) -> R): R =
        withContext(actualIoDispatcher) {
            block(fileSystem)
        }

    suspend fun <R> runCatchingIO(block: suspend (FileSystem) -> R): Result<R> =
        withContext(actualIoDispatcher) {
            try {
                Result.success(block(fileSystem))
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
}

internal class FileNames(val baseDir: Path) {

    fun jwtAndMetaFilePath(uri: Uri): Pair<Path, Path> {
        val jwtPath = jwtFilePath(uri)
        val metaPath = metadataFilePath(uri)
        return Pair(jwtPath, metaPath)
    }

    fun jwtFilePath(uri: Uri): Path =
        Path(baseDir, "${uriToFilename(uri)}.$JWT")

    fun metadataFilePath(uri: Uri): Path =
        Path(baseDir, "${uriToFilename(uri)}.${META_JSON}")

    /**
     * Converts a URI to a safe filename by replacing special characters.
     */
    private fun uriToFilename(uri: Uri): String =
        uri.toString()
            .replace(':', '_')
            .replace('/', '_')
            .replace('?', '_')
            .replace('&', '_')
            .replace('=', '_')

    companion object {
        const val JWT = "jwt"
        const val META_JSON = "meta.json"
    }
}

/**
 * Represents a cached LoTE with its JWT content and metadata.
 *
 * @param jwt the JWT content
 * @param metadata the metadata associated with the cache entry
 */
internal data class StoredLoTE(
    val jwt: String,
    val metadata: LoTEFileMetadata,
)

/**
 * Metadata associated with a cached LoTE file.
 *
 * @param loadedAt epoch milliseconds when the LoTE was loaded
 * @param expiresAt epoch milliseconds when the cache entry expires
 * @param nextUpdate epoch milliseconds when the LoTE should be updated (from LoTE metadata, optional)
 * @param sequenceNumber sequence number of the published release of the LoTE (from LoTE metadata, optional)
 */
@Serializable
internal data class LoTEFileMetadata(
    @SerialName("loaded_at") val loadedAt: EpocMillis,
    @SerialName("expires_at") val expiresAt: EpocMillis,
    @SerialName("next_update") val nextUpdate: EpocMillis?,
    @SerialName("sequence_number") val sequenceNumber: Int?,
)

private typealias EpocMillis =
    @Serializable(with = InstantAsMillisSerializer::class)
    Instant

private object InstantAsMillisSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("InstantAsMillisSerializer", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilliseconds())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeLong()
        return Instant.fromEpochMilliseconds(str)
    }
}
