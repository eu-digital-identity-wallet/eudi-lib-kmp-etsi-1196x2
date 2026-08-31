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

import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi119602.datamodel.*
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Covers the caller-supplied [LoadLoTE] overloads of [EudiwIosTrust.nonCached] / [EudiwIosTrust.cached]:
 * the injected loader is the one asked for the LoTE, and the content it returns is what the trust
 * anchors are derived from. The mDL context is used because its metadata carries no end-entity
 * certificate profile.
 */
class EudiwIosTrustLoadLoTETest {

    private val mdlUrl = "https://example.test/mdl"
    private val mdlContext = VerificationContext.EAA(EudiwIosTrust.mdlUseCase)

    private val urls = TrustListUrls().apply { mdlProviders = mdlUrl }

    private class RecordingLoadLoTE(private val jwt: String) : LoadLoTE<String> {
        val requestedUris: MutableList<String> = mutableListOf()
        override suspend fun invoke(uri: Uri): LoadLoTE.Outcome<String> {
            requestedUris += uri.value
            return LoadLoTE.Outcome.Loaded(jwt)
        }
    }

    @Test
    fun nonCached_usesInjectedLoadLoTE_andDerivesAnchorsFromIt() = runTest {
        val loadLoTE = RecordingLoadLoTE(mdlLoTEJwt(E2eTestCerts.rootDer))

        val validator = EudiwIosTrust.nonCached(urls, InsecureAcceptAllJwtSignature, loadLoTE)
        val anchors = EudiwIosTrust.trustAnchors(validator, mdlContext)

        assertEquals(listOf(mdlUrl), loadLoTE.requestedUris, "the injected loader must be the one consulted")
        assertEquals(1, anchors.size)
        assertTrue(anchors.single().toByteArray().contentEquals(E2eTestCerts.rootDer))
    }

    @Test
    fun cached_usesInjectedLoadLoTE_andServesSecondCallFromCache() = runTest {
        val loadLoTE = RecordingLoadLoTE(mdlLoTEJwt(E2eTestCerts.rootDer))

        val handle = EudiwIosTrust.cached(
            urls,
            ttlHours = 1.0,
            verifyJwtSignature = InsecureAcceptAllJwtSignature,
            loadLoTE = loadLoTE,
        )
        try {
            val first = handle.trustAnchors(mdlContext)
            val second = handle.trustAnchors(mdlContext)

            assertEquals(1, first.size)
            assertEquals(1, second.size)
            assertTrue(first.single().toByteArray().contentEquals(E2eTestCerts.rootDer))
            assertEquals(
                listOf(mdlUrl),
                loadLoTE.requestedUris,
                "second call within the TTL must be served from cache, not from the loader",
            )
        } finally {
            handle.dispose()
        }
    }

    /** The overloads without a loader build the Darwin downloader, so only assembly is exercised. */
    @Test
    fun overloadsWithoutLoadLoTE_stillAssemble() {
        EudiwIosTrust.nonCached(urls, InsecureAcceptAllJwtSignature)
        EudiwIosTrust.cached(urls, ttlHours = 1.0, verifyJwtSignature = InsecureAcceptAllJwtSignature).dispose()
    }

    /**
     * A compact JWT whose payload is an mDL LoTE advertising [certificate] as the digital identity
     * of a single issuance service. The signature is never checked, so a placeholder is enough.
     */
    private fun mdlLoTEJwt(certificate: ByteArray): String {
        val now = Clock.System.now()
        val lote = ListOfTrustedEntities(
            schemeInformation = ListAndSchemeInformation.implicit(
                schemeOperatorName = listOf(MultilanguageString.en("Test Scheme Operator")),
                schemeTerritory = CountryCode.EU,
                listIssueDateTime = now,
                nextUpdate = now + 30.days,
            ),
            entities = listOf(
                TrustedEntity(
                    information = TrustedEntityInformation(
                        name = listOf(MultilanguageString.en("Test mDL Provider")),
                        address = TEAddress(
                            postalAddresses = listOf(
                                PostalAddress(
                                    language = Language.ENGLISH,
                                    streetAddress = "1 Test Street",
                                    country = CountryCode.EU,
                                ),
                            ),
                            electronicAddresses = listOf(
                                MultiLanguageURI.en(Uri.parse("mailto:test@example.test")),
                            ),
                        ),
                        informationURI = listOf(MultiLanguageURI.en(Uri.parse("https://example.test/info"))),
                    ),
                    services = listOf(
                        TrustedEntityService(
                            information = ServiceInformation(
                                name = listOf(MultilanguageString.en("Test mDL Issuance")),
                                digitalIdentity = ServiceDigitalIdentity(
                                    x509Certificates = listOf(PKIObject(value = certificate)),
                                ),
                                typeIdentifier = Uri.parse(EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val payload = Json.encodeToString(ListOfTrustedEntitiesClaims(lote))
        return "${base64Url("""{"alg":"none"}""")}.${base64Url(payload)}.signature"
    }

    private fun base64Url(value: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(value.encodeToByteArray())
}
