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

import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.esig.dss.spi.client.http.DataLoader
import eu.europa.esig.dss.spi.client.http.NativeHTTPDataLoader
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files.createTempDirectory
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

object EUDIRefDevEnv {

    //
    // Conventions applicable to EUDI Echo system
    //
    private const val PUB_EAA_SVC_TYPE = "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA"
    private const val PID_SVC_TYPE = "http://uri.etsi.org/Svc/Svctype/Provider/PID"
    private const val LOTL_URL = "https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml"
    private fun lotlSource(serviceType: String): LOTLSource {
        val lotlSource = LOTLSource().apply {
            url = LOTL_URL
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(6)
            trustServicePredicate = Predicate { tspServiceType ->
                tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
            }
            isPivotSupport = true
        }
        return lotlSource
    }

    fun dssOptions(httpLoader: DataLoader) =
        DssOptions.usingFileCacheDataLoader(
            fileCacheExpiration = 24.hours,
            cacheDirectory = createTempDirectory("lotl-cache"),
            httpLoader = httpLoader,
        )

    val supportedLists = SupportedLists(
        pidProviders = lotlSource(PID_SVC_TYPE),
        pubEaaProviders = lotlSource(PUB_EAA_SVC_TYPE),
    )

    val pkixChainValidator = ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = false })
}

private fun SupportedLists<LOTLSource>.asMap(): Map<VerificationContext, LOTLSource> = buildMap {
    pidProviders?.let { put(VerificationContext.PID, it) }
    pubEaaProviders?.let { put(VerificationContext.PubEAA, it) }
    qeaProviders?.let { put(VerificationContext.QEAA, it) }
}

class IsChainTrustedUsingLoTLTest {

    val isX5CTrusted = run {
        val httpLoader = NativeHTTPDataLoader()
        val dssOptions = EUDIRefDevEnv.dssOptions(httpLoader)
        val pkixValidator = EUDIRefDevEnv.pkixChainValidator
        val getTrustAnchors = GetTrustAnchorsFromLoTL(dssOptions)
        getTrustAnchors
            .validator(EUDIRefDevEnv.supportedLists.asMap(), pkixValidator)
            .contraMap(::certsFromX5C)
    }

    @Test
    fun verifyThatPidX5CIsTrustedForPIDContext() = runTest {
        val validation = isX5CTrusted(pidX5c, VerificationContext.PID)
        assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(validation)
    }

    @Test
    @Ignore("This is not passing because current LoTL contains the same certs for PID and PubEAA service types")
    fun verifyThatPidX5CIsNotTrustedForPubEAAContext() = runTest {
        assertIs<CertificationChainValidation.NotTrusted>(
            isX5CTrusted(pidX5c, VerificationContext.PubEAA),
        )
    }

    @Test
    fun verifyThatPidX5CFailsForAnUnsupportedContext() = runTest {
        assertNull(
            isX5CTrusted(pidX5c, VerificationContext.WalletUnitAttestation),
        )
    }
}

/**
 * The test stresses the cached version of GetTrustAnchorsFromLoTL
 *
 * Conditions:
 * - The first-level cache is empty (to force lookup to DSS File Cache)
 * - The second-level cache is empty (to force http loading)
 * - 400 concurrent verifications are requested
 *
 * Implementation options to handle this use-case:
 * - [ConcurrentCacheDataLoader]: Thread-safe loader with dual-layer caching (recommended)
 * - [ConstraintHttpLoader]: Wraps HTTP loader with AsyncCache to deduplicate concurrent requests
 *
 * Even without the first-level cache deduplication, the test should complete without
 * - DSS File Cache reporting corrupted files and
 * - Keeping the Http Calls to the absolute minimum (This is 1 LOTL + 16 LIST = 17 HTTP calls)
 */
class IsChainTrustedUsingLoTLParallelTest {

    private val clock = Clock.System
    private val pkixValidator = EUDIRefDevEnv.pkixChainValidator
    private val iterations = 200

