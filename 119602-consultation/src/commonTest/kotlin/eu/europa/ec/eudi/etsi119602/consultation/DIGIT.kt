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

import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail

object DIGIT {

    private fun loteUrl(lote: String): String =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/$lote"

    val LOTE_LOCATIONS = SupportedLists(
        pidProviders = loteUrl("pid-providers.json"),
        walletProviders = loteUrl("wallet-providers.json"),
        wrpacProviders = loteUrl("wrpac-providers.json"),
        eaaProviders = mapOf(
            "mdl" to loteUrl("mdl-providers.json"),
        ),
    )

    val SVC_TYPE_PER_CTX = SupportedLists.EU.copy(
        eaaProviders = mapOf(
            "mdl" to mapOf(
                VerificationContext.EAA("mdl") to EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE,
                VerificationContext.EAAStatus("mdl") to EUMDLProvidersListSpec.SVC_TYPE_REVOCATION,
            ),
        ),
    )
}

class DIGITTest {

    @Test
    fun testDownload() = runTest {
        val trustAnchorsFromLoTE =
            createHttpClient().use { httpClient -> httpClient.get(DIGIT.LOTE_LOCATIONS) }

        val expectedContexts: List<VerificationContext> =
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

    private suspend fun HttpClient.get(
        loteLocationsSupported: SupportedLists<String>,
    ): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> {
        val fromHttp =
            ProvisionTrustAnchorsFromLoTEs.eudiw(
                LoadLoTEAndPointers(
                    constraints = LoadLoTEAndPointers.Constraints(
                        otherLoTEParallelism = 2,
                        maxDepth = 1,
                        maxLists = 40,
                    ),
                    verifyJwtSignature = NotValidating,
                    loadLoTE = LoadLoTEFromHttp(this),
                ),
                svcTypePerCtx = DIGIT.SVC_TYPE_PER_CTX,
                createTrustAnchor = { it },
            )

        return fromHttp(loteLocationsSupported, parallelism = 10)
    }
}
