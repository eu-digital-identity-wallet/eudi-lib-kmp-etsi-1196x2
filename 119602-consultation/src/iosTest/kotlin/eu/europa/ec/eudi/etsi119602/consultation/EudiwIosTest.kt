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

import eu.europa.ec.eudi.etsi119602.datamodel.PKIObject
import eu.europa.ec.eudi.etsi119602.datamodel.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import kotlin.io.encoding.Base64
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours

/**
 * End-to-end coverage of the iOS-specific data path: a [ServiceDigitalIdentity]'s DER
 * certificates are turned into [NSData] trust anchors by [defaultCreateTrustAnchorsIos],
 * then a leaf chain is validated against those anchors via the PKIX validator wired into
 * [ProvisionTrustAnchorsFromLoTEs.Companion.eudiwIos]. The LoTE-loading machinery itself is
 * common code and is covered by the cross-platform tests, so it is not re-stubbed here.
 */
class EudiwIosTest {

    private val rootSdi: ServiceDigitalIdentity =
        ServiceDigitalIdentity(x509Certificates = listOf(PKIObject(value = E2eTestCerts.rootDer)))

    @Test
    fun defaultCreateTrustAnchorsIos_convertsEachCertificate() {
        val anchors = defaultCreateTrustAnchorsIos(rootSdi)
        assertEquals(1, anchors.size)
        assertTrue(anchors.first().toByteArray().contentEquals(E2eTestCerts.rootDer))
    }

    @Test
    fun anchorsFromSdi_validateLeafChain_isTrusted() = runTest {
        val anchors = defaultCreateTrustAnchorsIos(rootSdi)
        // Test certs have no AIA/OCSP URLs; disable revocation so the test runs offline.
        val pkix = ValidateCertificateChainUsingPKIXIos(PKIXConfiguration(isRevocationEnabled = false))
        val leaf: NSData = E2eTestCerts.leafDer.toNSData()

        val result = pkix(listOf(leaf), NonEmptyList(anchors))

        if (result is CertificationChainValidation.NotTrusted) {
            kotlin.test.fail("expected Trusted, got NotTrusted: ${result.cause.message}")
        }
        assertIs<CertificationChainValidation.Trusted<NSData>>(result)
        assertTrue(result.trustAnchor.toByteArray().contentEquals(E2eTestCerts.rootDer))
    }

//    @Test
//    fun usingBundledAnchors_pkix_validatesAgainstBundledRoot_isTrusted() = runTest {
//        val validator = EudiwIosTrust.usingBundledAnchors(
//            pidAnchors = listOf(E2eTestCerts.rootDer.toNSData()),
//            walletAnchors = null,
//            wrpacAnchors = null,
//            wrprcAnchors = null,
//            pubEaaAnchors = null,
//            qeaAnchors = null,
//            mdlAnchors = null,
//            method = BundledAnchorMethod.PKIX,
//        )
//
//        val result = EudiwIosTrust.validate(
//            validator = validator,
//            chain = listOf(E2eTestCerts.leafDer.toNSData()),
//            context = VerificationContext.PID,
//        )
//
//        assertTrue(result.isTrusted, "expected trusted, got: ${result.failureReason}")
//    }

//    @Test
//    fun usingBundledAnchors_directTrust_pinsLeaf_isTrusted() = runTest {
//        val leaf = E2eTestCerts.leafDer.toNSData()
//        val validator = EudiwIosTrust.usingBundledAnchors(
//            pidAnchors = null,
//            walletAnchors = null,
//            wrpacAnchors = null,
//            wrprcAnchors = null,
//            pubEaaAnchors = null,
//            qeaAnchors = null,
//            mdlAnchors = listOf(leaf),
//            method = BundledAnchorMethod.DIRECT_TRUST,
//        )
//
//        val result = EudiwIosTrust.validate(
//            validator = validator,
//            chain = listOf(leaf),
//            context = VerificationContext.EAA(EudiwIosTrust.mdlUseCase),
//        )
//
//        assertTrue(result.isTrusted, "expected trusted (pinned leaf), got: ${result.failureReason}")
//    }

//    @Test
//    fun usingBundledAnchors_unconfiguredContext_isNotTrusted() = runTest {
//        val validator = EudiwIosTrust.usingBundledAnchors(
//            pidAnchors = listOf(E2eTestCerts.rootDer.toNSData()),
//            walletAnchors = null,
//            wrpacAnchors = null,
//            wrprcAnchors = null,
//            pubEaaAnchors = null,
//            qeaAnchors = null,
//            mdlAnchors = null,
//            method = BundledAnchorMethod.PKIX,
//        )
//
//        // QEAA has no bundled anchors, so it is rejected without ever invoking SecTrust.
//        val result = EudiwIosTrust.validate(
//            validator = validator,
//            chain = listOf(E2eTestCerts.leafDer.toNSData()),
//            context = VerificationContext.QEAA,
//        )
//
//        assertFalse(result.isTrusted)
//    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cachedHandle_servesSecondTrustAnchorsCallFromCache_andDisposes() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val anchor = E2eTestCerts.rootDer.toNSData()
        var loadCount = 0

