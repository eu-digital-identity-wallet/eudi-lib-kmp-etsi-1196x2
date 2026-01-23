/*
 * Copyright (c) 2023 European Commission
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

import eu.europa.ec.eudi.etsi119602.consultation.IsChainTrusted
import eu.europa.ec.eudi.etsi119602.consultation.TrustSource
import eu.europa.ec.eudi.etsi119602.consultation.ValidateCertificateChainJvm
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.spi.client.http.DSSCacheFileLoader
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.function.GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.function.Predicate
import kotlin.io.encoding.Base64
import kotlin.test.Test

class IsChainTrustedUsingLoTLTest {

    @Test
    fun testIsChainTrustedUsingLoTL() = runTest {
        val pubEEASource = TrustSource.LoTL("trustedlist.serviceproviders.eudiw.dev/PubEEA", "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA")
        val pidProviderSource = TrustSource.LoTL("trustedlist.serviceproviders.eudiw.dev/PID", "http://uri.etsi.org/Svc/Svctype/Provider/PID")

        val pubEEASourceCertificateSource = fetchLoTL("https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml", pubEEASource.serviceType)
        val pidProviderCertificateSource = fetchLoTL("https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml", pidProviderSource.serviceType)

        val dssSupport = CertTrustAnchorsFromLoTLUsingDSS(
            mapOf(
                pubEEASource to pubEEASourceCertificateSource,
                pidProviderSource to pidProviderCertificateSource,
            ),
        )

        val isChainTrusted = IsChainTrusted(
            validateCertificateChain = ValidateCertificateChainJvm { isRevocationEnabled = false },
            dssSupport,
        )
        val outcome = isChainTrusted(
            listOf(pidIssuerCert()),
            pidProviderSource,
        )
        assert(outcome is IsChainTrusted.Outcome.Trusted)
    }

    private fun fetchLoTL(lotlUrl: String, serviceType: String?): TrustedListsCertificateSource {
        val trustedListsCertificateSource = TrustedListsCertificateSource()

        val tlCacheDirectory = Files.createTempDirectory("lotl-cache").toFile()

        val offlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
            setCacheExpirationTime(24 * 60 * 60 * 1000)
            setFileCacheDirectory(tlCacheDirectory)
            dataLoader = IgnoreDataLoader()
        }

        val onlineLoader: DSSCacheFileLoader = FileCacheDataLoader().apply {
            setCacheExpirationTime(24 * 60 * 60 * 1000)
            setFileCacheDirectory(tlCacheDirectory)
            dataLoader = CommonsDataLoader()
        }

        val cacheCleaner = CacheCleaner().apply {
            setCleanMemory(true)
            setCleanFileSystem(true)
            setDSSFileLoader(offlineLoader)
        }

        val lotlSource = LOTLSource().apply {
            url = lotlUrl
            trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
            tlVersions = listOf(5, 6)
            serviceType?.let {
                trustServicePredicate = Predicate { tspServiceType ->
                    tspServiceType.serviceInformation.serviceTypeIdentifier == serviceType
                }
            }
        }

        val validationJob = TLValidationJob().apply {
            setListOfTrustedListSources(lotlSource)
            setOfflineDataLoader(offlineLoader)
            setOnlineDataLoader(onlineLoader)
            setTrustedListCertificateSource(trustedListsCertificateSource)
            setSynchronizationStrategy(ExpirationAndSignatureCheckStrategy())
            setCacheCleaner(cacheCleaner)
        }

        validationJob.onlineRefresh()

        return trustedListsCertificateSource
    }

    private fun pidIssuerCert(): X509Certificate {
        val base64Cert = "MIIC3zCCAoWgAwIBAgIUf3lohTmDMAmS/YX/q4hqoRyJB54wCgYIKoZIzj0EAwIwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI1MDQxMDE0Mzc1MloXDTI2MDcwNDE0Mzc1MVowUjEUMBIGA1UEAwwLUElEIERTIC0gMDExLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCVVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS7WAAWqPze0Us3z8pajyVPWBRmrRbCi5X2s9GvlybQytwTumcZnej9BkLfAglloX5tv+NgWfDfgt/06s+5tV4lo4IBLTCCASkwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwGwYDVR0RBBQwEoIQaXNzdWVyLmV1ZGl3LmRldjAWBgNVHSUBAf8EDDAKBggrgQICAAABAjBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX1VUXzAyLmNybDAdBgNVHQ4EFgQUql/opxkQlYy0llaToPbDE/myEcEwDgYDVR0PAQH/BAQDAgeAMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwIDSAAwRQIhANJVSDsqT3IkGcKWWgSeubkDOdi5/UE9b1GF/X5fQRFaAiBp5t6tHh8XwFhPstzOHMopvBD/Gwms0RAUgmSn6ku8Gg=="
        val decoded = Base64.decode(base64Cert)
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
    }
}
