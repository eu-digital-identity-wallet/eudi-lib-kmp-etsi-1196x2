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

import eu.europa.ec.eudi.etsi119602.consultation.eu.pidSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.walletProviderSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.wrpAccessCertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidatorIos
import eu.europa.ec.eudi.etsi1196x2.consultation.toNSData
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.fail

class EUDIRefImplProfilesIosTest {

    @Test
    fun testPidProviderProfile() =
        pidSigningCertificateProfile().testCertificateExpectedToFail(
            EUDIRefImplEnvTestFixtures.pidProviderSigningCertificateNotCompliant,
            "does not contain any QCType",
        )

    @Test
    fun testWalletProviderProfile() =
        walletProviderSigningCertificateProfile().testCertificateExpectedToFail(
            EUDIRefImplEnvTestFixtures.walletProviderSigningCertificateNotCompliant,
            "does not contain any QCType",
        )

    @Test
    fun testIssuerAccessCertificate() =
        wrpAccessCertificateProfile().testCertificate(EUDIRefImplEnvTestFixtures.issuerAccessCertificate)

    @Test
    fun testVerifierAccessCertificate() =
        wrpAccessCertificateProfile().testCertificate(EUDIRefImplEnvTestFixtures.verifierAccessCertificate)

    // Runs the profile evaluation via the iOS validator (CertificateProfileValidatorIos →
    // CertificateOperationsIos → PKIXCertificateInspector → in-house ASN.1/X.509 parser).
    private fun CertificateProfile.testCertificate(pem: String) = runTest {
        val cert: NSData = pemToDer(pem).toNSData()
        val validator = CertificateProfileValidatorIos()
        val evaluation = validator.validate(this@testCertificate, cert)
        if (evaluation is CertificateConstraintEvaluation.Violated) {
            fail(
                "Certificate validation failed:\n" +
                    evaluation.violations.joinToString("\n") { it.reason },
            )
        }
    }

    private fun CertificateProfile.testCertificateExpectedToFail(pem: String, expectedReasonSubstring: String) = runTest {
        val cert: NSData = pemToDer(pem).toNSData()
        val validator = CertificateProfileValidatorIos()
        val evaluation = validator.validate(this@testCertificateExpectedToFail, cert)
        val violated = evaluation as? CertificateConstraintEvaluation.Violated
            ?: fail("Certificate was expected to fail validation, but it passed")
        if (violated.violations.none { it.reason.contains(expectedReasonSubstring) }) {
            fail(
                "Expected a violation containing '$expectedReasonSubstring', got:\n" +
                    violated.violations.joinToString("\n") { it.reason },
            )
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