    @Ignore("This test will fail. It just demonstrates the problem of FileCacheDataLoader")
    @Test
    fun stressTestDssFileCacheLoaderCommonCase() = runTest {
        val expectedHttpCalls = 17 * 2
        val httpLoader = ObservableHttpLoader(NativeHTTPDataLoader())
        val dssOptions = DssOptions.usingFileCacheDataLoader(
            httpLoader = httpLoader,
            fileCacheExpiration = 24.hours,
            cacheDirectory = createTempDirectory("lotl-cache-dss"),
        )
        useResources {
            val getTrustAnchors = GetTrustAnchorsFromLoTL(dssOptions)
                .cached(clock = clock, ttl = 10.seconds, expectedQueries = iterations).bind()
            doTest(httpLoader, getTrustAnchors, expectedHttpCalls)
        }
    }

    @Ignore("Flaky: DSS FileCacheDataLoader can corrupt cache under concurrent access. Use ConcurrentCacheDataLoader instead.")
    @Test
    fun stressTestDssFileCacheLoaderCommonConstraint() = runTest {
        val expectedHttpCalls = 17
        useResources {
            val httpLoader = ConstraintHttpLoader(
                cacheDispatcher = Dispatchers.IO.limitedParallelism(1),
                clock = clock,
                maxCacheSize = 20,
                ttl = 5.seconds,
                proxied = NativeHTTPDataLoader(),
            ).bind()

            val dssOptions = DssOptions.usingFileCacheDataLoader(
                httpLoader = httpLoader,
                fileCacheExpiration = 24.hours,
                cacheDirectory = createTempDirectory("lotl-cache-dss-constraint"),
            )

            val getTrustAnchors = GetTrustAnchorsFromLoTL(dssOptions)
                .cached(clock = clock, ttl = 10.seconds, expectedQueries = iterations).bind()

            doTest(httpLoader, getTrustAnchors, expectedHttpCalls)
        }
    }

    @Test
    fun stressTestConcurrentCacheDataLoader() = runTest {
        val expectedHttpCalls = 17
        val httpLoader = ObservableHttpLoader(NativeHTTPDataLoader())

        useResources {
            val loader = ConcurrentCacheDataLoader(
                httpLoader = httpLoader,
                fileCacheExpiration = 24.hours,
                cacheDirectory = createTempDirectory("lotl-cache-custom"),
            ).bind()
            val dssOptions = DssOptions(loader = loader)
            val getTrustAnchors = GetTrustAnchorsFromLoTL(dssOptions)
                .cached(clock = clock, ttl = 10.seconds, expectedQueries = iterations).bind()
            doTest(httpLoader, getTrustAnchors, expectedHttpCalls)
        }
    }

    private suspend fun CoroutineScope.doTest(
        httpGetCounter: GetCounter,
        getTrustAnchors: GetTrustAnchorsCachedSource<LOTLSource, TrustAnchor>,
        expectedHttpCalls: Int,
    ) {
        httpGetCounter.resetCallCount()
        val isX5CTrusted =
            getTrustAnchors
                .validator(EUDIRefDevEnv.supportedLists.asMap(), pkixValidator)
                .contraMap(::certsFromX5C)
        val (verifications, consoleOutput) = captureConsole {
            buildList {
                repeat(iterations) {
                    add(async { isX5CTrusted(pidX5c, VerificationContext.PID) })
                    add(async { isX5CTrusted(pidX5c, VerificationContext.PubEAA) })
                }
            }.awaitAll()
        }

        val expectedVerifications = iterations * 2 // two calls per iteration
        val expectedErrors = 0
        val httpCalls = httpGetCounter.callCount
        val fatalPrintedToConsole = consoleOutput.containsError(listOf("Fatal", "error"))

        println("Total verifications: ${verifications.size}/$expectedVerifications")
        println("Total http calls: $httpCalls/$expectedHttpCalls")
        println("Fatal errors: ${fatalPrintedToConsole.size}")
        println("Logs:")
        consoleOutput.allLines.forEach { println(it) }

        assertEquals(expectedHttpCalls, httpCalls, "Unexpected number of http calls")
        assertEquals(expectedVerifications, verifications.size, "Unexpected number of verifications")
        assertEquals(
            expectedErrors,
            fatalPrintedToConsole.size,
            fatalPrintedToConsole.joinToString("\n"),
        )

        assertEquals(expectedHttpCalls, httpCalls)
    }

