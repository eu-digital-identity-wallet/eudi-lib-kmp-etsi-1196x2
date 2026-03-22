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

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ETSI319412Part1.ORG_ID_PATTERN
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ETSI319412Part1.VALID_NAT_ID_TYPES
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ETSI319412Part1.VALID_ORG_ID_TYPES
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

public object CertificateConstraintsEvaluations {

    public fun isEndEntity(
        constraints: BasicConstraintsInfo,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (constraints.isCa) {
            add(certificateTypeMismatch("end-entity", "CA"))
        }
    }

    public fun isCA(
        constraints: BasicConstraintsInfo,
        maxPathLen: Int? = null,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (!constraints.isCa) {
            add(certificateTypeMismatch("CA", "end-entity"))
        }
        if (constraints.isCa && maxPathLen != null) {
            val actualPathLen = constraints.pathLenConstraint
            when {
                actualPathLen == null ->
                    add(caCertificateMissingPathLenConstraint)

                actualPathLen > maxPathLen ->
                    add(certificatePathLenExceedsMaximum(maxPathLen, actualPathLen))
            }
        }
    }

    public fun mandatoryQcStatement(
        statements: List<QCStatementInfo>,
        qcType: String,
        requireCompliance: Boolean = false,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        when {
            statements.isEmpty() -> {
                add(certificateDoesNotContainAnyQCStatement)
            }

            statements.none { it.qcType == qcType } -> {
                add(certificateDoesNotContainRequiredQCStatement(qcType, statements))
            }

            requireCompliance && statements.none { it.qcType == qcType && it.qcCompliance } -> {
                add(certificateNotMarkedCompliantForQCStatement(qcType))
            }
        }
    }

    public fun mandatoryKeyUsage(
        keyUsageAndCritical: ExtensionInfo<KeyUsageBits>?,
        requiredKeyUsage: String,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (keyUsageAndCritical == null) {
            add(certificateDoesNotContainKeyUsage)
        } else {
            val (keyUsage, critical) = keyUsageAndCritical
            if (!critical) {
                add(keyUsageNotMarkedCritical)
            }
            if (!keyUsage[requiredKeyUsage]) {
                add(certificateMissingKeyUsage(requiredKeyUsage))
            }
        }
    }

    private operator fun KeyUsageBits.get(s: String): Boolean = when (s) {
        "digitalSignature" -> digitalSignature
        "nonRepudiation" -> nonRepudiation
        "keyEncipherment" -> keyEncipherment
        "dataEncipherment" -> dataEncipherment
        "keyAgreement" -> keyAgreement
        "keyCertSign" -> keyCertSign
        "crlSign" -> crlSign
        "encipherOnly" -> encipherOnly
        "decipherOnly" -> decipherOnly
        else -> error("Invalid key usage bit: $s")
    }

    public fun validAt(
        period: ValidityPeriod,
        time: Instant? = null,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        val validationTime = time ?: Clock.System.now()
        when {
            validationTime < period.notBefore -> {
                add(
                    CertificateConstraintViolation(
                        "Certificate is not yet valid. Valid from: ${period.notBefore}, current time: $validationTime",
                    ),
                )
            }

            validationTime > period.notAfter -> {
                add(
                    CertificateConstraintViolation(
                        "Certificate has expired. Valid until: ${period.notAfter}, current time: $validationTime",
                    ),
                )
            }
        }
    }

    public fun policyOneOf(
        policiesInfo: ExtensionInfo<List<String>>?,
        oids: Set<String>,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (policiesInfo == null) {
            add(certificateDoesNotContainPolicies(oids.toList()))
        } else {
            val policies = policiesInfo.value
            when {
                policies.isEmpty() -> {
                    add(certificateDoesNotContainPolicies(oids.toList()))
                }

                policies.none { it in oids } -> {
                    add(certificateDoesNotContainAnyPolicy(policies, oids.toList()))
                }
            }
        }
    }

