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

import kotlin.time.Clock
import kotlin.time.Instant

//
// Basic Constraints
//

/**
 * Requires the certificate to be an end-entity certificate (cA=FALSE).
 */
public fun ProfileBuilder.requireEndEntityCertificate() {
    basicConstraints { constraints ->
        CertificateConstraintEvaluation {
            if (constraints.isCa) {
                add(Violations.certificateTypeMismatch("end-entity", "CA"))
            }
        }
    }
}

/**
 * Requires the certificate to be a CA certificate (cA=TRUE) with the optional path length constraint.
 *
 * @param maxPathLen the maximum allowed pathLenConstraint (null means no limit)
 */
public fun ProfileBuilder.requireCaCertificate(maxPathLen: Int? = null) {
    basicConstraints { constraints ->
        CertificateConstraintEvaluation {
            if (!constraints.isCa) {
                add(Violations.certificateTypeMismatch("CA", "end-entity"))
            }
            if (constraints.isCa && maxPathLen != null) {
                val actualPathLen = constraints.pathLenConstraint
                when {
                    actualPathLen == null ->
                        add(Violations.caCertificateMissingPathLenConstraint)

                    actualPathLen > maxPathLen ->
                        add(Violations.certificatePathLenExceedsMaximum(maxPathLen, actualPathLen))
                }
            }
        }
    }
}

//
// QCStatements
//

/**
 * Requires the certificate to contain a specific QCStatement type.
 *
 * @param qcType the OID of the required QC type
 * @param requireCompliance whether to require the QCStatement to be marked as compliant
 */
public fun ProfileBuilder.requireQcStatement(
    qcType: String,
    requireCompliance: Boolean = false,
) {
    qcStatements(qcType) { statements ->
        CertificateConstraintEvaluation {
            when {
                statements.isEmpty() -> {
                    add(Violations.certificateDoesNotContainAnyQCStatement)
                }

                statements.none { it.qcType == qcType } -> {
                    add(Violations.certificateDoesNotContainRequiredQCStatement(qcType, statements))
                }

                requireCompliance && statements.none { it.qcType == qcType && it.qcCompliance } -> {
                    add(Violations.certificateNotMarkedCompliantForQCStatement(qcType))
                }
            }
        }
    }
}

//
// Key Usage
//

public fun ProfileBuilder.requireDigitalSignatureCritical() {
    requireKeyUsage("digitalSignature")
}

public fun ProfileBuilder.requireKeyCertSignCritical() {
    requireKeyUsage("keyCertSign")
}

/**
 * Requires the certificate to have the digitalSignature key usage bit set.
 * Per RFC 5280 section 4.2.1.3, the KeyUsage extension MUST be marked critical
 */
