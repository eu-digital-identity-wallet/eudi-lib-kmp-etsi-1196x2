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
@file:OptIn(SensitiveApi::class)

package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.datamodel.CountryCode
import eu.europa.ec.eudi.etsi119602.datamodel.ListAndSchemeInformation
import eu.europa.ec.eudi.etsi119602.datamodel.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.datamodel.ListOfTrustedEntitiesClaims
import eu.europa.ec.eudi.etsi119602.datamodel.MultilanguageString
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Covers the stage/commit behavior of [LoadSingleLoTEWithFileCache]: a fresh download is only
 * committed to the file store after the higher level emits [LoadLoTEAndPointers.Event.LoTELoaded]
 * for it, and a verification [LoadLoTEAndPointers.Event.Problem] keeps the last known good copy.
 *
 * Also covers the [LoTEFallbackProvider] behavior: the last known good LoTE is returned while its
 * `nextUpdate` is in the future, so the higher level can re-verify and serve it when the fresh
 * path fails.
 */
class LoadSingleLoTEWithFileCacheTest {

    private val uri = Uri.parse("https://example.test/lote.jwt")

    @Test
    fun verifiedDownload_isCommittedToCache() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val (jwt, lote) = loteJwt(1, Clock.System.now() + 30.days)
        val (downloader, requests) = mockDownloader(HttpStatusCode.OK to jwt)
        val loader = LoadSingleLoTEWithFileCache(cacheDir, downloadSingleLoTE = downloader)

        val loaded = (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content
        assertEquals(jwt, loaded)
        assertEquals(1, requests.size)
        assertEquals(null, fileStore.read(uri), "a fresh download must not be committed before verification")

        loader.onEvent(uri, jwt, LoadLoTEAndPointers.Event.LoTELoaded(lote, uri, 0))

        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(jwt, cached.jwt)
        assertEquals(1, cached.metadata.sequenceNumber)

        val second = loader.invoke(uri) as LoadLoTE.Outcome.Loaded
        assertEquals(jwt, second.content, "the committed download must be served from cache")
        assertEquals(1, requests.size, "no new download must be triggered for a valid cache entry")
    }

