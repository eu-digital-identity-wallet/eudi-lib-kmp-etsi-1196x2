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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntitiesClaims
import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail

class LoTEDownloaderTest {

    @Test
    fun testDigitTrust() = runTest {
        val trustAnchorsFromLoTE =
            createHttpClient().use { httpClient -> loteTrust(httpClient, DIGIT.LISTS) }
        val expectedContexts =
            listOf(
                VerificationContext.PID,
                VerificationContext.PIDStatus,
                VerificationContext.WalletInstanceAttestation,
                VerificationContext.WalletUnitAttestation,
                VerificationContext.WalletUnitAttestationStatus,
                VerificationContext.WalletRelyingPartyAccessCertificate,
                VerificationContext.EAA("mdl"),
                VerificationContext.EAAStatus("mdl"),
            )
        val actualContexts = trustAnchorsFromLoTE.supportedQueries
        assertContentEquals(expectedContexts, actualContexts)
        actualContexts.forEach { ctx ->
            when (val outcome = trustAnchorsFromLoTE.invoke(ctx)) {
                is GetTrustAnchorsForSupportedQueries.Outcome.Found<*> -> println("$ctx : ${outcome.trustAnchors.list.size} ")
                GetTrustAnchorsForSupportedQueries.Outcome.NotFound -> println("$ctx : Not found ")
                GetTrustAnchorsForSupportedQueries.Outcome.QueryNotSupported -> fail("Context not supported: $ctx")
            }
        }
    }

    suspend fun loteTrust(
        httpClient: HttpClient,
        lists: Map<EUListOfTrustedEntitiesProfile, String>,
    ): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> {
        val httpLoader = LoTEHttpLoader(
            httpClient,
            parallelism = 2,
            constrains = DownloadConstrains(maxDepth = 1, maxDownloads = 30),
        )

        val lotePerProfile =
            buildMap {
                lists.mapValues { (profile, uri) ->
                    val eventsFlow = httpLoader.downloadFlow(uri)
                    val summary = LoTEDownloadResult.collect(eventsFlow)
                    summary.downloaded?.lote?.let { put(profile.ctx(), it) }
                }
            }

        return GetTrustAnchorsForSupportedQueries.usingLoTE(lotePerProfile)
    }

    private class LoTEHttpLoader(
        httpClient: HttpClient,
        parallelism: Int,
        constrains: DownloadConstrains,
    ) {

        private val downloader = LoTEDownloader(parallelism, constrains) {
            val jwt = httpClient.get(it).bodyAsText()
            val (_, payload) = JwtUtil.headerAndPayload(jwt)
            JsonSupportDebug.decodeFromJsonElement(ListOfTrustedEntitiesClaims.serializer(), payload)
        }

        fun downloadFlow(uri: String): Flow<LoTEDownloadEvent> = downloader.downloadFlow(uri)
    }
}

private fun EUListOfTrustedEntitiesProfile.ctx(): Map<VerificationContext, URI> =
    buildMap {
        val (issuance, revocation) =
            trustedEntities.serviceTypeIdentifiers as ServiceTypeIdentifiers.IssuanceAndRevocation
        fun VerificationContext.putIssuance() = put(this, issuance)
        fun VerificationContext.putRevocation() = put(this, revocation)
        when (this@ctx) {
            EUPIDProvidersList -> {
                VerificationContext.PID.putIssuance()
                VerificationContext.PIDStatus.putRevocation()
            }

            EUWalletProvidersList -> {
                VerificationContext.WalletInstanceAttestation.putIssuance()
                VerificationContext.WalletUnitAttestation.putIssuance()
                VerificationContext.WalletUnitAttestationStatus.putRevocation()
            }

            EUWRPACProvidersList -> {
                VerificationContext.WalletRelyingPartyAccessCertificate.putIssuance()
            }

            EUWRPRCProvidersList -> {
                VerificationContext.WalletRelyingPartyRegistrationCertificate.putIssuance()
            }

            EUMDLProvidersList -> {
                VerificationContext.EAA("mdl").putIssuance()
                VerificationContext.EAAStatus("mdl").putRevocation()
            }

            else -> {}
        }
    }