public fun ProfileBuilder.requireKeyUsage(
    requiredKeyUsage: String,
) {
    keyUsage { keyUsageAndCritical ->
        CertificateConstraintEvaluation {
            if (keyUsageAndCritical == null) {
                add(Violations.certificateDoesNotContainKeyUsage)
            } else {
                val (keyUsage, critical) = keyUsageAndCritical
                if (!critical) {
                    add(Violations.keyUsageNotMarkedCritical)
                }
                if (!keyUsage[requiredKeyUsage]) {
                    add(Violations.certificateMissingKeyUsage(requiredKeyUsage))
                }
            }
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

//
// Validity Period
//

/**
 * Requires the certificate to be valid at a specific time.
 *
 * @param time the time at which to validate (null means current time)
 */
public fun ProfileBuilder.requireValidAt(time: Instant? = null) {
    validity { period ->
        val validationTime = time ?: Clock.System.now()
        CertificateConstraintEvaluation {
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
    }
}

//
// Certificate Policies
//

/**
 * Requires the certificate to contain at least one of the specified policy OIDs.
 */
public fun ProfileBuilder.requirePolicy(vararg oids: String) {
    requirePolicy(oids.toSet())
}

public fun ProfileBuilder.requirePolicy(oids: Set<String>) {
    policies { policiesInfo ->
        CertificateConstraintEvaluation {
            if (policiesInfo == null) {
                add(Violations.certificateDoesNotContainPolicies(oids.toList()))
            } else {
                val policies = policiesInfo.value
                when {
                    policies.isEmpty() -> {
                        add(Violations.certificateDoesNotContainPolicies(oids.toList()))
                    }

                    policies.none { it in oids } -> {
                        add(Violations.certificateDoesNotContainAnyPolicy(policies, oids.toList()))
                    }
                }
            }
        }
    }
}

/**
 * Requires the certificate to contain the certificatePolicies extension (at least one policy).
 */
public fun ProfileBuilder.requirePolicyPresence() {
    policies { policiesInfo ->
        CertificateConstraintEvaluation {
            if (policiesInfo == null || policiesInfo.value.isEmpty()) {
                add(Violations.missingCertificatePoliciesExtension)
            }
        }
    }
}

//
// Authority Information Access (AIA)
//

/**
 * Requires CA-issued certificates (non-self-signed) to have an AIA extension
 * with the id-ad-caIssuers access method.
 *
 * Self-signed certificates (trust anchors) are exempt from this requirement.
 */
public fun ProfileBuilder.requireAiaForCaIssued() {
    combine(
        CertificateOperationsAlgebra.GetAia,
        CertificateOperationsAlgebra.CheckSelfSigned,
    ) { (aiaInfo, isSelfSigned) ->
        CertificateConstraintEvaluation {
            // Only check AIA for non-self-signed certificates
            if (!isSelfSigned) {
                if (aiaInfo == null) {
                    add(Violations.caIssuedCertificateMissingAiaExtension)
                } else if (aiaInfo.value.caIssuersUri == null) {
                    add(Violations.aiaMissingIdAdCaIssuersAccessMethod)
                }
            }
            // Self-signed certificates: no AIA check needed (pass silently)
        }
    }
}

/**
 * Requires the certificate to NOT be self-signed.
 *
 * This is useful for certificates that must be issued by a trusted CA
 * (e.g., WRPAC certificates issued by authorized WRPAC Providers).
 */
public fun ProfileBuilder.requireNoSelfSigned() {
    selfSigned { isSelfSigned ->
        CertificateConstraintEvaluation {
            if (isSelfSigned) {
                add(Violations.selfSignedCertificateNotAllowed)
            }
        }
    }
}

//
// Version Constraints
//

/**
 * Requires the certificate to be a specific X.509 version.
 *
 * @param expectedVersion the expected version (0=v1, 1=v2, 2=v3)
 */
public fun ProfileBuilder.requireVersion(expectedVersion: Int) {
    version { version ->
        CertificateConstraintEvaluation {
            if (version.value != expectedVersion) {
                add(Violations.certificateVersionMismatch(expectedVersion, version.value))
            }
        }
    }
}

/**
 * Requires the certificate to be X.509 version 3 (required for extensions).
 */
public fun ProfileBuilder.requireV3() {
    requireVersion(2)
}

//
// Serial Number Constraints
//

/**
 * Requires the certificate to have a positive serial number.
 *
 * Per RFC 5280 Section 4.1.2.2, serial numbers MUST be positive.
 * A positive serial number has its most significant bit set to 0.
 */
public fun ProfileBuilder.requirePositiveSerialNumber() {
    serialNumber { serialNumber ->
        CertificateConstraintEvaluation {
            // Serial number is already validated to be positive in the SerialNumber constructor
            // This check is redundant but kept for explicit validation
            if (serialNumber.value.isEmpty()) {
                add(Violations.serialNumberNotPositive)
            }
            // Check MSB is 0 (positive number)
            if (serialNumber.value[0].toInt() and 0x80 != 0) {
                add(Violations.serialNumberNotPositive)
            }
        }
    }
}

//
// Subject/Issuer DN Constraints
//

/**
 * Requires the subject DN to contain attributes for a natural person
 * per ETSI EN 319 412-2.
 *
 * Required attributes:
 * - countryName (C)
 * - givenName (G) or surname (SN) or pseudonym
 * - commonName (CN)
 * - serialNumber (optional attribute, not to be confused with certificate serial number)
 */
public fun ProfileBuilder.requireSubjectNaturalPersonAttributes() {
    subject { subject -> evaluateSubjectNaturalPersonAttributes(subject) }
}

public fun evaluateSubjectNaturalPersonAttributes(
    subject: DistinguishedName?,
): CertificateConstraintEvaluation =
    CertificateConstraintEvaluation {
        if (subject == null) {
            add(Violations.missingSubjectDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (subject.country.isNullOrBlank()) {
            add(Violations.subjectMissingCountryName)
        }

        // givenName, surname, or pseudonym is mandatory
        val hasName = !subject.givenName.isNullOrBlank() ||
            !subject.surname.isNullOrBlank() ||
            !subject.pseudonym.isNullOrBlank()
        if (!hasName) {
            add(Violations.subjectMissingPersonalName)
        }

        // commonName is mandatory
        if (subject.commonName.isNullOrBlank()) {
            add(Violations.subjectMissingCommonName)
        }

        // serialNumber is mandatory for natural persons
        if (subject.serialNumber.isNullOrBlank()) {
            add(Violations.subjectMissingSerialNumber)
        }
    }

public fun evaluateSubjectLegalPersonAttributes(
    subject: DistinguishedName?,
): CertificateConstraintEvaluation =
    CertificateConstraintEvaluation {
        if (subject == null) {
            add(Violations.missingSubjectDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (subject.country.isNullOrBlank()) {
            add(Violations.subjectMissingCountryName)
        }

        // organizationName is mandatory
        if (subject.organization.isNullOrBlank()) {
            add(Violations.subjectMissingOrganizationName)
        }

        // organizationIdentifier is mandatory
        val organizationIdentifier = subject.organizationIdentifier
        println("DEBUG: organizationIdentifier from property = '$organizationIdentifier'")
        println("DEBUG: all attributes = ${subject?.attributes}")
        if (organizationIdentifier.isNullOrBlank()) {
            add(Violations.subjectMissingOrganizationIdentifier)
        } else {
            // organizationIdentifier format validation (EN 319 412-1 clause 5.1.4)
            if (!ETSI319412Part1.isValidOrgId(organizationIdentifier)) {
                add(Violations.organizationIdentifierInvalidFormat)
            }
        }

        // commonName is mandatory
        if (subject.commonName.isNullOrBlank()) {
            add(Violations.subjectMissingCommonName)
        }
    }

/**
 * Evaluates the issuer DN to contain attributes for a legal person
 * per ETSI EN 319 412-3.
 *
 * Required attributes:
 * - countryName (C)
 * - organizationName (O)
 * - organizationIdentifier
 * - commonName (CN)
 *
 * This is used for WRPAC Provider CA certificates, which are always
 * operated by legal persons under eIDAS regulation.
 */
public fun evaluateIssuerLegalPersonAttributes(
    issuer: DistinguishedName?,
): CertificateConstraintEvaluation =
    CertificateConstraintEvaluation {
        if (issuer == null) {
            add(Violations.missingIssuerDistinguishedName)
            return@CertificateConstraintEvaluation
        }

        // countryName is mandatory
        if (issuer.country.isNullOrBlank()) {
            add(Violations.issuerMissingCountryName)
        }

        // organizationName is mandatory
        if (issuer.organization.isNullOrBlank()) {
            add(Violations.issuerMissingOrganizationName)
        }

        // organizationIdentifier is mandatory
        val organizationIdentifier = issuer.organizationIdentifier
        if (organizationIdentifier.isNullOrBlank()) {
            add(Violations.issuerMissingOrganizationIdentifier)
        } else {
            // organizationIdentifier format validation (EN 319 412-1 clause 5.1.4)
            if (!ETSI319412Part1.isValidOrgId(organizationIdentifier)) {
                add(Violations.organizationIdentifierInvalidFormat)
            }
        }

        // commonName is mandatory
        if (issuer.commonName.isNullOrBlank()) {
            add(Violations.issuerMissingCommonName)
        }
    }

/**
 * Requires the subject DN to contain attributes for a legal person
 * per ETSI EN 319 412-3.
 *
 * Required attributes:
 * - countryName (C)
 * - organizationName (O)
 * - organizationIdentifier
 * - commonName (CN)
 */
public fun ProfileBuilder.requireSubjectLegalPersonAttributes() {
    subject { subject -> evaluateSubjectLegalPersonAttributes(subject) }
}

/**
 * Requires the issuer DN to contain attributes for a legal person
 * per ETSI EN 319 412-3.
 *
 * Required attributes:
 * - countryName (C)
 * - organizationName (O)
 * - organizationIdentifier
 * - commonName (CN)
 *
 * This is used for WRPAC Provider CA certificates, which are always
 * operated by legal persons under eIDAS regulation.
 */
public fun ProfileBuilder.requireIssuerLegalPersonAttributes() {
    issuer { issuer -> evaluateIssuerLegalPersonAttributes(issuer) }
}

/**
 * Requires the issuer DN to contain appropriate attributes.
 *
 * @param requireCountryName whether countryName is required (default: true)
 * @param requireOrganizationName whether organizationName is required (default: true)
 * @param requireCommonName whether commonName is required (default: true)
 */
public fun ProfileBuilder.requireIssuerAttributes(
    requireCountryName: Boolean = true,
    requireOrganizationName: Boolean = true,
    requireCommonName: Boolean = true,
) {
    issuer { issuer ->
        CertificateConstraintEvaluation {
            if (issuer == null) {
                add(Violations.missingIssuerDistinguishedName)
                return@CertificateConstraintEvaluation
            }

            if (requireCountryName && issuer.country.isNullOrBlank()) {
                add(Violations.issuerMissingCountryName)
            }

            if (requireOrganizationName && issuer.organization.isNullOrBlank()) {
                add(Violations.issuerMissingOrganizationName)
            }

            if (requireCommonName && issuer.commonName.isNullOrBlank()) {
                add(Violations.issuerMissingCommonName)
            }
        }
    }
}

//
// Subject Alternative Name Constraints
//

/**
 * Requires the certificate to contain at least one Subject Alternative Name.
 */
public fun ProfileBuilder.requireSubjectAltName() {
    subjectAltNames { sanInfo ->
        CertificateConstraintEvaluation {
            if (sanInfo == null || sanInfo.value.isEmpty()) {
                add(Violations.missingSubjectAltName)
            }
        }
    }
}

//
// Authority Key Identifier Constraints
//

/**
 * Requires the certificate to contain an Authority Key Identifier extension.
 *
 * Per ETSI EN 319 412-2 clause 4.3.1, this extension shall be present.
 */
public fun ProfileBuilder.requireAuthorityKeyIdentifier() {
    authorityKeyIdentifier { aki ->
        CertificateConstraintEvaluation {
            if (aki == null) {
                add(Violations.missingAuthorityKeyIdentifier)
            }
        }
    }
}

/**
 * Requires CRL Distribution Points if the certificate does not have an OCSP responder
 * in AIA and is not a short-term certificate with validity-assured extension.
 *
 * Per ETSI EN 319 412-2 clause 4.3.11, CRLDP is conditionally required.
 */
public fun ProfileBuilder.requireCrlDistributionPointsIfNoOcspAndNotValAssured() {
    combine(
        CertificateOperationsAlgebra.GetCrlDistributionPoints,
        CertificateOperationsAlgebra.GetAia,
        CertificateOperationsAlgebra.GetAllQcStatements,
    ) { (crldp, aiaInfo, qcStatements) ->
        CertificateConstraintEvaluation {
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
                add(Violations.missingCrlDistributionPointsWhenNoOcsp)
            }
        }
    }
}

/**
 * Requires the certificate to contain CRL Distribution Points.
 */
public fun ProfileBuilder.requireCrlDistributionPoints() {
    crlDistributionPoints { crldp ->
        CertificateConstraintEvaluation {
            if (crldp.isEmpty() || crldp.all { it.distributionPointUri.isNullOrBlank() }) {
                add(Violations.missingCrlDistributionPoints)
            }
        }
    }
}

//
// QC Statement Policy Constraints
//

/**
 * Requires QC statements based on the certificate's actual policy OIDs.
 *
 * For each policy OID present in the certificate, [rules] is called to determine
 * which QC statement OIDs are required. The certificate passes only if all
 * required QC statements are present.
 *
 * Example:
 * ```kotlin
 * requireQcStatementsForPolicy { policyOid ->
 *     when (policyOid) {
 *         QCP_N -> listOf(ETSI319412.QC_COMPLIANCE, ETSI319412.QC_SSCD)
 *         QCP_L -> listOf(ETSI319412.QC_COMPLIANCE, ETSI319412.QC_SSCD, ETSI319412.QC_TYPE)
 *         else -> emptyList()
 *     }
 * }
 * ```
 *
 * @param rules a function mapping a policy OID to the list of required QC statement OIDs
 */
public fun ProfileBuilder.requireQcStatementsForPolicy(rules: (String) -> List<String>) {
    combine(
        CertificateOperationsAlgebra.GetPolicies,
        CertificateOperationsAlgebra.GetAllQcStatements,
    ) { (policiesInfo, qcStatements) ->
        CertificateConstraintEvaluation {
            val policies = policiesInfo?.value ?: emptyList()
            val requiredQcTypes = policies
                .flatMap { rules(it) }
                .toSet()

            for (requiredType in requiredQcTypes) {
                if (qcStatements.none { it.qcType == requiredType }) {
                    add(
                        Violations.certificateDoesNotContainRequiredQCStatement(
                            requiredType,
                            qcStatements,
                        ),
                    )
                }
            }
        }
    }
}

//
// Public Key Constraints
//

/**
 * Requires the certificate's public key to satisfy at least one of the given [options].
 *
 * Each [PublicKeyAlgorithmOptions.AlgorithmRequirement] specifies an algorithm name and a minimum key size.
 * The certificate passes if its key algorithm matches AND its key size >= minimumKeySize
 * for at least one requirement.
 *
 * @param options the set of acceptable algorithm/key-size combinations
 */
public fun ProfileBuilder.requirePublicKey(options: PublicKeyAlgorithmOptions) {
    subjectPublicKeyInfo { pkInfo ->
        CertificateConstraintEvaluation {
            val compliant = options.algorithmOptions.any { req ->
                pkInfo.isAlgorithm(req.algorithm) &&
                    (pkInfo.keySize == null || pkInfo.keySize >= req.minimumKeySize)
            }
            if (!compliant) {
                add(Violations.publicKeyNotCompliant(pkInfo, options))
            }
        }
    }
}

//
// Helpers
//

internal object Violations {
    fun certificateTypeMismatch(expected: String, actual: String): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate type mismatch: expected $expected but was $actual",
        )

    fun certificatePathLenExceedsMaximum(maxPathLen: Int, actualPathLen: Int): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "CA certificate pathLenConstraint ($actualPathLen) exceeds maximum allowed ($maxPathLen)",
        )

    val certificateDoesNotContainAnyQCStatement: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate does not contain any QCStatement")

    fun certificateDoesNotContainRequiredQCStatement(
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

    fun certificateNotMarkedCompliantForQCStatement(qcType: String): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate contains QCStatement type '$qcType' but it is not marked as compliant",
        )

    val certificateDoesNotContainKeyUsage: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate does not contain keyUsage extension")

    val caCertificateMissingPathLenConstraint: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            "CA certificate missing pathLenConstraint",
        )

    fun certificateMissingKeyUsage(keyUsage: String) = CertificateConstraintViolation(
        "Certificate keyUsage missing required bits: $keyUsage",
    )

    val keyUsageNotMarkedCritical: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "KeyUsage extension must be marked critical per RFC 5280 clause 4.2",
        )

    fun certificateDoesNotContainAnyPolicy(
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

    fun certificateDoesNotContainPolicies(
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

    val missingCertificatePoliciesExtension: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = buildString {
                append("Certificate does not contain certificatePolicies extension. ")
                append("Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present ")
                append("with at least one TSP-defined policy OID.")
            },
        )

    val caIssuedCertificateMissingAiaExtension: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "CA-issued certificate missing Authority Information Access (AIA) extension",
        )

    val aiaMissingIdAdCaIssuersAccessMethod: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "AIA extension missing id-ad-caIssuers access method (CA certificate URI)",
        )

    val selfSignedCertificateNotAllowed: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Self-signed certificate not allowed. Certificate must be issued by a trusted CA.",
        )

    // Version violations
    fun certificateVersionMismatch(expected: Int, actual: Int): CertificateConstraintViolation =
        CertificateConstraintViolation(
            reason = "Certificate version mismatch: expected v${expected + 1} but was v${actual + 1}",
        )

    // Serial number violations
    val serialNumberNotPositive: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate serial number must be positive per RFC 5280",
        )

    // Subject/Issuer DN violations
    val missingSubjectDistinguishedName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate subject DN is missing")

    val missingIssuerDistinguishedName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate issuer DN is missing")

    val subjectMissingCountryName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required countryName attribute (per ETSI EN 319 412-2/3)",
        )

    val subjectMissingPersonalName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required personal name attribute (givenName, surname, or pseudonym per ETSI EN 319 412-2)",
        )

    val subjectMissingCommonName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required commonName attribute",
        )

    val subjectMissingOrganizationName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required organizationName attribute (per ETSI EN 319 412-3)",
        )

    val subjectMissingOrganizationIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required organizationIdentifier attribute (per ETSI EN 319 412-3)",
        )

    val subjectMissingSerialNumber: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Subject DN missing required serialNumber attribute (per ETSI EN 319 412-2)",
        )

    val issuerMissingCountryName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required countryName attribute")

    val issuerMissingOrganizationName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required organizationName attribute")

    val issuerMissingCommonName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Issuer DN missing required commonName attribute")

    val issuerMissingOrganizationIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Issuer DN missing required organizationIdentifier attribute (per ETSI EN 319 412-3)",
        )

    val organizationIdentifierInvalidFormat: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "organizationIdentifier has invalid format. Expected: XXXCC-identifier (per EN 319 412-1 clause 5.1.4)",
        )

    // Subject Alternative Name violations
    val missingSubjectAltName: CertificateConstraintViolation
        get() = CertificateConstraintViolation(reason = "Certificate missing subjectAltName extension")

    // Authority Key Identifier violations
    val missingAuthorityKeyIdentifier: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing authorityKeyIdentifier extension (per ETSI EN 319 412-2)",
        )

    // CRL Distribution Points violations
    val missingCrlDistributionPoints: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing CRL distribution points extension",
        )

    val missingCrlDistributionPointsWhenNoOcsp: CertificateConstraintViolation
        get() = CertificateConstraintViolation(
            reason = "Certificate missing CRL distribution points (required when no OCSP responder available per ETSI EN 319 412-2)",
        )

    // Public Key violations
    fun publicKeyNotCompliant(
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
