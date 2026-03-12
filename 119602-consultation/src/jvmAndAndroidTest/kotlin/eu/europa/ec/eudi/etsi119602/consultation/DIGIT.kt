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
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.hours

object DIGIT {

    private fun loteUrl(lote: String): String =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/$lote"

    val loteLocations = SupportedLists(
        pidProviders = loteUrl("pid-providers.json"),
        walletProviders = loteUrl("wallet-providers.json"),
        wrpacProviders = loteUrl("wrpac-providers.json"),
        eaaProviders = mapOf(
            "mdl" to loteUrl("mdl-providers.json"),
        ),
    )

    //
    // Has to relax constraints
    // The advertised lists do not satisfy the ETSI certificates profiles
    // This is ok, given that those lists are not official
    //
    val SVC_TYPE_PER_CTX: SupportedLists<LotEMeta<VerificationContext>>
        get() {
            val euBaseline = SupportedLists.eu()
            return euBaseline.copy(
                eaaProviders = mapOf(
                    "mdl" to LotEMeta(
                        svcTypePerCtx = mapOf(
                            VerificationContext.EAA("mdl") to EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE,
                            VerificationContext.EAAStatus("mdl") to EUMDLProvidersListSpec.SVC_TYPE_REVOCATION,
                        ),
                        serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.EndEntityOrCA,
                        endEntityCertificateProfile = null,
                    ),
                ),
            )
        }
}

class DIGITTest {

    @Test
    @SensitiveApi
    fun testDownload() = runTest {
        createHttpClient().use { httpClient ->
            val fileStore = LoTEFileStore(
                cacheDirectory = Path(System.getProperty("java.io.tmpdir")!!, "digit-lote"),
            )
            val loadLoTE = LoadSingleLoTEWithFileCache(
                fileStore = fileStore,
                downloadSingleLoTE = DownloadSingleLoTE(httpClient),
                fileCacheExpiration = 24.hours,
            )
            // Get the LoTEs, organized them as EUDIW verification contexts
            val provisionTrustAnchors = getTrustAnchorsProvisioner(loadLoTE, DIGIT.SVC_TYPE_PER_CTX)
            val isChainTrustedForContext = provisionTrustAnchors.nonCached(DIGIT.loteLocations)

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

            val actualContexts = isChainTrustedForContext.supportedContexts
            assertContentEquals(expectedContexts, actualContexts)
            actualContexts.forEach { ctx ->
                when (val outcome = isChainTrustedForContext.getTrustAnchors(ctx)) {
                    null -> println("$ctx : Not found")
                    else -> println("$ctx : ${outcome.list.size} ")
                }
            }
            fileStore.clear()
        }
    }
}