        // A counting backend standing in for the LoTE download; the cache should call it only once.
        val source = GetTrustAnchors<VerificationContext, NSData> { ctx ->
            if (ctx == VerificationContext.PID) {
                loadCount++
                NonEmptyList(listOf(anchor))
            } else {
                null
            }
        }
        val scope = DisposableContainer()
        val cachedSource = source.cached(cacheDispatcher = testDispatcher, ttl = 1.hours, expectedQueries = 1)
        scope.add(cachedSource)
        val validator = ComposeChainTrust(
            cachedSource.validator(
                setOf<VerificationContext>(VerificationContext.PID),
                ValidateCertificateChainUsingDirectTrustIos,
            ),
        )
        val handle = CachedTrustValidator(scope, validator)

        val first = handle.trustAnchors(VerificationContext.PID)
        val second = handle.trustAnchors(VerificationContext.PID)

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(1, loadCount, "second call within the TTL must be served from cache")
        handle.dispose()
    }

//    @Test
//    fun cachedFacade_buildsAndDisposes() {
//        val handle = EudiwIosTrust.cached(
//            pidProvidersUrl = "https://example.test/pid",
//            walletProvidersUrl = null,
//            wrpacProvidersUrl = null,
//            wrprcProvidersUrl = null,
//            pubEaaProvidersUrl = null,
//            qeaProvidersUrl = null,
//            mdlProvidersUrl = null,
//            ttlHours = 1.0,
//            verifyJwtSignature = NotValidating,
//        )
//        handle.dispose()
//    }

    /**
     * iOS counterpart of `EUDIRefImplEnvTest.testCertificateTrust`. Downloads the EUDI reference
     * LoTEs over the network, then validates a set of PEM fixtures against the iOS trust-chain
     * pipeline (`EudiwIosTrust.nonCached` → Security.framework via PKIXBridge). Used to reproduce
     * client-side `.byCertificateChain` failures (e.g. "PID Issuer CA 02" pinning) locally.
     *
     * `@Ignore`d for CI (network-dependent); remove the annotation to run locally.
     */
    @Test
    @Ignore
    @OptIn(SensitiveApi::class)
    fun testCertificateTrust() = runTest {
        val urls = TrustListUrls().apply {
            pidProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PIDProviders.jwt"
            walletProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WalletProviders.jwt"
            wrpacProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPACProviders.jwt"
            wrprcProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPRCProviders.jwt"
        }
        val validator = EudiwIosTrust.nonCached(urls, NotValidating, isRevocationEnabled = true)
        runTrustCases(label = "default (revocation ON)") { chain, ctx ->
            EudiwIosTrust.validate(validator, chain, ctx)
        }
    }

    /**
     * Same as [testCertificateTrust] but disables revocation via the new
     * `pkixConfiguration` parameter on the facade. Confirms the alpha.1 → alpha.2 regression
     * (commit `2fd24463` flipped `PKIXConfiguration()`'s default from `false` to `true`, so
     * `SecTrust`'s `kSecRevocationRequirePositiveResponse` policy hard-fails when OCSP is
     * unavailable) and validates the workaround exposed to Swift consumers.
     */
    @Test
    @Ignore
    @OptIn(SensitiveApi::class)
    fun testCertificateTrust_revocationDisabled() = runTest {
        val urls = TrustListUrls().apply {
            pidProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PIDProviders.jwt"
            walletProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WalletProviders.jwt"
            wrpacProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPACProviders.jwt"
            wrprcProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPRCProviders.jwt"
        }
        val validator = EudiwIosTrust.nonCached(
            urls,
            NotValidating,
            isRevocationEnabled = false,
        )
        runTrustCases(label = "revocation OFF") { chain, ctx ->
            EudiwIosTrust.validate(validator, chain, ctx)
        }
    }

    private suspend fun runTrustCases(
        label: String,
        validate: suspend (chain: List<NSData>, context: VerificationContext) -> IosValidationResult,
    ) {
        val cases: List<Triple<String, String, VerificationContext>> = listOf(
            Triple("pidProviderSigningCertificateNotCompliant", EUDIRefImplEnvTestFixtures.pidProviderSigningCertificateNotCompliant, VerificationContext.PID),
            Triple("walletProviderSigningCertificateNotCompliant", EUDIRefImplEnvTestFixtures.walletProviderSigningCertificateNotCompliant, VerificationContext.WalletProviderAttestation),
            Triple("issuerAccessCertificate", EUDIRefImplEnvTestFixtures.issuerAccessCertificate, VerificationContext.WalletRelyingPartyAccessCertificate),
            Triple("verifierAccessCertificate", EUDIRefImplEnvTestFixtures.verifierAccessCertificate, VerificationContext.WalletRelyingPartyAccessCertificate),
            Triple("wrprcProviderSigningCertificate", EUDIRefImplEnvTestFixtures.wrprcProviderSigningCertificate, VerificationContext.WalletRelyingPartyRegistrationCertificate),
        )

        val failures = mutableListOf<String>()
        for ((caseLabel, pem, context) in cases) {
            val cert: NSData = pemToDer(pem).toNSData()
            val result = validate(listOf(cert), context)
            if (result.isTrusted) {
                println("[$label] TRUSTED     $caseLabel vs $context")
            } else {
                val line = "[$label] NOT TRUSTED $caseLabel vs $context: ${result.failureReason}"
                println(line)
                failures += line
            }
        }

        if (failures.isNotEmpty()) {
            fail(failures.joinToString(separator = "\n"))
        }
    }

    private fun pemToDer(pem: String): ByteArray {
        val body = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString(separator = "")
        return Base64.decode(body)
    }
}
