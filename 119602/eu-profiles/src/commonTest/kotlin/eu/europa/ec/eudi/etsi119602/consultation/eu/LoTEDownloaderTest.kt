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

import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.consultation.LoTEDownloadEvent
import eu.europa.ec.eudi.etsi119602.consultation.LoTEDownloadResult
import eu.europa.ec.eudi.etsi119602.consultation.LoTEDownloader
import eu.europa.ec.eudi.etsi119602.consultation.usingLoTE
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail

class LoTEDownloaderTest {

    @Test
    fun testTrust() = runTest {
        val trustAnchorsFromLoTE =
            createHttpClient().use { httpClient -> loteTrust(httpClient, DIGIT.LISTS) }

        listOf(
            VerificationContext.PID,
            VerificationContext.PIDStatus,
            VerificationContext.WalletInstanceAttestation,
            VerificationContext.WalletUnitAttestation,
            VerificationContext.WalletUnitAttestationStatus,
            VerificationContext.WalletRelyingPartyAccessCertificate,
            VerificationContext.EAA("mdl"),
            VerificationContext.EAAStatus("mdl"),
        ).forEach { ctx ->
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
        val lotePerProfile =
            buildMap {
                lists.mapValues { (profile, uri) ->
                    val eventsFlow = with(LoTEHttpOps) {
                        httpClient.downloadFlow(uri, maxDepth = 2, maxTotalLotes = 30)
                    }

                    val downloadResult = LoTEDownloadResult.collect(eventsFlow)
                    val lote = downloadResult.list
                    try {
                        with(profile) { lote.ensureCompliesToProfile() }
                        put(profile, lote)
                    } catch (e: IllegalStateException) {
                        println("Not complies: $e")
                    }
                }
            }

        return GetTrustAnchorsForSupportedQueries.usingLoTE(lotePerProfile)
    }

    private interface LoTEHttpOps {

        fun HttpClient.downloadFlow(
            uri: String,
            parallelism: Int = 2,
            maxDepth: Int = 2,
            maxTotalLotes: Int = 30,
        ): Flow<LoTEDownloadEvent> {
            val downloader = LoTEDownloader(parallelism) { get(it).bodyAsText() }
            return downloader.downloadFlow(uri, maxDepth, maxTotalLotes)
        }

        companion object : LoTEHttpOps
    }
}
