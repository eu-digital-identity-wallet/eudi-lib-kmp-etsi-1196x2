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

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.validator
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.nio.file.Files.createTempDirectory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.function.Predicate
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.hours

@Suppress("SameParameterValue")
@RunWith(AndroidJUnit4::class)
class IsChainTrustedUsingLoTLAndroidTest {

    companion object {
        private const val PID_SVC_TYPE = "http://uri.etsi.org/Svc/Svctype/Provider/PID"

        private const val LOTL_URL = "https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml"

        private val pidX5c: List<String> = listOf(
            "MIIC3zCCAoWgAwIBAgIUf3lohTmDMAmS/YX/q4hqoRyJB54wCgYIKoZIzj0EAwIwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI1MDQxMDE0Mzc1MloXDTI2MDcwNDE0Mzc1MVowUjEUMBIGA1UEAwwLUElEIERTIC0gMDExLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCVVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS7WAAWqPze0Us3z8pajyVPWBRmrRbCi5X2s9GvlybQytwTumcZnej9BkLfAglloX5tv+NgWfDfgt/06s+5tV4lo4IBLTCCASkwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwGwYDVR0RBBQwEoIQaXNzdWVyLmV1ZGl3LmRldjAWBgNVHSUBAf8EDDAKBggrgQICAAABAjBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX1VUXzAyLmNybDAdBgNVHQ4EFgQUql/opxkQlYy0llaToPbDE/myEcEwDgYDVR0PAQH/BAQDAgeAMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwIDSAAwRQIhANJVSDsqT3IkGcKWWgSeubkDOdi5/UE9b1GF/X5fQRFaAiBp5t6tHh8XwFhPstzOHMopvBD/Gwms0RAUgmSn6ku8Gg==",
        )

        private fun certsFromX5C(x5c: List<String>): List<X509Certificate> {
            val factory = CertificateFactory.getInstance("X.509")
            return x5c.map {
                val decoded = Base64.decode(it)
                factory.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
            }
        }

        private fun lotlSource(serviceType: String): LOTLSource = LOTLSource().apply {
            url = LOTL_URL
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(6)
            trustServicePredicate = Predicate { tspServiceType ->
                tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
            }
            isPivotSupport = false
        }

        private fun pkixValidator(enableRevocation: Boolean) =
            ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = enableRevocation })

        private val dssOptions = DssOptions.usingFileCacheDataLoader(
            fileCacheExpiration = 24.hours,
            cacheDirectory = createTempDirectory("lotl-cache"),
        )

        val supportedListsMap = buildMap { put(VerificationContext.PID, lotlSource(PID_SVC_TYPE)) }
    }

    @Test
    fun verifyThatPidX5CIsTrustedForPIDContext() {
        val isX5CTrusted = GetTrustAnchorsFromLoTL(dssOptions)
            .validator(supportedListsMap, pkixValidator(enableRevocation = false))
            .contraMap(::certsFromX5C)

        val validation = runBlocking { isX5CTrusted(pidX5c, VerificationContext.PID) }

        assertNotNull("Validation result should not be null (context should be supported)", validation)
        assertTrue(
            "PID X5C should be trusted for PID context, but was: $validation",
            validation is CertificationChainValidation.Trusted<*>,
        )
    }
}
