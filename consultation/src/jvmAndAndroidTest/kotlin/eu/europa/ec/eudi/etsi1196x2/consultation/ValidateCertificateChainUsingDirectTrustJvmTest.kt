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
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.coroutines.test.runTest
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.*

class ValidateCertificateChainUsingDirectTrustJvmTest {
    private val certs = Sample.create()
    private val intermediate get() = certs.intermediate
    private val eeCertificate get() = certs.end
    private val root get() = certs.root

    @Test
    fun `when first certificate in chain matches trust anchor it should be trusted`() = runTest {
        val chain = listOf(eeCertificate, intermediate)
        val trustAnchors = NonEmptyList(listOf(TrustAnchor(eeCertificate, null)))

        val result = ValidateCertificateChainUsingDirectTrustJvm(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(result)
        assertEquals(eeCertificate, trusted.trustAnchor.trustedCert)
    }

    @Test
    fun `when chain and trust anchors contain only the eeCertificate it should be trusted`() = runTest {
        val chain = listOf(eeCertificate)
        val trustAnchors = NonEmptyList(listOf(TrustAnchor(eeCertificate, null)))

        val result = ValidateCertificateChainUsingDirectTrustJvm(chain, trustAnchors)

        val trusted = assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(result)
        assertEquals(eeCertificate, trusted.trustAnchor.trustedCert)
    }

    @Test
    fun `when first certificate in chain does not match any trust anchor it should not be trusted`() = runTest {
        val chain = listOf(eeCertificate, intermediate)
        val trustAnchors = NonEmptyList(listOf(TrustAnchor(root, null)))

        val result = ValidateCertificateChainUsingDirectTrustJvm(chain, trustAnchors)

        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun `when chain is empty it should throw exception`() = runTest {
        val chain = emptyList<X509Certificate>()
        val trustAnchors = NonEmptyList(listOf(TrustAnchor(root, null)))

        assertFailsWith<IllegalStateException> {
            ValidateCertificateChainUsingDirectTrustJvm(chain, trustAnchors)
        }
    }

    @Test
    fun `when trust anchor is missing certificate it should throw exception`() = runTest {
        val chain = listOf(eeCertificate)
        val trustAnchors = NonEmptyList(listOf(TrustAnchor("CN=Test", eeCertificate.publicKey, null)))

        assertFailsWith<IllegalStateException> {
            ValidateCertificateChainUsingDirectTrustJvm(chain, trustAnchors)
        }
    }
}
