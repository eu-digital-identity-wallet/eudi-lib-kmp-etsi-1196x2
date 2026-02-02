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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForContext
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.EUDIRefDevEnv.httpLoader
import eu.europa.esig.dss.spi.client.http.NativeHTTPDataLoader
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.nio.file.Files.createTempDirectory
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.function.Predicate
import kotlin.io.encoding.Base64
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

object EUDIRefDevEnv {

    //
    // Conventions applicable to EUDI Echo system
    //
    private const val PUB_EAA_SVC_TYPE = "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA"
    private const val PID_SVC_TYPE = "http://uri.etsi.org/Svc/Svctype/Provider/PID"
    private const val LOTL_URL = "https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml"
    private fun lotlSource(serviceType: String): LOTLSource {
        val lotlSource = LOTLSource().apply {
            url = LOTL_URL
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(6)
            trustServicePredicate =
                Predicate { tspServiceType ->
                    tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
                }
            isPivotSupport = true
        }
        return lotlSource
    }

    val httpLoader = ObservableHttpLoader(NativeHTTPDataLoader())
    val isChainTrustedForContext =
        IsChainTrustedForContext.usingLoTL(
            dssAdapter = DSSAdapter.usingFileCacheDataLoader(
                fileCacheExpiration = 24.hours,
                cacheDirectory = createTempDirectory("lotl-cache"),
                httpLoader = httpLoader,
            ),
            sourcePerVerification = buildMap {
                put(VerificationContext.PID, lotlSource(PID_SVC_TYPE))
                put(VerificationContext.PubEAA, lotlSource(PUB_EAA_SVC_TYPE))
            },
            validateCertificateChain = ValidateCertificateChainJvm(customization = {
                isRevocationEnabled = false
            }),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            coroutineDispatcher = Dispatchers.IO,
            ttl = 10.seconds,
        )
}

class IsChainTrustedUsingLoTLTest {

    val isX5CTrusted =
        EUDIRefDevEnv.isChainTrustedForContext.contraMap(::certsFromX5C)

    @Test
    fun verifyThatPidX5CIsTrustedForPIDContext() = runTest {
        val validation = isX5CTrusted(pidX5c, VerificationContext.PID)
        assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(validation)
    }

    @Test
    @Ignore("This is not passing because current LoTL contains the same certs for PID and PubEAA service types")
    fun verifyThatPidX5CIsNotTrustedForPubEAAContext() = runTest {
        assertIs<CertificationChainValidation.NotTrusted>(
            isX5CTrusted(pidX5c, VerificationContext.PubEAA),
        )
    }

    @Test
    fun verifyThatPidX5CFailsForAnUnsupportedContext() = runTest {
        assertNull(
            isX5CTrusted(pidX5c, VerificationContext.WalletUnitAttestation),
        )
    }
}

class IsChainTrustedUsingLoTLParallelTest {

    val isX5CTrusted =
        EUDIRefDevEnv.isChainTrustedForContext.contraMap(::certsFromX5C)

    @Test
    fun checkInParallel() = runTest {
        resetHttpLoaderAnd {
            buildList {
                repeat(200) {
                    add(async { isX5CTrusted(pidX5c, VerificationContext.PID) })
                    add(async { isX5CTrusted(pidX5c, VerificationContext.PubEAA) })
                }
            }.awaitAll()
        }
    }

    private suspend fun resetHttpLoaderAnd(doit: suspend () -> Unit): Int {
        httpLoader.reset()
        doit()
        return httpLoader.callCount.also { println("HTTP call count: $it") }
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