    public fun policyIsPresent(
        policiesInfo: ExtensionInfo<List<String>>?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (policiesInfo == null || policiesInfo.value.isEmpty()) {
            add(missingCertificatePoliciesExtension)
        }
    }

    public fun aiaForCaIssued(
        aiaInfo: ExtensionInfo<AuthorityInformationAccess>?,
        isSelfSigned: Boolean,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        // Only check AIA for non-self-signed certificates
        if (!isSelfSigned) {
            if (aiaInfo == null) {
                add(caIssuedCertificateMissingAiaExtension)
            } else if (aiaInfo.value.caIssuersUri == null) {
                add(aiaMissingIdAdCaIssuersAccessMethod)
            }
        }
        // Self-signed certificates: no AIA check needed (pass silently)
    }

    public fun notSelfSigned(isSelfSigned: Boolean): CertificateConstraintEvaluation =
        CertificateConstraintEvaluation {
            if (isSelfSigned) {
                add(selfSignedCertificateNotAllowed)
            }
        }

    public fun isVersion(
        version: Version,
        expectedVersion: Int,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (version.value != expectedVersion) {
            add(certificateVersionMismatch(expectedVersion, version.value))
        }
    }

    public fun positiveSerialNumber(serialNumber: SerialNumber): CertificateConstraintEvaluation =
        CertificateConstraintEvaluation {
            // Serial number is already validated to be positive in the SerialNumber constructor
            // This check is redundant but kept for explicit validation
            if (serialNumber.value.isEmpty()) {
                add(serialNumberNotPositive)
            }
            // Check MSB is 0 (positive number)
            if (serialNumber.value[0].toInt() and 0x80 != 0) {
                add(serialNumberNotPositive)
            }
        }

