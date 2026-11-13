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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [CertificateConstraintsEvaluations.legalPersonIssuerDN] and
 * [CertificateConstraintsEvaluations.legalPersonSubjectDN].
 *
 * Per the analysis of ETSI EN 319 412-2/3 and ETSI TS 119 411-8 (see issue #149):
 * - `organizationIdentifier` is only *conditionally* mandatory for legal-person **issuer** DNs
 *   (EN 319 412-2 GEN-4.2.3.1-3: required only if a registration number is known to exist),
 *   so its absence must not be flagged by [CertificateConstraintsEvaluations.legalPersonIssuerDN].
 * - `organizationIdentifier` is unconditionally mandatory for legal-person **subject** DNs
 *   (EN 319 412-3 LEG-4.2.1-2; reinforced by TS 119 411-8 GEN-6.6.1-05 for WRPAC), so its
 *   absence must be flagged by [CertificateConstraintsEvaluations.legalPersonSubjectDN].
 */
class CertificateConstraintsEvaluationsLegalPersonDNTest {

    private val validOrgId = "VATEU-1KJTIIGC8Y12222"

    private fun legalPersonDn(
        country: String? = "EU",
        organization: String? = "ACME Corp",
        commonName: String? = "ACME Corp",
        organizationIdentifier: String? = validOrgId,
    ): DistinguishedName {
        val attributes = buildMap {
            country?.let { put(DistinguishedName.X500OIDs.COUNTRY, it) }
            organization?.let { put(DistinguishedName.X500OIDs.ORGANIZATION, it) }
            commonName?.let { put(DistinguishedName.X500OIDs.COMMON_NAME, it) }
            organizationIdentifier?.let { put(DistinguishedName.X500OIDs.ORG_IDENTIFIER, it) }
        }
        return DistinguishedName(attributes)
    }

    //
    // legalPersonIssuerDN - organizationIdentifier is NOT unconditionally mandatory
    //

    @Test
    fun `legalPersonIssuerDN accepts a fully populated DN including organizationIdentifier`() {
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(legalPersonDn())
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `legalPersonIssuerDN accepts a DN missing organizationIdentifier`() {
        val dn = legalPersonDn(organizationIdentifier = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertTrue(evaluation.isMet(), "Issuer DN without organizationIdentifier should be accepted: $evaluation")
    }

    @Test
    fun `legalPersonIssuerDN accepts a DN with blank organizationIdentifier`() {
        val dn = legalPersonDn(organizationIdentifier = "")
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertTrue(evaluation.isMet(), "Issuer DN with blank organizationIdentifier should be accepted: $evaluation")
    }

    @Test
    fun `legalPersonIssuerDN rejects an invalid organizationIdentifier format when present`() {
        val dn = legalPersonDn(organizationIdentifier = "NOT-A-VALID-ORGID")
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(
            evaluation.violations.any { it.reason.contains("organizationIdentifier") && it.reason.contains("invalid format") },
            "$evaluation",
        )
    }

    @Test
    fun `legalPersonIssuerDN rejects a DN missing countryName`() {
        val dn = legalPersonDn(country = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("countryName") })
    }

    @Test
    fun `legalPersonIssuerDN rejects a DN missing organizationName`() {
        val dn = legalPersonDn(organization = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("organizationName") })
    }

    @Test
    fun `legalPersonIssuerDN rejects a DN missing commonName`() {
        val dn = legalPersonDn(commonName = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("commonName") })
    }

    @Test
    fun `legalPersonIssuerDN rejects a null DN`() {
        val evaluation = CertificateConstraintsEvaluations.legalPersonIssuerDN(null)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("Issuer") && it.reason.contains("missing") })
    }

    //
    // legalPersonSubjectDN - organizationIdentifier IS unconditionally mandatory
    //

    @Test
    fun `legalPersonSubjectDN accepts a fully populated DN including organizationIdentifier`() {
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(legalPersonDn())
        assertTrue(evaluation.isMet(), "$evaluation")
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN missing organizationIdentifier`() {
        val dn = legalPersonDn(organizationIdentifier = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet(), "Subject DN without organizationIdentifier must be rejected")
        assertTrue(
            evaluation.violations.any { it.reason.contains("organizationIdentifier") && it.reason.contains("missing") },
            "$evaluation",
        )
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN with blank organizationIdentifier`() {
        val dn = legalPersonDn(organizationIdentifier = "   ")
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet(), "Subject DN with blank organizationIdentifier must be rejected")
        assertTrue(
            evaluation.violations.any { it.reason.contains("organizationIdentifier") && it.reason.contains("missing") },
            "$evaluation",
        )
    }

    @Test
    fun `legalPersonSubjectDN rejects an invalid organizationIdentifier format`() {
        val dn = legalPersonDn(organizationIdentifier = "NOT-A-VALID-ORGID")
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(
            evaluation.violations.any { it.reason.contains("organizationIdentifier") && it.reason.contains("invalid format") },
            "$evaluation",
        )
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN missing countryName`() {
        val dn = legalPersonDn(country = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("countryName") })
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN missing organizationName`() {
        val dn = legalPersonDn(organization = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("organizationName") })
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN missing commonName`() {
        val dn = legalPersonDn(commonName = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("commonName") })
    }

    @Test
    fun `legalPersonSubjectDN rejects a DN missing all mandatory attributes`() {
        val dn = legalPersonDn(country = null, organization = null, commonName = null, organizationIdentifier = null)
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.size >= 4, "$evaluation")
    }

    @Test
    fun `legalPersonSubjectDN rejects a null DN`() {
        val evaluation = CertificateConstraintsEvaluations.legalPersonSubjectDN(null)
        assertFalse(evaluation.isMet())
        assertTrue(evaluation.violations.any { it.reason.contains("Subject") && it.reason.contains("missing") })
    }
}