    private class ConstraintHttpLoader(
        cacheDispatcher: CoroutineDispatcher,
        clock: Clock,
        ttl: Duration,
        maxCacheSize: Int,
        proxied: DataLoader,
    ) : DataLoader by proxied, Disposable, GetCounter {
        private val _callCount = AtomicInteger(0)
        override val callCount: Int get() = _callCount.get()
        private val cache = AsyncCache<String, ByteArray>(
            cacheDispatcher = cacheDispatcher,
            clock = clock,
            ttl = ttl,
            maxCacheSize = maxCacheSize,
        ) { url ->
            withContext(CoroutineName("Downloading $url")) {
                println("Downloading $url")
                _callCount.incrementAndGet()
                proxied.get(url)
            }
        }

        override fun resetCallCount(): Int = _callCount.getAndUpdate { 0 }

        override fun get(url: String): ByteArray =
            runBlocking {
                cache.invoke(url)
            }

        override fun dispose() {
            cache.dispose()
        }
    }
}

private val pidX5c: List<String> =
    listOf("MIIC3zCCAoWgAwIBAgIUf3lohTmDMAmS/YX/q4hqoRyJB54wCgYIKoZIzj0EAwIwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI1MDQxMDE0Mzc1MloXDTI2MDcwNDE0Mzc1MVowUjEUMBIGA1UEAwwLUElEIERTIC0gMDExLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCVVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS7WAAWqPze0Us3z8pajyVPWBRmrRbCi5X2s9GvlybQytwTumcZnej9BkLfAglloX5tv+NgWfDfgt/06s+5tV4lo4IBLTCCASkwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwGwYDVR0RBBQwEoIQaXNzdWVyLmV1ZGl3LmRldjAWBgNVHSUBAf8EDDAKBggrgQICAAABAjBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX1VUXzAyLmNybDAdBgNVHQ4EFgQUql/opxkQlYy0llaToPbDE/myEcEwDgYDVR0PAQH/BAQDAgeAMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwIDSAAwRQIhANJVSDsqT3IkGcKWWgSeubkDOdi5/UE9b1GF/X5fQRFaAiBp5t6tHh8XwFhPstzOHMopvBD/Gwms0RAUgmSn6ku8Gg==")

fun certsFromX5C(x5c: List<String>): List<X509Certificate> {
    val factory = CertificateFactory.getInstance("X.509")
    return x5c.map {
        val decoded = Base64.decode(it)
        factory.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
    }
}

private suspend fun <T> captureConsole(block: suspend () -> T): Pair<T, ConsoleOutput> {
    val originalOut = System.out
    val originalErr = System.err
    return ByteArrayOutputStream().use { outStream ->
        ByteArrayOutputStream().use { errStream ->
            System.setOut(PrintStream(outStream))
            System.setErr(PrintStream(errStream))
            val result = block()
            val console = ConsoleOutput(
                out = outStream.toString(),
                err = errStream.toString(),
            )
            result to console
        }
    }.also { _ ->
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
}

private data class ConsoleOutput(
    val out: String,
    val err: String,
) {
    val allLines: List<String>
        get() = (out + err)
            .split("\n")
            .filter { it.isNotBlank() }

    fun containsError(keywords: List<String>): List<String> {
        return allLines.filter { line ->
            keywords.any { keyword ->
                line.contains(keyword, ignoreCase = true)
            }
        }
    }
}
