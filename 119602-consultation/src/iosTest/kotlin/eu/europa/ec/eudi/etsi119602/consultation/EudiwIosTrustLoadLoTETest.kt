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

import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi119602.datamodel.*
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXIos
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.toByteArray
import eu.europa.ec.eudi.etsi1196x2.consultation.toNSData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Covers the caller-supplied [IosLoadLoTE] overloads of [EudiwIosTrust.nonCached] / [EudiwIosTrust.cached]:
 * the injected loader is the one asked for the LoTE, and the content it returns is what the trust
 * anchors are derived from. The mDL context is used because its metadata carries no end-entity
 * certificate profile.
 */
class EudiwIosTrustLoadLoTETest {

    private val mdlUrl = "https://example.test/mdl"
    private val mdlContext = VerificationContext.EAA("mDL")

    private val urls = TrustLists().apply {
        eaaProviders = listOf(EUTrustLists.mdl(mdlUrl, useCase = "mDL"))
    }

    private class RecordingLoadLoTE(private val jwt: String) : IosLoadLoTE {
        val requestedUris: MutableList<String> = mutableListOf()
        override suspend fun loadLoTE(uri: String): LoadLoTE.Outcome<String> {
            requestedUris += uri
            return LoadLoTE.Outcome.Loaded(jwt)
        }
    }

    @Test
    fun nonCached_usesInjectedLoadLoTE_andDerivesAnchorsFromIt() = runTest {
        val loadLoTE = RecordingLoadLoTE(mdlLoTEJwt(E2eTestCerts.rootDer))

        val validator = EudiwIosTrust.nonCached(
            trustLists = urls,
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,
            pkix = ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = false),
        )
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
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,
            pkix = ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = false),
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
        val loadLoTE = RecordingLoadLoTE(mdlLoTEJwt(E2eTestCerts.rootDer))
        EudiwIosTrust.nonCached(
            trustLists = urls,
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,
            pkix = ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = false),
        )
        EudiwIosTrust.cached(
            trustLists = urls,
            ttlHours = 1.0,
            verifyJwtSignature = NotValidating,
            pkix = ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = false),
        ).dispose()
    }

    /**
     * Issue #224: a PubEAA LoTE configured via [EUTrustLists.pubEaa] is loaded, and its Issuance and
     * Revocation services become the [VerificationContext.PubEAA] / [VerificationContext.PubEAAStatus]
     * anchors.
     */
    @Test
    fun pubEaa_lote_providesIssuanceAndRevocationAnchors() = runTest {
        val pubEaaUrl = "https://example.test/pubeaa"
        val loadLoTE = RecordingLoadLoTE(
            loteJwt(
                E2eTestCerts.rootDer to ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_ISSUANCE,
                E2eTestCerts.leafDer to ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
        )
        val validator = EudiwIosTrust.nonCached(
            trustLists = TrustLists().apply { pubEaaProviders = EUTrustLists.pubEaa(pubEaaUrl) },
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,
            pkix = ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = false),
        )

        val issuance = EudiwIosTrust.trustAnchors(validator, VerificationContext.PubEAA)
        val status = EudiwIosTrust.trustAnchors(validator, VerificationContext.PubEAAStatus)

        assertEquals(listOf(pubEaaUrl), loadLoTE.requestedUris.distinct(), "the PubEAA LoTE must be fetched")
        assertTrue(issuance.single().toByteArray().contentEquals(E2eTestCerts.rootDer))
        assertTrue(status.single().toByteArray().contentEquals(E2eTestCerts.leafDer))

        // A chain issued under the listed CA validates for PubEAA (PKIX, since the list is EndEntityOrCA).
        val result = EudiwIosTrust.validate(validator, listOf(E2eTestCerts.leafDer.toNSData()), VerificationContext.PubEAA)
        assertTrue(result.isTrusted, "expected trusted, got: ${result.failureReason}")
    }

    /** A JWT whose payload is an mDL LoTE advertising [certificate] as a single issuance service. */
    private fun mdlLoTEJwt(certificate: ByteArray): String = loteJwt(certificate to EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE)

    /**
     * A compact JWT whose payload is a LoTE with one trusted entity offering one service per
     * (certificate, service type) pair. The signature is never checked, so a placeholder is enough.
     */
    private fun loteJwt(vararg services: Pair<ByteArray, String>): String {
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
                        name = listOf(MultilanguageString.en("Test Provider")),
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
                    services = services.map { (certificate, svcType) ->
                        TrustedEntityService(
                            information = ServiceInformation(
                                name = listOf(MultilanguageString.en("Test service $svcType")),
                                digitalIdentity = ServiceDigitalIdentity(
                                    x509Certificates = listOf(PKIObject(value = certificate)),
                                ),
                                typeIdentifier = Uri.parse(svcType),
                            ),
                        )
                    },
                ),
            ),
        )
        val payload = Json.encodeToString(ListOfTrustedEntitiesClaims(lote))
        return "${base64Url("""{"alg":"none"}""")}.${base64Url(payload)}.signature"
    }

    private fun base64Url(value: String): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(value.encodeToByteArray())
}