    public fun subjectNaturalPersonAttributes(
        subject: DistinguishedName?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (subject == null) {
            add(missingSubjectDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (subject.country.isNullOrBlank()) {
            add(subjectMissingCountryName)
        }

        // givenName, surname, or pseudonym is mandatory
        val hasName = !subject.givenName.isNullOrBlank() ||
            !subject.surname.isNullOrBlank() ||
            !subject.pseudonym.isNullOrBlank()
        if (!hasName) {
            add(subjectMissingPersonalName)
        }

        // commonName is mandatory
        if (subject.commonName.isNullOrBlank()) {
            add(subjectMissingCommonName)
        }

        // serialNumber is mandatory for natural persons
        val serialNumber = subject.serialNumber
        if (serialNumber.isNullOrBlank()) {
            add(subjectMissingSerialNumber)
        } else {
            // natural person identity type reference validation (EN 319 412-1 clause 5.1.3)
            if (!isValidNaturalPersonId(serialNumber)) {
                add(subjectSerialNumberInvalidFormat)
            }
        }
    }

    public fun validSubjectLegalPersonAttributes(
        subject: DistinguishedName?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (subject == null) {
            add(missingSubjectDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (subject.country.isNullOrBlank()) {
            add(subjectMissingCountryName)
        }

        // organizationName is mandatory
        if (subject.organization.isNullOrBlank()) {
            add(subjectMissingOrganizationName)
        }

        // organizationIdentifier is mandatory
        val organizationIdentifier = subject.organizationIdentifier
        if (organizationIdentifier.isNullOrBlank()) {
            add(subjectMissingOrganizationIdentifier)
        } else {
            // organizationIdentifier format validation (EN 319 412-1 clause 5.1.4)
            if (!isValidOrgId(organizationIdentifier)) {
                add(organizationIdentifierInvalidFormat)
            }
        }

        // commonName is mandatory
        if (subject.commonName.isNullOrBlank()) {
            add(subjectMissingCommonName)
        }
    }

    public fun validIssuerLegalPersonAttributes(
        issuer: DistinguishedName?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (issuer == null) {
            add(missingIssuerDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (issuer.country.isNullOrBlank()) {
            add(issuerMissingCountryName)
        }

        // organizationName is mandatory
        if (issuer.organization.isNullOrBlank()) {
            add(issuerMissingOrganizationName)
        }

        // organizationIdentifier is mandatory
        val organizationIdentifier = issuer.organizationIdentifier
        if (organizationIdentifier.isNullOrBlank()) {
            add(issuerMissingOrganizationIdentifier)
        } else {
            // organizationIdentifier format validation (EN 319 412-1 clause 5.1.4)
            if (!isValidOrgId(organizationIdentifier)) {
                add(organizationIdentifierInvalidFormat)
            }
        }

        // commonName is mandatory
        if (issuer.commonName.isNullOrBlank()) {
            add(issuerMissingCommonName)
        }
    }

    /**
     * Validates an organization identifier against the ETSI EN 319 412-1 format.
     *
     * @param orgId the organization identifier to validate
     * @return true if valid, false otherwise
     */
    public fun isValidOrgId(orgId: String): Boolean {
        val matchResult = ORG_ID_PATTERN.matchEntire(orgId) ?: return false
        val identityType = matchResult.groupValues[1]
        return identityType in VALID_ORG_ID_TYPES
    }

    /**
     * Validates a natural person serial number against the ETSI EN 319 412-1 format.
     *
     * @param serialNumber the serial number to validate
     * @return true if valid, false otherwise
     */
    public fun isValidNaturalPersonId(serialNumber: String): Boolean {
        val matchResult = ORG_ID_PATTERN.matchEntire(serialNumber) ?: return false
        val identityType = matchResult.groupValues[1]
        return identityType in VALID_NAT_ID_TYPES
    }

    public fun validIssuerAttributes(
        issuer: DistinguishedName?,
        requireCountryName: Boolean = true,
        requireOrganizationName: Boolean = true,
        requireCommonName: Boolean = true,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (issuer == null) {
            add(missingIssuerDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        if (requireCountryName && issuer.country.isNullOrBlank()) {
            add(issuerMissingCountryName)
        }

        if (requireOrganizationName && issuer.organization.isNullOrBlank()) {
            add(issuerMissingOrganizationName)
        }

        if (requireCommonName && issuer.commonName.isNullOrBlank()) {
            add(issuerMissingCommonName)
        }
    }

    public fun subjectAltName(
        sanInfo: ExtensionInfo<List<SubjectAlternativeName>>?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (sanInfo == null || sanInfo.value.isEmpty()) {
            add(missingSubjectAltName)
        }
    }

    public fun authorityKeyIdentifier(
        aki: AuthorityKeyIdentifier?,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (aki == null) {
            add(missingAuthorityKeyIdentifier)
        }
    }

    public fun evaluateCrlDistributionPointsIfNoOcspAndNotValAssured(
        crldp: List<CrlDistributionPoint>,
        aiaInfo: ExtensionInfo<AuthorityInformationAccess>?,
        qcStatements: List<QCStatementInfo>,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        // Exempt if OCSP responder is present in AIA
        val hasOcsp = aiaInfo?.value?.ocspUri != null
        if (hasOcsp) return@CertificateConstraintEvaluation

        // Exempt if validity-assured short-term certificate QC statement is present
        val isValAssured = qcStatements.any {
            it.qcType == ETSI319412Part1.EXT_ETSI_VAL_ASSURED_ST_CERTS
        }
        if (isValAssured) return@CertificateConstraintEvaluation

        // Otherwise, CRLDP must be present with at least one valid URI
        if (crldp.isEmpty() || crldp.all { it.distributionPointUri.isNullOrBlank() }) {
            add(missingCrlDistributionPointsWhenNoOcsp)
        }
    }

    public fun evaluateCrlDistributionPoints(
        crldp: List<CrlDistributionPoint>,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        if (crldp.isEmpty() || crldp.all { it.distributionPointUri.isNullOrBlank() }) {
            add(missingCrlDistributionPoints)
        }
    }

    public fun evaluateQcStatementsForPolicy(
        policiesInfo: ExtensionInfo<List<String>>?,
        qcStatements: List<QCStatementInfo>,
        rules: (String) -> List<String>,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        val policies = policiesInfo?.value ?: emptyList()
        val requiredQcTypes = policies
            .flatMap { rules(it) }
            .toSet()

        for (requiredType in requiredQcTypes) {
            if (qcStatements.none { it.qcType == requiredType }) {
                add(certificateDoesNotContainRequiredQCStatement(requiredType, qcStatements))
            }
        }
    }

    public fun evaluatePublicKey(
        pkInfo: PublicKeyInfo,
        options: PublicKeyAlgorithmOptions,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        val compliant = options.algorithmOptions.any { req ->
            pkInfo.isAlgorithm(req.algorithm) &&
                (pkInfo.keySize == null || pkInfo.keySize >= req.minimumKeySize)
        }
        if (!compliant) {
            add(publicKeyNotCompliant(pkInfo, options))
        }
    }

    public fun evaluateValidityAssuredShortTerm(
        maxShortTermDuration: Duration = 7.days,
        validity: ValidityPeriod,
        qcStatements: List<QCStatementInfo>,
        hasNoRevAvail: Boolean,
    ): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
        val isValAssured = qcStatements.any {
            it.qcType == ETSI319412Part1.EXT_ETSI_VAL_ASSURED_ST_CERTS
        }
        if (!isValAssured) return@CertificateConstraintEvaluation

        // Check validity period (must be <= 7 days)
        val duration = validity.notAfter - validity.notBefore
        if (duration > maxShortTermDuration) {
            add(invalidValidityPeriodForValidityAssured(duration))
        }

        // Check noRevocationAvail (must be present)
        if (!hasNoRevAvail) {
            add(missingNoRevocationAvailForValidityAssured)
        }
    }

    public fun invalidValidityPeriodForValidityAssured(duration: kotlin.time.Duration): CertificateConstraintViolation =
        CertificateConstraintViolation(
            "Validity-assured certificate must be short-term (<= 7 days). Actual: $duration",
        )

    public val missingNoRevocationAvailForValidityAssured: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            "Validity-assured certificate must include noRevocationAvail extension (RFC 9608)",
        )

    public fun certificateTypeMismatch(expected: String, actual: String): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate type mismatch: expected $expected but was $actual",
        )

    public fun certificatePathLenExceedsMaximum(maxPathLen: Int, actualPathLen: Int): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "CA certificate pathLenConstraint ($actualPathLen) exceeds maximum allowed ($maxPathLen)",
        )

    public val certificateDoesNotContainAnyQCStatement: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate does not contain any QCStatement")

    public fun certificateDoesNotContainRequiredQCStatement(
        qcType: String,
        statements: List<QCStatementInfo>,
    ): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = buildString {
                val statementsStr = statements.joinToString { it.qcType }
                append("Certificate does not contain required QCStatement type '$qcType'.")
                append("Available: $statementsStr")
            },
        )

    public fun certificateNotMarkedCompliantForQCStatement(qcType: String): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate contains QCStatement type '$qcType' but it is not marked as compliant",
        )

    public val certificateDoesNotContainKeyUsage: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate does not contain keyUsage extension")

    public val caCertificateMissingPathLenConstraint: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            "CA certificate missing pathLenConstraint",
        )

    public fun certificateMissingKeyUsage(keyUsage: String): CertificateConstraintViolation = CertificateConstraintViolation(
        "Certificate keyUsage missing required bits: $keyUsage",
    )

    public val keyUsageNotMarkedCritical: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "KeyUsage extension must be marked critical per RFC 5280 clause 4.2",
        )

