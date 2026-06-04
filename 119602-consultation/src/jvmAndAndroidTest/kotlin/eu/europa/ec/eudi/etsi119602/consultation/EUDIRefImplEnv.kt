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
import eu.europa.ec.eudi.etsi119602.consultation.eu.CertificateProfileValidatorJVM
import eu.europa.ec.eudi.etsi119602.consultation.eu.pidSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.walletProviderSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.wrpAccessCertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
                    VerificationContext.WalletOrKeyStorageStatus,
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
    fun testPidProviderProfile() = pidSigningCertificateProfile().testCertificate(
        """
            -----BEGIN CERTIFICATE-----
            MIIDADCCAqWgAwIBAgIUPYqmwQevpl4zHH0kInP2kmjornYwCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDcxMDQ1MDRaFw0yODA1MDYxMDQ1MDNaMFcxCzAJBgNVBAYTAkVVMQ4wDAYDVQQK
            DAVOaXNjeTEeMBwGA1UEAwwVUElEIFByb3ZpZGVyIERFViBFWCAyMRgwFgYDVQRh
            DA9MRUlFVS0xMjM0NTY3ODkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATGoyQ1
            k+dsTicH9I/U7zYrnkujdyzBXPX+XzoSQzq3baTMLY3MWx6yj3jaeXAjz0ccWU0v
            gDwo2bHKcQ/mkz4ao4IBTTCCAUkwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBRC
            UFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUHMAKG
            PWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJDQTAy
            LUVVLmNhY2VydC5wZW0wLgYDVR0gBCcwJTAjBgMqAwQwHDAaBggrBgEFBQcCARYO
            ZXhhbXBsZS5wb2xpY3kwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9k
            LnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYEFE+L
            ZfV8VC4akQ2J1kXpjr6AdHQSMA4GA1UdDwEB/wQEAwIHgDAZBggrBgEFBQcBAwQN
            MAswCQYHBACL7E4BATAKBggqhkjOPQQDAgNJADBGAiEA9eyUPSrnG84Q134rsSkH
            vCVI5zOksUqGnJtB9HaVHNECIQDBeW4UUk8jptkef6JRkAK52QOMGmIQ4bWZOZSe
            Twb1Ag==
            -----END CERTIFICATE-----
        """.trimIndent(),
    )

    @Test
    fun testWalletProviderProfile() = walletProviderSigningCertificateProfile().testCertificate(
        """
            -----BEGIN CERTIFICATE-----
            MIIDAjCCAqigAwIBAgIUWclZqMVuu3Er5tgW7exeSm1ibAkwCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDcxMDQ2MDVaFw0yODA1MDYxMDQ2MDRaMFoxCzAJBgNVBAYTAkVVMQ4wDAYDVQQK
            DAVOaXNjeTEhMB8GA1UEAwwYV2FsbGV0IFByb3ZpZGVyIERFViBFWCAyMRgwFgYD
            VQRhDA9MRUlFVS0xMjM0NTY3ODkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQm
            +N+6Cj8/4B59z1Fw/+iLXb+AG2BKIsBWMG1UZNJ7rdcxrXuaGEsVHWZL2vXBUGdx
            E8gr5Kkc6725Eh+spPyho4IBTTCCAUkwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW
            gBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUH
            MAKGPWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJD
            QTAyLUVVLmNhY2VydC5wZW0wLgYDVR0gBCcwJTAjBgMqAwQwHDAaBggrBgEFBQcC
            ARYOZXhhbXBsZS5wb2xpY3kwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVw
            cm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYE
            FPlYls0Eintao0UtQeopc5Cs+EhIMA4GA1UdDwEB/wQEAwIHgDAZBggrBgEFBQcB
            AwQNMAswCQYHBACL7E4BAjAKBggqhkjOPQQDAgNIADBFAiB89GW6rJmzKDi/AbLG
            JLfFee9FJntiQAT4Qh6rnuAhigIhAPhddtIl9ZpNxoVT0deASmgzeTv6lv6aRpAB
            xoZ/gbin
            -----END CERTIFICATE-----
        """.trimIndent(),
    )

    @Test
    fun testIssuerAccessCertificate() = wrpAccessCertificateProfile().testCertificate(
        """
           -----BEGIN CERTIFICATE-----
           MIIDAzCCAqqgAwIBAgIURqZMwltm47FnrUuswJZawUAjTtEwCgYIKoZIzj0EAwIw
           VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
           ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
           MDcxMzM3MzBaFw0yODA1MDYxMzM3MjlaMFoxITAfBgNVBAMMGEtvdGxpbiBJc3N1
           ZXIgU2lnbmVyIERldjELMAkGA1UEBhMCRVUxDjAMBgNVBAoMBU5pc2N5MRgwFgYD
           VQRhDA9MRUlFVS0xMjM0NTY3ODkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQD
           sy1vqh8TI7SUYY7OyZ0Tn08TaZPn+Zdw5BilTEVzXc6SSu0gAFkcaNKunRZB4JAk
           luQ5YKi6DRPa3s8fcYGWo4IBTzCCAUswDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW
           gBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUH
           MAKGPWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJD
           QTAyLUVVLmNhY2VydC5wZW0wNQYDVR0RBC4wLIYqaHR0cHM6Ly9kZXYua290bGlu
           SXNzdWVyU2lnbmVyLmNvbS9zdXBwb3J0MBQGA1UdIAQNMAswCQYHBACL7EYBAjBD
           BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
           cmwvcGlkX0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUwu8/c7hdHHi6rGE75pg3f4Yf
           JSswDgYDVR0PAQH/BAQDAgeAMAoGCCqGSM49BAMCA0cAMEQCIDjAxHdaaRIc1CG3
           oqbvYRbzIbMHoqNh2EUfLjLfsezLAiBPVXyUJQyJ/rE43aVgjB4tX5h8oAuQNEBS
           G9WdPfYDrg==
           -----END CERTIFICATE-----
        """.trimIndent(),
    )

    @Test
    fun testVerifierAccessCertificate() = wrpAccessCertificateProfile().testCertificate(
        """
                -----BEGIN CERTIFICATE-----
                MIIC/TCCAqKgAwIBAgIUK/6I3nrQOiMq/aIqMF7D7vv+xA4wCgYIKoZIzj0EAwIw
                VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
                ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
                MDcxMzM4MzhaFw0yODA1MDYxMzM4MzdaMFUxHDAaBgNVBAMME1ZlcmlmaWVyIFNp
                Z25lciBkZXYxCzAJBgNVBAYTAkVVMQ4wDAYDVQQKDAVOaXNjeTEYMBYGA1UEYQwP
                TEVJRVUtMTIzNDU2Nzg5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvZPdm4oz
                0rYYexoyJSYU5YG0ZBMTUQRzSVZjo2y0gZYU2jpxwb8/Rk1Aeb2rcc98CfJONqky
                a9p/wae5k7fChaOCAUwwggFIMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUQlBQ
                vhC4EPCdRFyNv6sQCO4n3EkwWQYIKwYBBQUHAQEETTBLMEkGCCsGAQUFBzAChj1o
                dHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9haWEvUElESXNzdWVyQ0EwMi1F
                VS5jYWNlcnQucGVtMDIGA1UdEQQrMCmGJ2h0dHBzOi8vZGV2LnZlcmlmaWVyLWJh
                Y2tlbmQuZXVkaXcuZGV2LzAUBgNVHSAEDTALMAkGBwQAi+xGAQIwQwYDVR0fBDww
                OjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9D
                QV9FVV8wMi5jcmwwHQYDVR0OBBYEFO+X15taOVBhkGJTBBt50FSN0zMPMA4GA1Ud
                DwEB/wQEAwIHgDAKBggqhkjOPQQDAgNJADBGAiEApj2PCZqVuQwq/Wy6y5gf2tm4
                XXYfyjgJS2jl6poPBK0CIQDOrjRS9rPbEK3MbUnQdcfZpRHCMeaT5+Fhqb+nrb89
                cw==
                -----END CERTIFICATE-----
        """.trimIndent(),
    )

    private fun CertificateProfile.testCertificate(pem: String) = runTest {
        val certificate = x509Certificate(pem)
        val evaluation = CertificateProfileValidatorJVM.validate(this@testCertificate, certificate)
        if (evaluation is CertificateConstraintEvaluation.Violated) {
            fail("Certificate validation failed: ${evaluation.violations.joinToString("\n")}")
        }
    }

    private fun x509Certificate(pem: String): X509Certificate =
        JvmSecurity.DefaultX509Factory.generateCertificate(pem.byteInputStream()) as X509Certificate
}
