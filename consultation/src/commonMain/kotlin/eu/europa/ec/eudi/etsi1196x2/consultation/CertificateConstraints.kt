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

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Constraint 1: Validates the basicConstraints extension of a certificate.
 *
 * This constraint checks whether a certificate is a CA certificate or an end-entity certificate,
 * and optionally validates the path length constraint for CA certificates.
 *
 * @param CERT the type representing the certificate
 * @param expectedCa whether the certificate should be a CA (true) or end-entity (false)
 * @param pathLenConstraint optional expected path length constraint (only for CA certificates)
 * @param getBasicConstraints a function to extract basic constraints information from a certificate
 *
 * @see BasicConstraintsInfo
 */
public class BasicConstraintsConstraint<CERT : Any>(
    private val expectedCa: Boolean,
    private val pathLenConstraint: Int? = null,
    private val getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
) : CertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): ConstraintValidationResult {
        val info = getBasicConstraints(certificate)

        return when {
            info.isCa != expectedCa -> {
                val expectedType = if (expectedCa) "CA" else "end-entity"
                val actualType = if (info.isCa) "CA" else "end-entity"
                ConstraintValidationResult.Invalid(
                    "Certificate type mismatch: expected $expectedType but was $actualType",
                )
            }
            expectedCa && pathLenConstraint != null -> {
                val actualPathLen = info.pathLenConstraint
                when {
                    actualPathLen == null -> ConstraintValidationResult.Invalid(
                        "CA certificate missing pathLenConstraint",
                    )
                    actualPathLen > pathLenConstraint -> ConstraintValidationResult.Invalid(
                        "CA certificate pathLenConstraint ($actualPathLen) exceeds maximum allowed ($pathLenConstraint)",
                    )
                    else -> ConstraintValidationResult.Valid
                }
            }
            else -> ConstraintValidationResult.Valid
        }
    }

    public companion object {
        /**
         * Creates a constraint for end-entity certificates (cA=FALSE).
         */
        public fun <CERT : Any> requireEndEntity(
            getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        ): BasicConstraintsConstraint<CERT> = BasicConstraintsConstraint(
            expectedCa = false,
            getBasicConstraints = getBasicConstraints,
        )

        /**
         * Creates a constraint for CA certificates (cA=TRUE) with optional path length constraint.
         */
        public fun <CERT : Any> requireCa(
            maxPathLen: Int? = null,
            getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        ): BasicConstraintsConstraint<CERT> = BasicConstraintsConstraint(
            expectedCa = true,
            pathLenConstraint = maxPathLen,
            getBasicConstraints = getBasicConstraints,
        )
    }
}

/**
 * Constraint 2: Validates the QCStatement extension of a certificate (ETSI EN 319 412-5).
 *
 * This constraint checks whether a certificate contains the required QCStatement type,
 * which is mandatory for PID and Wallet Provider certificates according to ETSI TS 119 412-6.
 *
 * Optionally, it can also validate that the QCStatement is marked as compliant.
 *
 * @param CERT the type representing the certificate
 * @param requiredQcType the OID of the required QC type (e.g., "0.4.0.1949.1.1" for id-etsi-qct-pid)
 * @param requireCompliance whether to require the QCStatement to be marked as compliant
 * @param getQcStatements a function to extract QCStatement information from a certificate
 *
 * @see QCStatementInfo
 * @see [ETSI EN 319 412-5 - QCStatements](https://www.etsi.org/deliver/etsi_en/319400_319499/31941205/)
 */
