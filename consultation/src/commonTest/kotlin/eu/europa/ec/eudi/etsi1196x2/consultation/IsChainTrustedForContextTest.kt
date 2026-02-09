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

import kotlin.test.Test
import kotlin.test.assertTrue

class IsChainTrustedForContextTest {

    private class MockAutoCloseableGetTrustAnchors : GetTrustAnchors<String, String>, AutoCloseable {
        var closed = false
        override suspend fun invoke(query: String): NonEmptyList<String>? = null
        override fun close() {
            closed = true
        }
    }

    @Test
    fun testClose() {
        val validateCertificateChain = ValidateCertificateChain<String, String> { _, _ ->
            CertificationChainValidation.NotTrusted(RuntimeException())
        }
        val source = MockAutoCloseableGetTrustAnchors()
        val getTrustAnchorsByContext = GetTrustAnchorsForSupportedQueries(setOf("ctx1"), source)
        val isChainTrusted = IsChainTrustedForContext(validateCertificateChain, getTrustAnchorsByContext)

        isChainTrusted.close()

        assertTrue(source.closed, "Underlying source should be closed")
    }

    @Test
    fun testContraMapClose() {
        val validateCertificateChain = ValidateCertificateChain<String, String> { _, _ ->
            CertificationChainValidation.NotTrusted(RuntimeException())
        }
        val source = MockAutoCloseableGetTrustAnchors()
        val getTrustAnchorsByContext = GetTrustAnchorsForSupportedQueries(setOf("ctx1"), source)
        val isChainTrusted = IsChainTrustedForContext(validateCertificateChain, getTrustAnchorsByContext)

        val contraMapped = isChainTrusted.contraMap<Int> { it.toString() }
        contraMapped.close()

        assertTrue(source.closed, "Underlying source should be closed after contraMap and close")
    }

    @Test
    @SensitiveApi
    fun testRecoverWithClose() {
        val validateCertificateChain = ValidateCertificateChain<String, String> { _, _ ->
            CertificationChainValidation.NotTrusted(RuntimeException())
        }
        val source = MockAutoCloseableGetTrustAnchors()
        val getTrustAnchorsByContext = GetTrustAnchorsForSupportedQueries(setOf("ctx1"), source)
        val isChainTrusted = IsChainTrustedForContext(validateCertificateChain, getTrustAnchorsByContext)

        val recovered = isChainTrusted.recoverWith { _ -> null }
        (recovered as AutoCloseable).close()

        assertTrue(source.closed, "Underlying source should be closed after recoverWith and close")
    }
}
