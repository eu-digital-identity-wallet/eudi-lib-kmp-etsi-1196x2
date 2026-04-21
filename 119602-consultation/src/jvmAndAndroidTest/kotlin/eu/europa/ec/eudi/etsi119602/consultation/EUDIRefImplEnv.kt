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

import eu.europa.ec.eudi.etsi119602.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours

object EUDIRefImplEnv {

    // https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/RegistrarsAndRegisters.jwt
    // https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PubEAAProviders.jwt

    private fun String.uri() = Uri.parse(this)
    val LOTE_URL = SupportedLists(
        pidProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PIDProviders.jwt".uri(),
        walletProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WalletProviders.jwt".uri(),
        wrpacProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPACProviders.jwt".uri(),
        wrprcProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPRCProviders.jwt".uri(),
    )
}

class EUDIRefImplEnvTest {
    @Test
    @SensitiveApi
    fun testDownload() = runTest {
        createHttpClient().use { httpClient ->

            val fileStore = LoTEFileStore(
                cacheDirectory = Path(System.getProperty("java.io.tmpdir")!!, "ref-impl-lote"),
            )

            val isChainTrustedForContext = isChainTrustedForContext(httpClient, fileStore)

            val expectedContexts: List<VerificationContext> =
                listOf(
                    VerificationContext.PID,
                    VerificationContext.PIDStatus,
                    VerificationContext.WalletProviderAttestation,
                    VerificationContext.WalletProviderAttestationStatus,
                    VerificationContext.WalletRelyingPartyAccessCertificate,
                    VerificationContext.WalletRelyingPartyRegistrationCertificate,
                    VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
                )

            val actualContexts = isChainTrustedForContext.supportedContexts
            assertContentEquals(expectedContexts.sortedBy { it.toString() }, actualContexts.sortedBy { it.toString() })
            val errors = mutableMapOf<VerificationContext, Throwable>()
            actualContexts.forEach { ctx ->
                try {
                    when (val outcome = isChainTrustedForContext.getTrustAnchors(ctx)) {
                        null -> println("$ctx : Not found")
                        else -> println("$ctx : ${outcome.list.size} ")
                    }
                } catch (e: Exception) {
                    errors[ctx] = e
                }
            }
            if (errors.isNotEmpty()) {
                val es = buildString {
                    appendLine("Errors:")
                    errors.forEach { (ctx, e) ->
                        appendLine("$ctx ")
                        e.suppressed.forEach { appendLine(" - $it") }
                    }
                }
                fail(es)
            }
            fileStore.clear()
        }
    }

    @SensitiveApi
    private fun isChainTrustedForContext(
        httpClient: HttpClient,
        fileStore: LoTEFileStore,
    ): ComposeChainTrust<List<X509Certificate>, VerificationContext, TrustAnchor> {
        val loadLoTE = LoadSingleLoTEWithFileCache(
            fileStore = fileStore,
            downloadSingleLoTE = DownloadSingleLoTE(httpClient),
            fileCacheExpiration = 24.hours,

        )
        // Remove endEntityProfile from the list of supported contexts
        val svcTypePerCtx = SupportedLists.eu().run {
            fun LotEMeta<VerificationContext>.noEndEntityProfile() = copy(
                svcTypePerCtx = svcTypePerCtx.mapValues { (_, v) ->
                    v.copy(endEntityProfile = null)
                },
            )
            copy(
                pidProviders = pidProviders?.noEndEntityProfile(),
                walletProviders = walletProviders?.noEndEntityProfile(),
                wrpacProviders = wrpacProviders?.noEndEntityProfile(),
                wrprcProviders = wrprcProviders?.noEndEntityProfile(),
                pubEaaProviders = pubEaaProviders?.noEndEntityProfile(),
                qeaProviders = qeaProviders?.noEndEntityProfile(),
            )
        }
        // Get the LoTEs, organized them as EUDIW verification contexts
        val provisionTrustAnchors = getTrustAnchorsProvisioner(loadLoTE, svcTypePerCtx = svcTypePerCtx)
        return provisionTrustAnchors.nonCached(EUDIRefImplEnv.LOTE_URL)
    }

    @Test
    @SensitiveApi
    fun verifyThatPidX5CIsTrustedForPIDContext() = runTest {
        createHttpClient().use { httpClient ->
            val fileStore = LoTEFileStore(
                cacheDirectory = Path(System.getProperty("java.io.tmpdir")!!, "ref-impl-lote"),
            )
            val isChainTrustedForContext = isChainTrustedForContext(httpClient, fileStore).contraMap(::certsFromX5C)
            val validation = isChainTrustedForContext(pidX5c, VerificationContext.PID)
            assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(validation)
        }
    }

    private val pidX5c: List<String> =
        listOf("MIIC3zCCAoWgAwIBAgIUf3lohTmDMAmS/YX/q4hqoRyJB54wCgYIKoZIzj0EAwIwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI1MDQxMDE0Mzc1MloXDTI2MDcwNDE0Mzc1MVowUjEUMBIGA1UEAwwLUElEIERTIC0gMDExLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCVVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS7WAAWqPze0Us3z8pajyVPWBRmrRbCi5X2s9GvlybQytwTumcZnej9BkLfAglloX5tv+NgWfDfgt/06s+5tV4lo4IBLTCCASkwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwGwYDVR0RBBQwEoIQaXNzdWVyLmV1ZGl3LmRldjAWBgNVHSUBAf8EDDAKBggrgQICAAABAjBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX1VUXzAyLmNybDAdBgNVHQ4EFgQUql/opxkQlYy0llaToPbDE/myEcEwDgYDVR0PAQH/BAQDAgeAMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwIDSAAwRQIhANJVSDsqT3IkGcKWWgSeubkDOdi5/UE9b1GF/X5fQRFaAiBp5t6tHh8XwFhPstzOHMopvBD/Gwms0RAUgmSn6ku8Gg==")

    fun certsFromX5C(x5c: List<String>): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return x5c.map {
            val decoded = Base64.decode(it)
            factory.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
        }
    }
}
