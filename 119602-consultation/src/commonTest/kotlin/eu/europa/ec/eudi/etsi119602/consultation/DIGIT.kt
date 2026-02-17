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
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail

object DIGIT {

    private fun loteUrl(lote: String): String =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/$lote"

    private val EU_PID_PROVIDERS_URL = loteUrl("pid-providers.json")
    private val EU_WALLET_PROVIDERS_URL = loteUrl("wallet-providers.json")
    private val EU_WRPAC_PROVIDERS_URL = loteUrl("wrpac-providers.json")
    private val EU_MDL_PROVIDERS_URL = loteUrl("mdl-providers.json")

    val LOTE_LOCATIONS = SupportedLists(
        pidProviders = EU_PID_PROVIDERS_URL,
        walletProviders = EU_WALLET_PROVIDERS_URL,
        wrpacProviders = EU_WRPAC_PROVIDERS_URL,
        eaaProviders = mapOf(
            "mdl" to EU_MDL_PROVIDERS_URL,
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
            createHttpClient().use { httpClient ->
                val fromHttp =
                    ProvisionTrustAnchorsFromLoTEs.fromHttp(
                        httpClient = httpClient,
                        constrains = LoadLoTEAndPointers.Constraints(
                            otherLoTEParallelism = 2,
                            maxDepth = 1,
                            maxLists = 40,
                        ),
                        svcTypePerCtx = DIGIT.SVC_TYPE_PER_CTX,
                        verifyJwtSignature = NotValidating,
                        continueOnProblem = ContinueOnProblem.Never,
                    )
                fromHttp(loteLocationsSupported = DIGIT.LOTE_LOCATIONS, parallelism = 10)
            }
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
}
