/*
 * Copyright (c) 2023 European Commission
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

import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DataLoader
import eu.europa.esig.dss.spi.client.http.NativeHTTPDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal class DSSLoader(
    private val loader: FileCacheDataLoader,
    private val cleanMemory: Boolean = true,
    private val cleanFileSystem: Boolean = false,
) {

    fun getTrustedListsCertificateByLOTLSource(
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
            block = ::refresh,
        )

    internal fun refresh(lotlSource: LOTLSource): TrustedListsCertificateSource {
        return TrustedListsCertificateSource().apply {
            runValidationJobFor(lotlSource)
        }
    }

    private fun TrustedListsCertificateSource.runValidationJobFor(lotlSource: LOTLSource) {
        TLValidationJob().apply {
            setListOfTrustedListSources(lotlSource)
            setOnlineDataLoader(loader)
            setTrustedListCertificateSource(this@runValidationJobFor)
            setSynchronizationStrategy(ExpirationAndSignatureCheckStrategy())
            setCacheCleaner(
                CacheCleaner().apply {
                    setCleanMemory(cleanMemory)
                    setCleanFileSystem(cleanFileSystem)
                    setDSSFileLoader(loader)
                },
            )
        }.onlineRefresh()
    }

    companion object {
        operator fun invoke(
            cacheDirectory: Path?,
            fileCacheExpiration: Duration = 24.hours,
            cleanMemory: Boolean = true,
            cleanFileSystem: Boolean = false,
            httpLoader: DataLoader = NativeHTTPDataLoader(),
        ): DSSLoader {
            val loader = FileCacheDataLoader(httpLoader)
                .apply {
                    setCacheExpirationTime(fileCacheExpiration.inWholeMilliseconds)
                    cacheDirectory?.let { setFileCacheDirectory(it.toFile()) }
                }
            return DSSLoader(loader)
        }
    }
}
