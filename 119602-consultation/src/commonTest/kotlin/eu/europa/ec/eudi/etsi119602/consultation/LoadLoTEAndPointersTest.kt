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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Covers the event listening of [LoadLoTEAndPointers]: the [LoadLoTEAndPointers.Event] of each
 * processed LoTE step is emitted to the [LoTEEventListener] (explicitly provided, or auto-wired
 * when `loadLoTE` is a [LoadSingleLoTEWithFileCache] via the dedicated constructor), so a caching
 * loader can commit a fresh download only after it passed verification.
 */
class LoadLoTEAndPointersTest {

    private val uri = Uri.parse("https://example.test/lote.jwt")
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun verifiedLoTE_emitsLoTELoaded() = runTest {
        val (jwt, _) = loteJwt(nextUpdate = now + 30.days)
        val listener = RecordingListener()
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded(jwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(listOf(RecordingListener.Call(uri, jwt, event)), listener.calls)
    }

    @Test
    fun invalidSignature_emitsInvalidJWTSignature() = runTest {
        val (jwt, _) = loteJwt(nextUpdate = now + 30.days)
        val listener = RecordingListener()
        val cause = IllegalStateException("bad signature")
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = { VerifyJwtSignature.Outcome.NotVerified(cause) },
                loadLoTE = { LoadLoTE.Outcome.Loaded(jwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.InvalidJWTSignature>(events.single())
        assertEquals(cause, event.cause)
        assertEquals(listOf(RecordingListener.Call(uri, jwt, event)), listener.calls)
    }

    @Test
    fun unparseableLoTE_emitsFailedToParseJwt() = runTest {
        val jwt = "not-a-jwt"
        val listener = RecordingListener()
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded(jwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.FailedToParseJwt>(events.single())
        assertEquals(listOf(RecordingListener.Call(uri, jwt, event)), listener.calls)
    }

    @Test
    fun expiredLoTE_emitsLoTEExpired() = runTest {
        val (jwt, _) = loteJwt(nextUpdate = now - 1.days)
        val listener = RecordingListener()
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded(jwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTEExpired>(events.single())
        assertEquals(listOf(RecordingListener.Call(uri, jwt, event)), listener.calls)
    }

    @Test
    fun notFoundLoTE_emitsResourceNotFoundWithNullJwt() = runTest {
        val listener = RecordingListener()
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = LoadLoTE { LoadLoTE.Outcome.NotFound(null) },
                clock = FakeClock(now),
                loTEEventListener = listener,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.ResourceNotFound>(events.single())
        assertEquals(listOf(RecordingListener.Call(uri, null, event)), listener.calls)
    }

    @Test
    fun badFreshLoTE_fallsBackToLastKnownGood() = runTest {
        val freshJwt = "not-a-jwt"
        val (fallbackJwt, fallbackLote) = loteJwt(nextUpdate = now + 30.days)
        val listener = RecordingListener()
        val fallback = RecordingFallbackProvider(fallbackJwt)
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded(freshJwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
                loTEFallbackProvider = fallback,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(fallbackLote, event.lote)
        assertEquals(listOf(uri), fallback.calls)
        val listenerCall = listener.calls.single()
        assertEquals(freshJwt, listenerCall.jwt)
        assertEquals(listenerCall.event as LoadLoTEAndPointers.Event.Problem, event.fallbackReason)
        assertIs<LoadLoTEAndPointers.Event.FailedToParseJwt>(listenerCall.event)
    }

    @Test
    fun notFoundLoTE_fallsBackToLastKnownGood() = runTest {
        val (fallbackJwt, fallbackLote) = loteJwt(nextUpdate = now + 30.days)
        val listener = RecordingListener()
        val fallback = RecordingFallbackProvider(fallbackJwt)
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = LoadLoTE { LoadLoTE.Outcome.NotFound(null) },
                clock = FakeClock(now),
                loTEEventListener = listener,
                loTEFallbackProvider = fallback,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(fallbackLote, event.lote)
        assertEquals(listOf(uri), fallback.calls)
        val listenerCall = listener.calls.single()
        assertEquals(null, listenerCall.jwt)
        assertEquals(listenerCall.event as LoadLoTEAndPointers.Event.Problem, event.fallbackReason)
        assertIs<LoadLoTEAndPointers.Event.ResourceNotFound>(listenerCall.event)
    }

    @Test
    fun fallbackThatAlsoFails_keepsOriginalProblem() = runTest {
        val listener = RecordingListener()
        val fallback = RecordingFallbackProvider("bad-fallback")
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded("bad-fresh") },
                clock = FakeClock(now),
                loTEEventListener = listener,
                loTEFallbackProvider = fallback,
            )

        val events = loader.invoke(uri).toList()

        assertIs<LoadLoTEAndPointers.Event.FailedToParseJwt>(events.single())
        assertEquals(listOf(uri), fallback.calls)
        assertIs<LoadLoTEAndPointers.Event.FailedToParseJwt>(listener.calls.single().event)
    }

    @Test
    fun fallbackNotUsed_whenFreshVerified() = runTest {
        val (jwt, lote) = loteJwt(nextUpdate = now + 30.days)
        val listener = RecordingListener()
        val fallback = RecordingFallbackProvider(null)
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded(jwt) },
                clock = FakeClock(now),
                loTEEventListener = listener,
                loTEFallbackProvider = fallback,
            )

        val events = loader.invoke(uri).toList()

        val event = assertIs<LoadLoTEAndPointers.Event.LoTELoaded>(events.single())
        assertEquals(lote, event.lote)
        assertNull(event.fallbackReason)
        assertEquals(emptyList<Uri>(), fallback.calls)
        assertEquals(listOf(RecordingListener.Call(uri, jwt, event)), listener.calls)
    }

    @Test
    fun fallbackNotUsed_whenNoFallbackAvailable() = runTest {
        val listener = RecordingListener()
        val fallback = RecordingFallbackProvider(null)
        val loader =
            LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
                verifyJwtSignature = NotValidating,
                loadLoTE = { LoadLoTE.Outcome.Loaded("not-a-jwt") },
                clock = FakeClock(now),
                loTEEventListener = listener,
                loTEFallbackProvider = fallback,
            )

        val events = loader.invoke(uri).toList()

        assertIs<LoadLoTEAndPointers.Event.FailedToParseJwt>(events.single())
        assertEquals(listOf(uri), fallback.calls)
    }

    private class RecordingListener : LoTEEventListener {
        data class Call(val uri: Uri, val jwt: String?, val event: LoadLoTEAndPointers.Event)

        val calls = mutableListOf<Call>()

        override suspend fun onEvent(uri: Uri, jwt: String?, event: LoadLoTEAndPointers.Event) {
            calls += Call(uri, jwt, event)
        }
    }

    private class RecordingFallbackProvider(private val fallbackJwt: String?) : LoTEFallbackProvider {
        val calls = mutableListOf<Uri>()

        override suspend fun lastKnownGood(uri: Uri): String? {
            calls += uri
            return fallbackJwt
        }
    }

    private class FakeClock(private val fixed: Instant) : Clock {
        override fun now(): Instant = fixed
    }

    /** A compact JWT whose payload is a LoTE with the given [nextUpdate], plus the parsed LoTE. */
    private fun loteJwt(nextUpdate: Instant): Pair<String, ListOfTrustedEntities> {
        val lote = ListOfTrustedEntities(
            schemeInformation = ListAndSchemeInformation.implicit(
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