public class QCStatementConstraint<CERT : Any>(
    private val requiredQcType: String,
    private val requireCompliance: Boolean = false,
    private val getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
) : CertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): ConstraintValidationResult {
        val statements = getQcStatements(certificate)

        return when {
            statements.isEmpty() -> ConstraintValidationResult.Invalid(
                "Certificate does not contain any QCStatements (required: $requiredQcType)",
            )
            statements.none { it.qcType == requiredQcType } -> {
                val availableTypes = statements.joinToString(", ") { it.qcType }
                ConstraintValidationResult.Invalid(
                    "Certificate does not contain required QCStatement type '$requiredQcType'. Available: [$availableTypes]",
                )
            }
            requireCompliance && statements.none { it.qcType == requiredQcType && it.qcCompliance } -> {
                ConstraintValidationResult.Invalid(
                    "Certificate contains QCStatement type '$requiredQcType' but it is not marked as compliant",
                )
            }
            else -> ConstraintValidationResult.Valid
        }
    }

    public companion object {
        /**
         * Creates a constraint for PID Provider certificates (id-etsi-qct-pid).
         * OID: 0.4.0.1949.1.1
         */
        public fun <CERT : Any> forPidProvider(
            getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        ): QCStatementConstraint<CERT> = QCStatementConstraint(
            requiredQcType = "0.4.0.1949.1.1",
            requireCompliance = false,
            getQcStatements = getQcStatements,
        )

        /**
         * Creates a constraint for Wallet Provider certificates (id-etsi-qct-wal).
         * OID: 0.4.0.1949.1.2
         */
        public fun <CERT : Any> forWalletProvider(
            getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        ): QCStatementConstraint<CERT> = QCStatementConstraint(
            requiredQcType = "0.4.0.1949.1.2",
            requireCompliance = false,
            getQcStatements = getQcStatements,
        )

        /**
         * Creates a constraint for PID Provider certificates with compliance check.
         * OID: 0.4.0.1949.1.1
         */
        public fun <CERT : Any> forPidProviderWithCompliance(
            getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        ): QCStatementConstraint<CERT> = QCStatementConstraint(
            requiredQcType = "0.4.0.1949.1.1",
            requireCompliance = true,
            getQcStatements = getQcStatements,
        )

        /**
         * Creates a constraint for Wallet Provider certificates with compliance check.
         * OID: 0.4.0.1949.1.2
         */
        public fun <CERT : Any> forWalletProviderWithCompliance(
            getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        ): QCStatementConstraint<CERT> = QCStatementConstraint(
            requiredQcType = "0.4.0.1949.1.2",
            requireCompliance = true,
            getQcStatements = getQcStatements,
        )
    }
}

/**
 * Constraint 3: Validates the keyUsage extension of a certificate (RFC 5280).
 *
 * This constraint checks whether a certificate has the required key usage bits set.
 *
 * @param CERT the type representing the certificate
 * @param requiredKeyUsage the required key usage bits
 * @param getKeyUsage a function to extract key usage information from a certificate
 *
 * @see KeyUsageBits
 */
public class KeyUsageConstraint<CERT : Any>(
    private val requiredKeyUsage: KeyUsageBits,
    private val getKeyUsage: suspend (CERT) -> KeyUsageBits?,
) : CertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): ConstraintValidationResult {
        val keyUsage = getKeyUsage(certificate)

        return when {
            keyUsage == null -> ConstraintValidationResult.Invalid(
                "Certificate does not contain keyUsage extension",
            )
            !hasRequiredBits(keyUsage, requiredKeyUsage) -> {
                val missing = getMissingBits(keyUsage, requiredKeyUsage)
                ConstraintValidationResult.Invalid(
                    "Certificate keyUsage missing required bits: $missing",
                )
            }
            else -> ConstraintValidationResult.Valid
        }
    }

    private fun hasRequiredBits(actual: KeyUsageBits, required: KeyUsageBits): Boolean {
        return (!required.digitalSignature || actual.digitalSignature) &&
            (!required.nonRepudiation || actual.nonRepudiation) &&
            (!required.keyEncipherment || actual.keyEncipherment) &&
            (!required.dataEncipherment || actual.dataEncipherment) &&
            (!required.keyAgreement || actual.keyAgreement) &&
            (!required.keyCertSign || actual.keyCertSign) &&
            (!required.crlSign || actual.crlSign) &&
            (!required.encipherOnly || actual.encipherOnly) &&
            (!required.decipherOnly || actual.decipherOnly)
    }

    private fun getMissingBits(actual: KeyUsageBits, required: KeyUsageBits): List<String> {
        val missing = mutableListOf<String>()
        if (required.digitalSignature && !actual.digitalSignature) missing.add("digitalSignature")
        if (required.nonRepudiation && !actual.nonRepudiation) missing.add("nonRepudiation")
        if (required.keyEncipherment && !actual.keyEncipherment) missing.add("keyEncipherment")
        if (required.dataEncipherment && !actual.dataEncipherment) missing.add("dataEncipherment")
        if (required.keyAgreement && !actual.keyAgreement) missing.add("keyAgreement")
        if (required.keyCertSign && !actual.keyCertSign) missing.add("keyCertSign")
        if (required.crlSign && !actual.crlSign) missing.add("crlSign")
        if (required.encipherOnly && !actual.encipherOnly) missing.add("encipherOnly")
        if (required.decipherOnly && !actual.decipherOnly) missing.add("decipherOnly")
        return missing
    }

    public companion object {
        /**
         * Creates a constraint requiring digitalSignature key usage.
         * Suitable for PID and Wallet Provider end-entity certificates.
         */
        public fun <CERT : Any> requireDigitalSignature(
            getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        ): KeyUsageConstraint<CERT> = KeyUsageConstraint(
            requiredKeyUsage = KeyUsageBits(digitalSignature = true),
            getKeyUsage = getKeyUsage,
        )

        /**
         * Creates a constraint requiring keyCertSign key usage.
         * Suitable for CA certificates (WRPAC/WRPRC Providers).
         */
        public fun <CERT : Any> requireKeyCertSign(
            getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        ): KeyUsageConstraint<CERT> = KeyUsageConstraint(
            requiredKeyUsage = KeyUsageBits(keyCertSign = true),
            getKeyUsage = getKeyUsage,
        )
    }
}

