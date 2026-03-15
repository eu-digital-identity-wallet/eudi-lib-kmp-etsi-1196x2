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
        evaluation {
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
        evaluation {
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
        evaluation {
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

/**
 * Requires the certificate to have the digitalSignature key usage bit set.
 */
public fun ProfileBuilder.requireDigitalSignature() {
    keyUsage { keyUsage ->
        evaluation {
            when {
                keyUsage == null ->
                    add(Violations.certificateDoesNotContainKeyUsage)

                !keyUsage.digitalSignature -> {
                    add(Violations.certificateMissingKeyUsage("digitalSignature"))
                }
            }
        }
    }
}

/**
 * Requires the certificate to have the keyCertSign key usage bit set.
 */
public fun ProfileBuilder.requireKeyCertSign() {
    keyUsage { keyUsage ->
        evaluation {
            when {
                keyUsage == null ->
                    add(Violations.certificateDoesNotContainKeyUsage)

                !keyUsage.keyCertSign ->
                    add(Violations.certificateMissingKeyUsage("keyCertSign"))
            }
        }
    }
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
        evaluation {
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
    policies { policies ->
        evaluation {
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

/**
 * Requires the certificate to contain the certificatePolicies extension (at least one policy).
 */
public fun ProfileBuilder.requirePolicyPresence() {
    policies { policies ->
        evaluation {
            if (policies.isEmpty()) {
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
    aiaWithSelfSigned { aiaWithSelfSigned ->
        evaluation {
            // Only check AIA for non-self-signed certificates
            if (!aiaWithSelfSigned.isSelfSigned) {
                val aia = aiaWithSelfSigned.aia
                if (aia == null) {
                    add(Violations.caIssuedCertificateMissingAiaExtension)
                } else if (aia.caIssuersUri == null) {
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
        evaluation {
            if (isSelfSigned) {
                add(Violations.selfSignedCertificateNotAllowed)
            }
        }
    }
}

//
// Helpers
//

private fun evaluation(builder: MutableList<CertificateConstraintViolation>.() -> Unit): CertificateConstraintEvaluation {
    val violations = buildList(builder)
    return CertificateConstraintEvaluation(violations)
}

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
}
