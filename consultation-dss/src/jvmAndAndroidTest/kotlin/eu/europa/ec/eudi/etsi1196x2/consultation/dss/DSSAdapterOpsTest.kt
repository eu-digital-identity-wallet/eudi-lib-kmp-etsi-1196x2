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

import eu.europa.ec.eudi.etsi1196x2.consultation.JvmSecurity
import eu.europa.esig.dss.spi.client.http.NativeHTTPDataLoader
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DSSAdapterOpsTest {

    /**
     * Test is not deterministic
     */
    @Ignore("Requires internet connection, it is not deterministic due to hard-coded, non-clock based expiration checks")
    @Test
    fun verifyBehavior() = runTest {
        val path: Path = Files.createTempDirectory("dss-cache")
        val expiration = 2.seconds
        val observableHttpLoader = ObservableHttpLoader(NativeHTTPDataLoader())
        val config = DssOptions.usingFileCacheDataLoader(
            cacheDirectory = path,
            fileCacheExpiration = expiration,
            cleanFileSystem = true,
            cleanMemory = true,
            httpLoader = observableHttpLoader,
        )

        val getTrustAnchorsFromLoTL = GetTrustAnchorsFromLoTL(
            dispatcher = Dispatchers.IO,
            trustAnchorCreator = JvmSecurity.DefaultTrustAnchorCreator,
            dssOptions = config,
        )

        val lotlSource = lotlSource(pidSvcType)

        // 1) First call
        // Expectations:
        // - FileCacheDataLoader should use HTTP loader
        // - Files downloaded
        getTrustAnchorsFromLoTL(lotlSource)
        val firstCallCount = observableHttpLoader.callCount
        assert(firstCallCount > 0) { "ObservableHttpLoader should be called on first call" }
        val firstCallFiles = path.listDirectoryEntries()
        assert(firstCallFiles.isNotEmpty()) { "Cache directory should not be empty after first call" }

        // 2) 2nd call (within expiration time from 1st)
        // Expectations:
        // - FileCacheDataLoader should use cached files
        getTrustAnchorsFromLoTL(lotlSource)
        val secondCallCount = observableHttpLoader.callCount
        assert(secondCallCount == firstCallCount) {
            "FileCacheDataLoader should retrieve the list from path (no new HTTP calls). " +
                "Expected $firstCallCount, got $secondCallCount"
        }
        val secondCallFiles = path.listDirectoryEntries()
        assert(secondCallFiles == firstCallFiles) { "Cache files should be the same on second call" }

        // 3) 3rd call, after the expiration
        // We advance the clock
        // Expectations:
        // - FileCacheDataLoader should use HTTP loader again
        // - Old files deleted
        // - New files downloaded
        Thread.sleep((expiration + 1.seconds).inWholeMilliseconds)

        // Capture last modified times of some files
        val lastModifiedBefore = firstCallFiles.associateWith { Files.getLastModifiedTime(it) }

        getTrustAnchorsFromLoTL(lotlSource)
        val thirdCallCount = observableHttpLoader.callCount
        assert(thirdCallCount > secondCallCount) {
            "ObservableHttpLoader should be invoked again after expiration. " +
                "Expected > $secondCallCount, got $thirdCallCount"
        }
        val thirdCallFiles = path.listDirectoryEntries()
        assert(thirdCallFiles.isNotEmpty()) { "Cache directory should not be empty after third call" }

        thirdCallFiles.forEach { file ->
            val before = lastModifiedBefore[file]
            if (before != null) {
                val after = Files.getLastModifiedTime(file)
                assert(after > before) { "File ${file.fileName} should have been updated/overwritten" }
            }
        }

        path.toFile().deleteRecursively()
    }

    private val pidSvcType = "http://uri.etsi.org/Svc/Svctype/Provider/PID"
    private fun lotlSource(@Suppress("SameParameterValue") serviceType: String): LOTLSource {
        val lotlSource = LOTLSource().apply {
            url = "https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml"
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(5, 6)
            trustServicePredicate =
                Predicate { tspServiceType ->
                    tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
                }
        }
        return lotlSource
    }
}
