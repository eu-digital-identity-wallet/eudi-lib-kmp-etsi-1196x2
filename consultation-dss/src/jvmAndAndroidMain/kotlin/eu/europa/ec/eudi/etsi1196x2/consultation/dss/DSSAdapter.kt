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

import eu.europa.ec.eudi.etsi1196x2.consultation.dss.DSSAdapter.Companion.DEFAULT_CLEAN_FILE_SYSTEM
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.DSSAdapter.Companion.DEFAULT_CLEAN_MEMORY
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.DSSAdapter.Companion.DefaultFileCacheExpiration
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.DSSAdapter.Companion.DefaultHttpLoader
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.DSSAdapter.Companion.DefaultSynchronizationStrategy
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.DataLoader
import eu.europa.esig.dss.spi.client.http.NativeHTTPDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import eu.europa.esig.dss.tsl.sync.SynchronizationStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Adapter for using the DSS library with the [GetTrustedListsCertificateByLOTLSource] functional interface.
 *
 * @param loader the [DSSCacheFileLoader] to use for fetching the trusted lists certificate source
 * @param cleanMemory whether to clean the memory cache.
 *        Defaults to [DEFAULT_CLEAN_MEMORY]
 * @param cleanFileSystem whether to clean the file system cache.
 *        Defaults to [DEFAULT_CLEAN_FILE_SYSTEM]
 * @param synchronizationStrategy the synchronization strategy to use for the validation job.
 *        Defaults to [DefaultSynchronizationStrategy]
 */
public data class DSSAdapter(
    val loader: DSSCacheFileLoader,
    val cleanMemory: Boolean = DEFAULT_CLEAN_MEMORY,
    val cleanFileSystem: Boolean = DEFAULT_CLEAN_FILE_SYSTEM,
    val synchronizationStrategy: SynchronizationStrategy = DefaultSynchronizationStrategy,
) {
    public companion object {
        /**
         * Default HTTP loader. Uses [NativeHTTPDataLoader]
         */
        public val DefaultHttpLoader: DataLoader get() = NativeHTTPDataLoader()

        /**
         * Default options for cleaning the memory. Defaults to true.
         */
        public const val DEFAULT_CLEAN_MEMORY: Boolean = true

        /**
         * Default options for cleaning the file system. Defaults to true.
         */
        public const val DEFAULT_CLEAN_FILE_SYSTEM: Boolean = true

        /**
         * Default expiration time for the file cache. Defaults to 24 hours.
         */
        public val DefaultFileCacheExpiration: Duration = 24.hours

        /**
         * Default synchronization strategy.
         *
         * - Accept expired trust lists: false
         * - Accept invalid trust lists: false
         * - Accept expired lists of trust lists: false
         * - Accept invalid lists of trust lists: false
         */
        public val DefaultSynchronizationStrategy: SynchronizationStrategy
            get() = ExpirationAndSignatureCheckStrategy().apply {
                setAcceptExpiredTrustedList(false)
                setAcceptInvalidTrustedList(false)
                setAcceptExpiredListOfTrustedLists(false)
                setAcceptInvalidListOfTrustedLists(false)
            }

        /**
         * Creates a [DSSAdapter] that uses [FileCacheDataLoader] with the following options:
         * - expiration time: [DefaultFileCacheExpiration]
         * - cache directory: `null` [FileCacheDataLoader] will peek a temporary directory
         * - clean memory: [DEFAULT_CLEAN_MEMORY]
         * - clean file system: [DEFAULT_CLEAN_FILE_SYSTEM]
         * - HTTP loader: [DefaultHttpLoader]
         *
         */
        public val Default: DSSAdapter = usingFileCacheDataLoader(
            fileCacheExpiration = DefaultFileCacheExpiration,
            cacheDirectory = null,
            cleanMemory = DEFAULT_CLEAN_MEMORY,
            cleanFileSystem = DEFAULT_CLEAN_FILE_SYSTEM,
            httpLoader = DefaultHttpLoader,
        )

        /**
         * Creates a [DSSAdapter] that uses [FileCacheDataLoader] with the given options.
         * @param fileCacheExpiration the expiration time for the file cache.
         *        Defaults to [DefaultFileCacheExpiration]
         * @param cacheDirectory the cache directory.
         *        Defaults to `null`, in this case [FileCacheDataLoader] will peek a temporary directory
         * @param cleanMemory whether to clean the memory cache.
         *        Defaults to [DEFAULT_CLEAN_MEMORY]
         * @param cleanFileSystem whether to clean the file system cache.
         *        Defaults to [DEFAULT_CLEAN_FILE_SYSTEM]
         * @param httpLoader the HTTP loader to use.
         *        Defaults to [DefaultHttpLoader]
         * @param synchronizationStrategy the synchronization strategy to use for the validation job.
         *        Defaults to [DefaultSynchronizationStrategy]
         */
        public fun usingFileCacheDataLoader(
            fileCacheExpiration: Duration = DefaultFileCacheExpiration,
            cacheDirectory: Path? = null,
            cleanMemory: Boolean = DEFAULT_CLEAN_MEMORY,
            cleanFileSystem: Boolean = DEFAULT_CLEAN_MEMORY,
            httpLoader: DataLoader = DefaultHttpLoader,
            synchronizationStrategy: SynchronizationStrategy = DefaultSynchronizationStrategy,
        ): DSSAdapter {
            val loader = FileCacheDataLoader(httpLoader)
                .apply {
                    setCacheExpirationTime(fileCacheExpiration.inWholeMilliseconds)
                    cacheDirectory?.let { setFileCacheDirectory(it.toFile()) }
                }
            return DSSAdapter(loader, cleanMemory, cleanFileSystem, synchronizationStrategy)
        }
    }
}

internal object DSSAdapterOps {

    fun DSSAdapter.getTrustedListsCertificateByLOTLSource(
        clock: Clock,
        coroutineScope: CoroutineScope,
        coroutineDispatcher: CoroutineDispatcher,
        ttl: Duration,
        expectedTrustSourceNo: Int,
    ): GetTrustedListsCertificateByLOTLSource =
        GetTrustedListsCertificateByLOTLSource.fromBlocking(
            coroutineScope = coroutineScope,
            coroutineDispatcher = coroutineDispatcher,
            expectedTrustSourceNo = expectedTrustSourceNo,
            ttl = ttl,
            clock = clock,
            block = { refresh(it) },
        )

    internal fun DSSAdapter.refresh(
        lotlSource: LOTLSource,
    ): TrustedListsCertificateSource {
        return TrustedListsCertificateSource().apply {
            runValidationJobFor(this@refresh, lotlSource)
        }
    }

    private fun TrustedListsCertificateSource.runValidationJobFor(
        dssAdapter: DSSAdapter,
        lotlSource: LOTLSource,
    ) {
        TLValidationJob().apply {
            setListOfTrustedListSources(lotlSource)
            setOnlineDataLoader(dssAdapter.loader)
            setTrustedListCertificateSource(this@runValidationJobFor)
            setSynchronizationStrategy(dssAdapter.synchronizationStrategy)
            setCacheCleaner(
                CacheCleaner().apply {
                    setCleanMemory(dssAdapter.cleanMemory)
                    setCleanFileSystem(dssAdapter.cleanFileSystem)
                    setDSSFileLoader(dssAdapter.loader)
                },
            )
        }.onlineRefresh()
    }
}
