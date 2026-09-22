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

import eu.europa.ec.eudi.etsi119602.consultation.eu.CertificateProfileValidatorJVM
import eu.europa.ec.eudi.etsi119602.consultation.eu.pidSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.walletProviderSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.wrpAccessCertificateProfile
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import io.ktor.client.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
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
    @Ignore("Unstable network-based test")
    @SensitiveApi
    fun testDownload() = runTest {
        createHttpClient().use { httpClient ->

            val fileStore = LoTEFileStore(
                cacheDirectory = Path(Files.createTempDirectory("ref-impl-lote").toString()),
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
                pidProviders = pidProviders,
                walletProviders = walletProviders,
                wrpacProviders = wrpacProviders,
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
    fun testPidProviderSigningCertificateProfile() =
        pidSigningCertificateProfile().testCertificateExpectedToFail(
            EUDIRefImplEnvTestFixtures.pidProviderSigningCertificateNotCompliant,
            "does not contain any QCType",
        )

    @Test
    fun testWalletProviderSigningCertificateProfile() =
        walletProviderSigningCertificateProfile().testCertificateExpectedToFail(
            EUDIRefImplEnvTestFixtures.walletProviderSigningCertificateNotCompliant,
            "does not contain any QCType",
        )

    @Test
    fun testIssuerAccessCertificate() = wrpAccessCertificateProfile().testCertificate(EUDIRefImplEnvTestFixtures.issuerAccessCertificate)

    @Test
    fun testVerifierAccessCertificate() = wrpAccessCertificateProfile().testCertificate(EUDIRefImplEnvTestFixtures.verifierAccessCertificate)

    private fun CertificateProfile.testCertificate(pem: String) = runTest {
        val certificate = x509Certificate(pem)
        val evaluation = CertificateProfileValidatorJVM.validate(this@testCertificate, certificate)
        if (evaluation is CertificateConstraintEvaluation.Violated) {
            fail("Certificate validation failed: ${evaluation.violations.joinToString("\n")}")
        }
    }

    private fun CertificateProfile.testCertificateExpectedToFail(pem: String, expectedReasonSubstring: String) = runTest {
        val certificate = x509Certificate(pem)
        val evaluation = CertificateProfileValidatorJVM.validate(this@testCertificateExpectedToFail, certificate)
        val violated = evaluation as? CertificateConstraintEvaluation.Violated
            ?: fail("Certificate was expected to fail validation, but it passed")
        assertTrue(
            violated.violations.any { it.reason.contains(expectedReasonSubstring) },
            "Expected a violation containing '$expectedReasonSubstring', got: ${violated.violations.joinToString("\n") { it.reason }}",
        )
    }

    @Test
    @Ignore("Unstable network-based test")
    @OptIn(SensitiveApi::class)
    fun testCertificateTrust() = runTest {
        createHttpClient().use { httpClient ->
            val fileStore = LoTEFileStore(
                cacheDirectory = Path(Files.createTempDirectory("ref-impl-lote").toString()),
            )

            val isChainTrustedForContext = isChainTrustedForContext(httpClient, fileStore)
            // Disabled those two, text fixtures are not compliant
            // isChainTrustedForContext.testCertificate(EUDIRefImplEnvTestFixtures.pidProviderSigningCertificateNotCompliant, VerificationContext.PID)
            // isChainTrustedForContext.testCertificate(EUDIRefImplEnvTestFixtures.walletProviderSigningCertificateNotCompliant, VerificationContext.WalletProviderAttestation)
            isChainTrustedForContext.testCertificate(EUDIRefImplEnvTestFixtures.issuerAccessCertificate, VerificationContext.WalletRelyingPartyAccessCertificate)
            isChainTrustedForContext.testCertificate(EUDIRefImplEnvTestFixtures.verifierAccessCertificate, VerificationContext.WalletRelyingPartyAccessCertificate)
            isChainTrustedForContext.testCertificate(EUDIRefImplEnvTestFixtures.wrprcProviderSigningCertificate, VerificationContext.WalletRelyingPartyRegistrationCertificate)
        }
    }

    private suspend fun IsChainTrustedForEUDIW<List<X509Certificate>, TrustAnchor>.testCertificate(pem: String, context: VerificationContext) {
        val certificate = x509Certificate(pem)
        val certificationChainValidation = invoke(listOf(certificate), context)
            ?: error("Verification context $context has not been configured")

        if (certificationChainValidation is CertificationChainValidation.NotTrusted) {
            fail("Certificate could not be validated against context $context", certificationChainValidation.cause)
        }
    }

    private fun x509Certificate(pem: String): X509Certificate =
        JvmSecurity.DefaultX509Factory.generateCertificate(pem.byteInputStream()) as X509Certificate
}