    @Test
    fun notVerifiedDownload_isDiscarded_andLastKnownGoodKept() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val (oldJwt, _) = loteJwt(1, now - 1.days)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = now - 1.days,
                sequenceNumber = 1,
            ),
        )
        val (downloader, _) = mockDownloader(HttpStatusCode.OK to "not-a-jwt")
        val loader = LoadSingleLoTEWithFileCache(cacheDir, downloadSingleLoTE = downloader)

        val badJwt = (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content
        assertEquals("not-a-jwt", badJwt)

        loader.onEvent(uri, badJwt, LoadLoTEAndPointers.Event.FailedToParseJwt(uri, IllegalStateException("bad")))

        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(oldJwt, cached.jwt, "the last known good LoTE must be kept")
    }

    @Test
    fun notVerifiedDownload_extendsLastKnownGoodUntilItsNextUpdate() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val nextUpdate = now + 10.days
        val (oldJwt, _) = loteJwt(1, nextUpdate)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = nextUpdate,
                sequenceNumber = 1,
            ),
        )
        val (downloader, _) = mockDownloader(HttpStatusCode.OK to "not-a-jwt")
        val loader = LoadSingleLoTEWithFileCache(
            cacheDir,
            downloadSingleLoTE = downloader,
            fileCacheExpiration = 30.days,
        )
        val badJwt = (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content

        loader.onEvent(uri, badJwt, LoadLoTEAndPointers.Event.FailedToParseJwt(uri, null))

        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(oldJwt, cached.jwt)
        assertEquals(
            nextUpdate.toEpochMilliseconds(),
            cached.metadata.expiresAt.toEpochMilliseconds(),
            "the old entry must keep serving until its nextUpdate",
        )

        val second = loader.invoke(uri) as LoadLoTE.Outcome.Loaded
        assertEquals(oldJwt, second.content, "the extended entry must be served from cache")
    }

    @Test
    fun verifiedDownloadWithLowerSequenceNumber_isNotCommitted() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val (oldJwt, _) = loteJwt(5, now - 1.days)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = now - 1.days,
                sequenceNumber = 5,
            ),
        )
        val (stagedJwt, stagedLote) = loteJwt(3, now + 30.days)
        val (downloader, _) = mockDownloader(HttpStatusCode.OK to stagedJwt)
        val loader = LoadSingleLoTEWithFileCache(cacheDir, downloadSingleLoTE = downloader)
        val staged = (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content
        assertEquals(stagedJwt, staged)

        loader.onEvent(uri, stagedJwt, LoadLoTEAndPointers.Event.LoTELoaded(stagedLote, uri, 0))

        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(
            oldJwt,
            cached.jwt,
            "a download with a lower sequence number must not replace the cached one",
        )
    }

    @Test
    fun event_forJwtThatIsNotTheStagedOne_isIgnored() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val (oldJwt, _) = loteJwt(1, now - 1.days)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = now - 1.days,
                sequenceNumber = 1,
            ),
        )
        val (stagedJwt, stagedLote) = loteJwt(2, now + 30.days)
        val (downloader, _) = mockDownloader(HttpStatusCode.OK to stagedJwt)
        val loader = LoadSingleLoTEWithFileCache(cacheDir, downloadSingleLoTE = downloader)
        val staged = (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content
        assertEquals(stagedJwt, staged)

        loader.onEvent(uri, "some-other-jwt", LoadLoTEAndPointers.Event.LoTELoaded(stagedLote, uri, 0))
        assertEquals(
            oldJwt,
            fileStore.read(uri)?.jwt,
            "an event for a different JWT must be a no-op",
        )

        loader.onEvent(uri, stagedJwt, LoadLoTEAndPointers.Event.LoTELoaded(stagedLote, uri, 0))
        assertEquals(stagedJwt, fileStore.read(uri)?.jwt)
    }

    @Test
    fun event_withoutPendingDownload_isIgnored() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val (jwt, lote) = loteJwt(1, now + 30.days)
        fileStore.write(
            uri,
            jwt,
            LoTEFileMetadata(
                loadedAt = now,
                expiresAt = now + 24.hours,
                nextUpdate = now + 30.days,
                sequenceNumber = 1,
            ),
        )
        val loader = LoadSingleLoTEWithFileCache(cacheDir)

        assertEquals(jwt, (loader.invoke(uri) as LoadLoTE.Outcome.Loaded).content)

        loader.onEvent(uri, jwt, LoadLoTEAndPointers.Event.LoTELoaded(lote, uri, 0))
        assertEquals(jwt, fileStore.read(uri)?.jwt, "an event without a pending download must be a no-op")
    }

    @Test
    fun lastKnownGood_returnsValidCachedLote_andExtendsExpiry() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val nextUpdate = now + 10.days
        val (oldJwt, _) = loteJwt(1, nextUpdate)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = nextUpdate,
                sequenceNumber = 1,
            ),
        )
        val loader = LoadSingleLoTEWithFileCache(cacheDir, fileCacheExpiration = 30.days)

        val result = loader.lastKnownGood(uri)

        assertEquals(oldJwt, result)
        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(oldJwt, cached.jwt)
        assertEquals(
            nextUpdate.toEpochMilliseconds(),
            cached.metadata.expiresAt.toEpochMilliseconds(),
            "the old entry must keep serving until its nextUpdate",
        )
    }

    @Test
    fun lastKnownGood_returnsNull_whenNextUpdatePassed() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val nextUpdate = now - 1.days
        val (oldJwt, _) = loteJwt(1, nextUpdate)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = nextUpdate,
                sequenceNumber = 1,
            ),
        )
        val loader = LoadSingleLoTEWithFileCache(cacheDir)

        assertNull(loader.lastKnownGood(uri))
    }

    @Test
    fun lastKnownGood_returnsNull_whenNothingCached() = runTest {
        val cacheDir = tempCacheDir()
        val loader = LoadSingleLoTEWithFileCache(cacheDir)

        assertNull(loader.lastKnownGood(uri))
    }

    @Test
    fun badDownload_fallsBackToLastKnownGood_whenUsedWithLoadLoTEAndPointers() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val nextUpdate = now + 10.days
        val (oldJwt, oldLote) = loteJwt(1, nextUpdate)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = nextUpdate,
                sequenceNumber = 1,
            ),
        )
        val (downloader, _) = mockDownloader(HttpStatusCode.OK to "not-a-jwt")
        val loadLoTE = LoadSingleLoTEWithFileCache(
            cacheDir,
            downloadSingleLoTE = downloader,
            fileCacheExpiration = 30.days,
        )
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = loadLoTE,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(oldLote, event.lote)
        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(oldJwt, cached.jwt)
        assertEquals(
            nextUpdate.toEpochMilliseconds(),
            cached.metadata.expiresAt.toEpochMilliseconds(),
            "the old entry must keep serving until its nextUpdate",
        )
    }

    @Test
    fun notFoundDownload_fallsBackToLastKnownGood_whenUsedWithLoadLoTEAndPointers() = runTest {
        val cacheDir = tempCacheDir()
        val fileStore = LoTEFileStore(cacheDir)
        val now = Clock.System.now()
        val nextUpdate = now + 10.days
        val (oldJwt, oldLote) = loteJwt(1, nextUpdate)
        fileStore.write(
            uri,
            oldJwt,
            LoTEFileMetadata(
                loadedAt = now - 2.days,
                expiresAt = now - 1.hours,
                nextUpdate = nextUpdate,
                sequenceNumber = 1,
            ),
        )
        val (downloader, _) = mockDownloader(HttpStatusCode.NotFound to "")
        val loadLoTE = LoadSingleLoTEWithFileCache(
            cacheDir,
            downloadSingleLoTE = downloader,
            fileCacheExpiration = 30.days,
        )
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = loadLoTE,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(oldLote, event.lote)
        val cached = assertNotNull(fileStore.read(uri))
        assertEquals(oldJwt, cached.jwt)
        assertEquals(
            nextUpdate.toEpochMilliseconds(),
            cached.metadata.expiresAt.toEpochMilliseconds(),
            "the old entry must keep serving until its nextUpdate",
        )
    }

    private fun tempCacheDir(): Path = Path(Files.createTempDirectory("lote-cache-test").toString())

    private fun mockDownloader(response: Pair<HttpStatusCode, String>): Pair<DownloadSingleLoTE, MutableList<String>> {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request.url.toString()
                    respond(content = response.second, status = response.first)
                }
            }
        }
        return DownloadSingleLoTE(client) to requests
    }

    /** A compact JWT whose payload is a LoTE with the given [sequenceNumber] and [nextUpdate], plus the parsed LoTE. */
    private fun loteJwt(sequenceNumber: Int, nextUpdate: Instant): Pair<String, ListOfTrustedEntities> {
        val now = Clock.System.now()
        val lote = ListOfTrustedEntities(
            schemeInformation = ListAndSchemeInformation.implicit(
                sequenceNumber = sequenceNumber,
                schemeOperatorName = listOf(MultilanguageString.en("Test Scheme Operator")),
                schemeTerritory = CountryCode.EU,
                listIssueDateTime = now,
                nextUpdate = nextUpdate,
            ),
        )
        val payload = Json.encodeToString(ListOfTrustedEntitiesClaims(lote))
        return "${base64Url("""{"alg":"none"}""")}.${base64Url(payload)}.signature" to lote
    }

    private fun base64Url(value: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(value.encodeToByteArray())
}