/**
 * Constraint 4: Validates the validity period of a certificate.
 *
 * This constraint checks whether a certificate is valid at a specific point in time.
 *
 * @param CERT the type representing the certificate
 * @param validationTime the time at which to validate (defaults to current time if null)
 * @param getValidityPeriod a function to extract validity period information from a certificate
 *
 * @see ValidityPeriod
 */
public class ValidityPeriodConstraint<CERT : Any>(
    private val validationTime: Instant? = null,
    private val getValidityPeriod: suspend (CERT) -> ValidityPeriod,
) : CertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): ConstraintValidationResult {
        val period = getValidityPeriod(certificate)
        val time = validationTime ?: Clock.System.now()

        return when {
            time < period.notBefore -> ConstraintValidationResult.Invalid(
                "Certificate is not yet valid. Valid from: ${period.notBefore}, current time: $time",
            )
            time > period.notAfter -> ConstraintValidationResult.Invalid(
                "Certificate has expired. Valid until: ${period.notAfter}, current time: $time",
            )
            else -> ConstraintValidationResult.Valid
        }
    }

    public companion object {
        /**
         * Creates a constraint that validates the certificate at the current time.
         */
        public fun <CERT : Any> validateAtCurrentTime(
            getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        ): ValidityPeriodConstraint<CERT> = ValidityPeriodConstraint(
            validationTime = null,
            getValidityPeriod = getValidityPeriod,
        )

        /**
         * Creates a constraint that validates the certificate at a specific time.
         */
        public fun <CERT : Any> validateAtTime(
            time: Instant,
            getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        ): ValidityPeriodConstraint<CERT> = ValidityPeriodConstraint(
            validationTime = time,
            getValidityPeriod = getValidityPeriod,
        )
    }
}

/**
 * Constraint 5: Validates the certificatePolicies extension of a certificate (RFC 5280).
 *
 * This constraint checks whether a certificate contains at least one of the required policy OIDs.
 *
 * @param CERT the type representing the certificate
 * @param requiredPolicyOids list of policy OIDs where at least one must be present
 * @param getCertificatePolicies a function to extract certificate policy OIDs from a certificate
 */
public class CertificatePolicyConstraint<CERT : Any>(
    private val requiredPolicyOids: List<String>,
    private val getCertificatePolicies: suspend (CERT) -> List<String>,
) : CertificateConstraint<CERT> {

    override suspend fun invoke(certificate: CERT): ConstraintValidationResult {
        val policies = getCertificatePolicies(certificate)

        return when {
            policies.isEmpty() -> ConstraintValidationResult.Invalid(
                "Certificate does not contain any certificate policies. Required one of: ${requiredPolicyOids.joinToString(", ")}",
            )
            policies.none { it in requiredPolicyOids } -> {
                val availablePolicies = policies.joinToString(", ")
                ConstraintValidationResult.Invalid(
                    "Certificate policies [$availablePolicies] do not match any of the required policies: ${requiredPolicyOids.joinToString(", ")}",
                )
            }
            else -> ConstraintValidationResult.Valid
        }
    }

    public companion object {
        /**
         * Creates a constraint requiring a specific policy OID.
         */
        public fun <CERT : Any> requirePolicy(
            policyOid: String,
            getCertificatePolicies: suspend (CERT) -> List<String>,
        ): CertificatePolicyConstraint<CERT> = CertificatePolicyConstraint(
            requiredPolicyOids = listOf(policyOid),
            getCertificatePolicies = getCertificatePolicies,
        )

        /**
         * Creates a constraint requiring one of multiple policy OIDs.
         */
        public fun <CERT : Any> requireAnyPolicy(
            policyOids: List<String>,
            getCertificatePolicies: suspend (CERT) -> List<String>,
        ): CertificatePolicyConstraint<CERT> = CertificatePolicyConstraint(
            requiredPolicyOids = policyOids,
            getCertificatePolicies = getCertificatePolicies,
        )
    }
}
