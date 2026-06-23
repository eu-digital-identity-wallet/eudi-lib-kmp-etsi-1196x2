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
package eu.europa.ec.eudi.etsi1196x2.consultation.certs

import eu.europa.ec.eudi.etsi1196x2.consultation.TestCertificates
import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CertificateOperationsIosTest {

    private val ops = CertificateOperationsIos()
    private val root: NSData get() = TestCertificates.rootDer
    private val leaf: NSData get() = TestCertificates.leafDer

    private val oidCommonName = "2.5.4.3"
    private val oidCountry = "2.5.4.6"

    @Test
    fun version_isV3() {
        assertEquals(2, ops.getVersion(leaf).value)
    }

    @Test
    fun subject_and_issuer_ofLeaf() {
        assertEquals("EUDI Test Leaf", ops.getSubject(leaf)?.get(oidCommonName))
        assertEquals("DE", ops.getSubject(leaf)?.get(oidCountry))
        assertEquals("EUDI Test Root CA", ops.getIssuer(leaf)?.get(oidCommonName))
    }

    @Test
    fun selfSigned_rootTrue_leafFalse() {
        assertTrue(ops.isSelfSigned(root))
        assertFalse(ops.isSelfSigned(leaf))
    }

    @Test
    fun basicConstraints_rootIsCa_leafIsNot() {
        assertTrue(ops.getBasicConstraints(root).isCa)
        assertFalse(ops.getBasicConstraints(leaf).isCa)
    }

    @Test
    fun keyUsage_rootSigns_leafDigitalSignature() {
        val rootKu = assertNotNull(ops.getKeyUsage(root))
        assertTrue(rootKu.keyCertSign)
        assertTrue(rootKu.crlSign)
        assertFalse(rootKu.digitalSignature)

        val leafKu = assertNotNull(ops.getKeyUsage(leaf))
        assertTrue(leafKu.digitalSignature)
        assertFalse(leafKu.keyCertSign)
    }

    @Test
    fun validityPeriod_notBeforeBeforeNotAfter() {
        val validity = ops.getValidityPeriod(leaf)
        assertTrue(validity.notBefore < validity.notAfter)
    }

    @Test
    fun serialNumber_isPresent() {
        assertTrue(ops.getSerialNumber(leaf).value.isNotEmpty())
    }

    @Test
    fun subjectKeyIdentifier_present_andAkiMatchesRootSki() {
        val rootSki = assertNotNull(ops.getSubjectKeyIdentifier(root))
        val leafAki = assertNotNull(ops.getAuthorityKeyIdentifier(leaf)?.keyIdentifier)
        // Leaf's AKI keyIdentifier should equal the issuer (root) SKI.
        assertTrue(rootSki.contentEquals(leafAki))
    }

    @Test
    fun publicKeyInfo_isRsa2048() {
        val pki = ops.getSubjectPublicKeyInfo(leaf)
        assertEquals("RSA", pki.algorithm)
        assertEquals(2048, pki.keySize)
    }

    @Test
    fun hasExtension_basicConstraints() {
        assertTrue(ops.hasExtension(root, "2.5.29.19"))
    }
}