    public fun certificateDoesNotContainAnyPolicy(
        policies: Collection<String>,
        oids: Collection<String>,
    ): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = buildString {
                val policiesStr = policies.joinToString(", ")
                val oidsStr = oids.joinToString(", ")
                append("Certificate policies [$policiesStr] do not match any of the required policies: $oidsStr")
            },
        )

    public fun certificateDoesNotContainPolicies(
        oids: Collection<String>? = null,
    ): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = buildString {
                append("Certificate does not contain any certificate policies")
                if (!oids.isNullOrEmpty()) {
                    val oidsStr = oids.joinToString(", ")
                    append("Required one of: $oidsStr")
                }
            },

        )

    public val missingCertificatePoliciesExtension: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = buildString {
                append("Certificate does not contain certificatePolicies extension. ")
                append("Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present ")
                append("with at least one TSP-defined policy OID.")
            },
        )

    public val caIssuedCertificateMissingAiaExtension: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "CA-issued certificate missing Authority Information Access (AIA) extension",
        )

    public val aiaMissingIdAdCaIssuersAccessMethod: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "AIA extension missing id-ad-caIssuers access method (CA certificate URI)",
        )

    public val selfSignedCertificateNotAllowed: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Self-signed certificate not allowed. Certificate must be issued by a trusted CA.",
        )

    // Version violations
    public fun certificateVersionMismatch(expected: Int, actual: Int): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate version mismatch: expected v${expected + 1} but was v${actual + 1}",
        )

    // Serial number violations
    public val serialNumberNotPositive: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate serial number must be positive per RFC 5280",
        )

    // Subject/Issuer DN violations
    public val missingSubjectDistinguishedName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate subject DN is missing")

    public val missingIssuerDistinguishedName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate issuer DN is missing")

    public val subjectMissingCountryName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required countryName attribute (per ETSI EN 319 412-2/3)",
        )

    public val subjectMissingPersonalName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required personal name attribute (givenName, surname, or pseudonym per ETSI EN 319 412-2)",
        )

    public val subjectMissingCommonName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required commonName attribute",
        )

    public val subjectMissingOrganizationName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required organizationName attribute (per ETSI EN 319 412-3)",
        )

    public val subjectMissingOrganizationIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required organizationIdentifier attribute (per ETSI EN 319 412-3)",
        )

    public val subjectMissingSerialNumber: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required serialNumber attribute (per ETSI EN 319 412-2)",
        )

    public val subjectSerialNumberInvalidFormat: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN serialNumber has invalid format. Expected: XXXCC-identifier (per EN 319 412-1 clause 5.1.3)",
        )

    public val issuerMissingCountryName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required countryName attribute")

    public val issuerMissingOrganizationName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required organizationName attribute")

    public val issuerMissingCommonName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required commonName attribute")

    public val issuerMissingOrganizationIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Issuer DN missing required organizationIdentifier attribute (per ETSI EN 319 412-3)",
        )

    public val organizationIdentifierInvalidFormat: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "organizationIdentifier has invalid format. Expected: XXXCC-identifier (per EN 319 412-1 clause 5.1.4)",
        )

    // Subject Alternative Name violations
    public val missingSubjectAltName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate missing subjectAltName extension")

    // Authority Key Identifier violations
    public val missingAuthorityKeyIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing authorityKeyIdentifier extension (per ETSI EN 319 412-2)",
        )

    // CRL Distribution Points violations
    public val missingCrlDistributionPoints: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing CRL distribution points extension",
        )

    public val missingCrlDistributionPointsWhenNoOcsp: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing CRL distribution points (required when no OCSP responder available per ETSI EN 319 412-2)",
        )

    // Public Key violations
    public fun publicKeyNotCompliant(
        pkInfo: PublicKeyInfo,
        options: PublicKeyAlgorithmOptions,
    ): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = buildString {
                append("Public key (algorithm=${pkInfo.algorithm}, size=${pkInfo.keySize}) ")
                append("does not satisfy any of the required options: ")
                append(
                    options.algorithmOptions.joinToString(", ") {
                        "${it.algorithm} >= ${it.minimumKeySize} bits"
                    },
                )
            },
        )
}
