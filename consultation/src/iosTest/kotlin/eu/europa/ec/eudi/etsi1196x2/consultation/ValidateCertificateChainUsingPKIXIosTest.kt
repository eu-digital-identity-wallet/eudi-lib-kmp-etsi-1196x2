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
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateCertificateChainUsingPKIXIosTest {

    private val root: NSData get() = TestCertificates.rootDer
    private val leaf: NSData get() = TestCertificates.leafDer
    private val other: NSData get() = TestCertificates.otherDer

    @Test
    fun pkix_validChain_isTrusted() = runTest {
        val validator = ValidateCertificateChainUsingPKIXIos()
        val result = validator(listOf(leaf), NonEmptyList(listOf(root)))
        if (result is CertificationChainValidation.NotTrusted) {
            kotlin.test.fail("expected Trusted, got NotTrusted: ${result.cause.message}")
        }
        assertIs<CertificationChainValidation.Trusted<NSData>>(result)
        assertTrue(
            result.trustAnchor.contentEqualsData(root),
            "matched anchor should be the root CA",
        )
    }

    @Test
    fun pkix_unrelatedAnchor_isNotTrusted() = runTest {
        val validator = ValidateCertificateChainUsingPKIXIos()
        // `other` is an unrelated CA that did not sign the leaf, so the chain cannot be anchored.
        // (Note: anchoring the leaf against itself WOULD be trusted — SecTrust trusts any cert
        // present in the anchor set, i.e. leaf pinning.)
        val result = validator(listOf(leaf), NonEmptyList(listOf(other)))
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun pkix_invalidDer_isNotTrusted() = runTest {
        val validator = ValidateCertificateChainUsingPKIXIos()
        val garbage = byteArrayOf(0xFF.toByte(), 0x00, 0x13, 0x37).toNSData()
        val result = validator(listOf(garbage), NonEmptyList(listOf(root)))
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }

    @Test
    fun directTrust_headMatchesAnchor_isTrusted() = runTest {
        val result = ValidateCertificateChainUsingDirectTrustIos(
            listOf(root),
            NonEmptyList(listOf(root)),
        )
        assertIs<CertificationChainValidation.Trusted<NSData>>(result)
        assertTrue(result.trustAnchor.contentEqualsData(root))
    }

    @Test
    fun directTrust_headDiffersFromAnchor_isNotTrusted() = runTest {
        val result = ValidateCertificateChainUsingDirectTrustIos(
            listOf(leaf),
            NonEmptyList(listOf(root)),
        )
        assertIs<CertificationChainValidation.NotTrusted>(result)
    }
}
