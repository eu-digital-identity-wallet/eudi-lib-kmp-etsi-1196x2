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

import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.spi.DSSUtils
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource
import eu.europa.esig.dss.spi.x509.TrustedCertificateSource
import eu.europa.esig.dss.validation.SignedDocumentValidator
import eu.europa.esig.dss.validation.reports.Reports
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

object DSSSignatureValidator {
    fun validate(jwt: String, certificate: ByteArray): Boolean {
        val documentValidator = documentValidator(jwt.toDSSDocument(), certificate.toCertificateSource())
        val reports = documentValidator.validateDocument()
        return isValid(reports)
    }

    private fun documentValidator(document: DSSDocument, trustedCertificateSource: TrustedCertificateSource): SignedDocumentValidator {
        val certVerifier = CommonCertificateVerifier().apply {
            setTrustedCertSources(trustedCertificateSource)
        }
        return SignedDocumentValidator.fromDocument(document).apply {
            setCertificateVerifier(certVerifier)
        }
    }

    private fun isValid(reports: Reports): Boolean {
        val simpleReport = reports.simpleReport
        val signatureId = simpleReport.firstSignatureId
        return signatureId?.let { simpleReport.isValid(it) } ?: false
    }
}

private fun String.toDSSDocument() = InMemoryDocument(toByteArray())
private fun ByteArray.toCertificateToken() = DSSUtils.loadCertificate(this)
private fun ByteArray.toCertificateSource() = CommonTrustedCertificateSource().apply {
    addCertificate(toCertificateToken())
}

class SignatureValidatorTest {

    @Test
    fun testDSSSignatureValidator() = runTest {
        val jwt = SignatureValidatorTest::class.java.classLoader!!.getResourceAsStream("wallet-providers.txt")!!.bufferedReader().use { it.readText() }
        val certificate = SignatureValidatorTest::class.java.classLoader!!.getResourceAsStream("wallet-providers.cer")!!.use { it.readBytes() }
        val result = DSSSignatureValidator.validate(jwt, certificate)
        assertTrue(result)
    }
}
