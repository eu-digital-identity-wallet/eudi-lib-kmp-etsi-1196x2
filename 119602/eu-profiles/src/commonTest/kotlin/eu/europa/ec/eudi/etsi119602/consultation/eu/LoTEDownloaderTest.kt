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

import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTE
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail

class LoTEDownloaderTest {

    @Test
    fun testDigitTrust() = runTest {
        val trustAnchorsFromLoTE =
            createHttpClient().use { httpClient ->
                ProvisionTrustAnchorsFromLoTEs.fromHttp(
                    httpClient = httpClient,
                    parallelism = 10,
                    constrains = LoadLoTE.Constraints(
                        otherLoTEParallelism = 2,
                        maxDepth = 1,
                        maxLists = 40,
                    ),
                    loteLocationsSupported = DIGIT.LOTE_LOCATIONS,
                    svcTypePerCtx = DIGIT.SVC_TYPE_PER_CTX,
                )
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
