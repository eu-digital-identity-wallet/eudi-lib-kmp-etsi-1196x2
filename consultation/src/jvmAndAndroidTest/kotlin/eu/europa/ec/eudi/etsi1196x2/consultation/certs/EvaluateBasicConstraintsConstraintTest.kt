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

import eu.europa.ec.eudi.etsi1196x2.consultation.CertOps
import eu.europa.ec.eudi.etsi1196x2.consultation.CertOps.toX509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificateOperationsJvm
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertTrue

class EvaluateBasicConstraintsConstraintTest {

    private val x500Name = X500Name("CN=Foo")

    private val certificateProfileValidator = CertificateProfileValidator(CertificateOperationsJvm)

    @Test
    fun `BasicConstraintsConstraint should reject CA when end-entity expected`() = runTest {
        // Generate a CA certificate (trust anchor)
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", x500Name)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for end-entity
        val profile = certificateProfile { endEntity() }

        // Validate
        val constraintEvaluation = certificateProfileValidator.validate(profile, certificate)

        // Should fail - CA certificate when end-entity expected
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("CA") })
    }

    @Test
    fun `BasicConstraintsConstraint should validate pathLenConstraint for CA certificates`() = runTest {
        // Generate a CA certificate (trust anchor) without pathLenConstraint
        val (_, certHolder) = CertOps.genTrustAnchor("SHA256withECDSA", x500Name)
        val certificate = certHolder.toX509Certificate()

        // Create constraint for CA with maxPathLen = 2
        val profile = certificateProfile { ca(2) }

        // Validate - should fail because CA certificate has no pathLenConstraint
        val constraintEvaluation = certificateProfileValidator.validate(profile, certificate)

        // Should fail - CA certificate missing pathLenConstraint
        assertTrue(!constraintEvaluation.isMet())
        assertTrue(constraintEvaluation.violations.any { it.reason.contains("pathLenConstraint") })
    }
}
