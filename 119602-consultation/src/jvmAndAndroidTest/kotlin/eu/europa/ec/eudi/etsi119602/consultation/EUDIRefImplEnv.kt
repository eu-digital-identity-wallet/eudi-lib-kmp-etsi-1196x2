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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.isMet
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

    @Test
    fun testPidProviderProfile() = runTest {
        val pem = """
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
        """.trimIndent()
        suspend fun evaluateCertificateConstraints(
            certificate: X509Certificate,
        ): CertificateConstraintEvaluation =
            CertificateProfileValidatorJVM.validate(pidSigningCertificateProfile(), certificate)

        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
        val evaluation = evaluateCertificateConstraints(certificate)
        if (!evaluation.isMet()) {
            evaluation.violations.forEach { println(it.reason) }
        }
    }

    @Test
    fun testWalletProviderProfile() = runTest {
        val pem = """
            -----BEGIN CERTIFICATE-----
            MIICnjCCAkWgAwIBAgIUfe4X0ubB1E97+DX+WKX5KdYwR08wCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDQxNTI4NDRaFw0yODA1MDMxNTI4NDNaMFYxHjAcBgNVBAMMFVdhbGxldCBQcm92
            aWRlciBOaXNjeTEXMBUGA1UEBRMOTEVJRVUxMjM0NTY3ODkxDjAMBgNVBAoMBU5p
            c2N5MQswCQYDVQQGEwJFVTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABMjCdMfZ
            r/xQdS/YAJXKSWd9vIatS8nps3IA/I3GairMS5N1MU2S1AML9SgllUby78NLNn1d
            7pIvSzyYyCwO4pqjge8wgewwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBRCUFC+
            ELgQ8J1EXI2/qxAI7ifcSTAxBgNVHREEKjAohiZodHRwczovL2Rldi53YWxsZXQt
            cHJvdmlkZXIuZXVkaXcuZGV2LzAUBgNVHSAEDTALMAkGBwQAi+0MAQQwQwYDVR0f
            BDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3Bp
            ZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYEFHV7FXPTOzJgRiGO9e6gSMDEUMUHMA4G
            A1UdDwEB/wQEAwIHgDAKBggqhkjOPQQDAgNHADBEAiAeqe3oQJKq36qQJVvrpt5w
            fKh+oG/qtgxFzs8EulZqnQIgUT1HaQ9HBdYWhTDRE6CK+zFnpYbFj3fZxyKKX+wi
            o1w=
            -----END CERTIFICATE-----
        """.trimIndent()

        suspend fun evaluateCertificateConstraints(
            certificate: X509Certificate,
        ): CertificateConstraintEvaluation =
            CertificateProfileValidatorJVM.validate(walletProviderSigningCertificateProfile(), certificate)

        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
        val evaluation = evaluateCertificateConstraints(certificate)
        if (!evaluation.isMet()) {
            evaluation.violations.forEach { println(it.reason) }
        }
    }

    @Test
    fun testIssuerAccessCertificate() = runTest {
        val pem = """
            -----BEGIN CERTIFICATE-----
            MIICpjCCAkygAwIBAgIUM7/PArCKjv27uiY/Ni8A8azqaZ8wCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDQxNTAwNDNaFw0yODA1MDMxNTAwNDJaMFkxITAfBgNVBAMMGEtvdGxpbiBJc3N1
            ZXIgU2lnbmVyIERldjEXMBUGA1UEBRMOTEVJRVUxMjM0NTY3ODkxDjAMBgNVBAoM
            BU5pc2N5MQswCQYDVQQGEwJFVTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABJjX
            55LCoBGDOihHf0nsBg/NoVbO5j+wcg3JazvzzNhkKc8pDsYPwinJ8QeB9XE6T4BU
            SwjzBb5PJeGNftLFBS2jgfMwgfAwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBRC
            UFC+ELgQ8J1EXI2/qxAI7ifcSTA1BgNVHREELjAshipodHRwczovL2Rldi5rb3Rs
            aW5Jc3N1ZXJTaWduZXIuY29tL3N1cHBvcnQwFAYDVR0gBA0wCzAJBgcEAIvtDAEE
            MEMGA1UdHwQ8MDowOKA2oDSGMmh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2
            L2NybC9waWRfQ0FfRVVfMDIuY3JsMB0GA1UdDgQWBBQOj02dmRPIysmmbzGS7iBw
            C6l6eDAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhANwHb//QN5MT
            SrVu56xpRTyShNZBSqLF0TT2gJe98DBcAiAD+F/xgb4Lb1CPWzK1wsJg4CzIEzbx
            ce3TI4S4toz3aA==
            -----END CERTIFICATE-----
        """.trimIndent()
        suspend fun evaluateCertificateConstraints(
            certificate: X509Certificate,
        ): CertificateConstraintEvaluation =
            CertificateProfileValidatorJVM.validate(wrpAccessCertificateProfile(), certificate)

        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
        val evaluation = evaluateCertificateConstraints(certificate)
        if (!evaluation.isMet()) {
            evaluation.violations.forEach { println(it.reason) }
        }
    }

    @Test
    fun testVerifierAccessCertificate() = runTest {
        val pem = """
            -----BEGIN CERTIFICATE-----
            MIICnTCCAkSgAwIBAgIUfN4WXnsvepKTY3fc3guAVCzOY8MwCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDQxNTM0MDVaFw0yODA1MDMxNTM0MDRaMFQxHDAaBgNVBAMME1ZlcmlmaWVyIFNp
            Z25lciBkZXYxFzAVBgNVBAUTDkxFSUVVMTIzNDU2Nzg5MQ4wDAYDVQQKDAVOaXNj
            eTELMAkGA1UEBhMCRVUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARFWwOxh4K9
            l+zAs9PmFNaAfmZmi0MXfPV40Tlz8ry+vXesYxCtMcXSCyM0l3zkrDqDvxQfwRLR
            4N2GibVmxI59o4HwMIHtMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUQlBQvhC4
            EPCdRFyNv6sQCO4n3EkwMgYDVR0RBCswKYYnaHR0cHM6Ly9kZXYudmVyaWZpZXIt
            YmFja2VuZC5ldWRpdy5kZXYvMBQGA1UdIAQNMAswCQYHBACL7QwBBDBDBgNVHR8E
            PDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlk
            X0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUDHcVltLcuUu9Wij7+uMMat42rHkwDgYD
            VR0PAQH/BAQDAgeAMAoGCCqGSM49BAMCA0cAMEQCIH2VbWiul/bYGJFlhJ0mNh81
            zzM2DA2BKPtMTuheQ95EAiBjl6d1BfHgRu8QR9cO5paFnj72iz94nXmPyn8Prxz7
            6g==
            -----END CERTIFICATE-----
        """.trimIndent()
        suspend fun evaluateCertificateConstraints(
            certificate: X509Certificate,
        ): CertificateConstraintEvaluation =
            CertificateProfileValidatorJVM.validate(wrpAccessCertificateProfile(), certificate)

        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
        val evaluation = evaluateCertificateConstraints(certificate)
        if (!evaluation.isMet()) {
            evaluation.violations.forEach { println(it.reason) }
        }
    }
}
