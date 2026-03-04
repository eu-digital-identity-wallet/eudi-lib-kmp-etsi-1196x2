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
import eu.europa.ec.eudi.etsi119602.x509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertContentEquals

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

    private fun LotEMata<VerificationContext, X509Certificate>.noConstraints() =
        copy(certificateConstraints = null)

    //
    // Has to relax constraints
    // The advertised lists do not satisfy the ETSI certificates profiles
    // This is ok, given that those lists are not official
    //
    val SVC_TYPE_PER_CTX: SupportedLists<LotEMata<VerificationContext, X509Certificate>>
        get() {
            val euBaseline = SupportedLists.eu(EULoTECertificateConstraintsJvm)
            return euBaseline.copy(
                pidProviders = euBaseline.pidProviders?.noConstraints(),
                walletProviders = euBaseline.walletProviders?.noConstraints(),
                wrpacProviders = euBaseline.wrpacProviders?.noConstraints(),
                eaaProviders = mapOf(
                    "mdl" to LotEMata(
                        svcTypePerCtx = mapOf(
                            VerificationContext.EAA("mdl") to EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE,
                            VerificationContext.EAAStatus("mdl") to EUMDLProvidersListSpec.SVC_TYPE_REVOCATION,
                        ),
                        directTrust = true,
                    ),
                ),
            )
        }
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

        val actualContexts = trustAnchorsFromLoTE.supportedContexts
        assertContentEquals(expectedContexts, actualContexts)
        actualContexts.forEach { ctx ->
            when (val outcome = trustAnchorsFromLoTE.getTrustAnchors(ctx)) {
                null -> println("$ctx : Not found")
                else -> println("$ctx : ${outcome.list.size} ")
            }
        }
    }

    private suspend fun HttpClient.get(
        loteLocationsSupported: SupportedLists<String>,
    ): ComposeChainTrust<*, VerificationContext, PKIObject> {
        val fromHttp =
            ProvisionTrustAnchorsFromLoTEs.eudiw(
                loadLoTEAndPointers = LoadLoTEAndPointers(
                    constraints = LoadLoTEAndPointers.Constraints(
                        otherLoTEParallelism = 2,
                        maxDepth = 1,
                        maxLists = 40,
                    ),
                    verifyJwtSignature = NotValidating,
                    loadLoTE = LoadLoTEFromHttp(this),
                ),
                constraints = EULoTECertificateConstraintsJvm,
                svcTypePerCtx = DIGIT.SVC_TYPE_PER_CTX,
                extractCertificate = { it.x509Certificate() },
                createTrustAnchors = { it.x509Certificates.orEmpty() },
                directTrust = ValidateCertificateChainUsingDirectTrust(
                    { _ -> error("Not used") },
                    { _ -> error("Not used") },
                ),
                pkix = ValidateCertificateChainUsingPKIX { _, _ -> error("Not used") },

            )

        return fromHttp(loteLocationsSupported, parallelism = 10)
    }
}
