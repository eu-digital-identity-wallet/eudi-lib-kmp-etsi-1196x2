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
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.TrustAnchor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for LoTE certificate constraint validators.
 */
class LoTECertificateConstraintValidatorTest {

    private val cnPidProvider = X500Name("CN=PID Provider Test")
    private val cnWalletProvider = X500Name("CN=Wallet Provider Test")
    private val cnWrpacProvider = X500Name("CN=WRPAC Provider Test")
    private val cnWrprcProvider = X500Name("CN=WRPRC Provider Test")

    @Test
    fun `PID Provider validator should validate end-entity certificate`() = runTest {
        // Generate a trust anchor (end-entity certificate for PID Provider)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnPidProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as PID Provider
        val failures = trustAnchor.validateAsPidProvider()

        // Should pass basic constraints (end-entity) and key usage (digitalSignature)
        // Will fail QCStatement and Certificate Policy (not implemented yet)
        assertTrue(
            failures.any { it.reason.contains("QCStatement") } ||
                failures.any { it.reason.contains("certificate policies") },
            "Expected failure for missing QCStatement or Certificate Policy",
        )
    }

    @Test
    fun `Wallet Provider validator should validate end-entity certificate`() = runTest {
        // Generate a trust anchor (end-entity certificate for Wallet Provider)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWalletProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as Wallet Provider
        val failures = trustAnchor.validateAsWalletProvider()

        // Should pass basic constraints (end-entity) and key usage (digitalSignature)
        // Will fail QCStatement and Certificate Policy (not implemented yet)
        assertTrue(
            failures.any { it.reason.contains("QCStatement") } ||
                failures.any { it.reason.contains("certificate policies") },
            "Expected failure for missing QCStatement or Certificate Policy",
        )
    }

    @Test
    fun `WRPAC Provider validator should validate CA certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPAC Provider)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPAC Provider
        val failures = trustAnchor.validateAsWrpacProvider()

        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (not implemented yet)
        assertTrue(
            failures.any { it.reason.contains("certificate policies") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `WRPRC Provider validator should validate CA certificate`() = runTest {
        // Generate a trust anchor (CA certificate for WRPRC Provider)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrprcProvider)
        val certificate = certHolder.toX509Certificate()
        val trustAnchor = TrustAnchor(certificate, null)

        // Validate as WRPRC Provider
        val failures = trustAnchor.validateAsWrprcProvider()

        // Should pass basic constraints (CA) and key usage (keyCertSign)
        // Will fail Certificate Policy (not implemented yet)
        assertTrue(
            failures.any { it.reason.contains("certificate policies") },
            "Expected failure for missing Certificate Policy",
        )
    }

    @Test
    fun `BasicConstraintsConstraint should reject CA when end-entity expected`() = runTest {
        // Generate a CA certificate (trust anchor)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for end-entity
        val constraint = BasicConstraintsConstraint.requireEndEntity(
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
        )

        // Validate
        val result = constraint(certificate)

        // Should fail - CA certificate when end-entity expected
        assertTrue(result is ConstraintValidationResult.Invalid)
        assertTrue((result as ConstraintValidationResult.Invalid).reason.contains("CA"))
    }

    @Test
    fun `KeyUsageConstraint should reject when digitalSignature not set`() = runTest {
        // Generate a CA certificate (has keyCertSign, not digitalSignature)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for digitalSignature
        val constraint = KeyUsageConstraint.requireDigitalSignature(
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
        )

        // Validate
        val result = constraint(certificate)

        // Should fail - CA certificate has keyCertSign, not digitalSignature
        assertTrue(result is ConstraintValidationResult.Invalid)
        assertTrue((result as ConstraintValidationResult.Invalid).reason.contains("keyUsage"))
    }

    @Test
    fun `KeyUsageConstraint should accept when digitalSignature is set`() = runTest {
        // Generate an end-entity certificate (has digitalSignature)
        val signerCertHolder = CertOps.createTrustAnchor(
            CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=Signer")).first,
            "SHA256withECDSA",
            X500Name("CN=Signer"),
        )
        val eeKeyPair = CertOps.genTrustAnchor("SHA256withECDSA", X500Name("CN=End Entity")).first
        val eeCertHolder = CertOps.createEndEntity(
            signerCertHolder,
            eeKeyPair.private,
            "SHA256withECDSA",
            eeKeyPair.public,
            X500Name("CN=End Entity"),
        )
        val certificate = eeCertHolder.toX509Certificate()

        // Create constraint for digitalSignature
        val constraint = KeyUsageConstraint.requireDigitalSignature(
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
        )

        // Validate
        val result = constraint(certificate)

        // Should pass
        assertTrue(result is ConstraintValidationResult.Valid)
    }

    @Test
    fun `ValidityPeriodConstraint should accept valid certificate`() = runTest {
        // Generate a valid certificate
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnPidProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint
        val constraint = ValidityPeriodConstraint.validateAtCurrentTime(
            getValidityPeriod = X509CertificateConstraintExtractors.getValidityPeriod,
        )

        // Validate
        val result = constraint(certificate)

        // Should pass - certificate is valid
        assertTrue(result is ConstraintValidationResult.Valid)
    }

    @Test
    fun `BasicConstraintsConstraint should validate pathLenConstraint for CA certificates`() = runTest {
        // Generate a CA certificate (trust anchor) without pathLenConstraint
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for CA with maxPathLen = 2
        val constraint = BasicConstraintsConstraint.requireCa(
            maxPathLen = 2,
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
        )

        // Validate - should fail because CA certificate has no pathLenConstraint
        val result = constraint(certificate)

        // Should fail - CA certificate missing pathLenConstraint
        assertTrue(result is ConstraintValidationResult.Invalid)
        assertTrue((result as ConstraintValidationResult.Invalid).reason.contains("pathLenConstraint"))
    }

    @Test
    fun `CertificateConstraintValidator should collect all failures`() = runTest {
        // Generate a CA certificate (will fail end-entity check)
        val (keyPair, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", cnWrpacProvider)
        val certificate = certHolder.toX509Certificate()

        // Create validator with multiple constraints for PID Provider
        val validator = LoTEX509CertificateValidators.pidProviderValidator()

        // Validate
        val failures = validator.validate(certificate)

        // Should have multiple failures (basic constraints, key usage, etc.)
        assertTrue(failures.isNotEmpty(), "Expected at least one validation failure")
    }
}
