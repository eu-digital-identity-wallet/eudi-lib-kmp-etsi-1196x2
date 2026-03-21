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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A constraint that validates extracted certificate data.
 *
 * Combines a certificate operation with a validation function that evaluates
 * the extracted data and returns the result.
 *
 * @param T the type of data extracted by the operation
 * @property op the operation to perform on the certificate
 * @property validate the validation function applied to the extracted data
 */
public data class CertificateConstraint<T>(
    val op: CertificateOperationsAlgebra<T>,
    val validate: (T) -> CertificateConstraintEvaluation,
)

/**
 * The constraint is not satisfied.
 *
 * @param reason a human-readable description of why the constraint failed
 */
public data class CertificateConstraintViolation(val reason: String)

/**
 * Result of validating a certificate constraint.
 */
public sealed interface CertificateConstraintEvaluation {
    /**
     * The constraint is satisfied.
     */
    public data object Met : CertificateConstraintEvaluation

    /**
     * The constraint is not satisfied.
     */
    public data class Violated(public val violations: List<CertificateConstraintViolation>) :
        CertificateConstraintEvaluation {
        init {
            require(violations.isNotEmpty()) { "At least one violation is required" }
        }
    }

    public companion object {
        public operator fun invoke(violations: List<CertificateConstraintViolation>): CertificateConstraintEvaluation =
            if (violations.isEmpty()) Met else Violated(violations)
    }
}

@OptIn(ExperimentalContracts::class)
public fun CertificateConstraintEvaluation.isMet(): Boolean {
    contract {
        returns(true) implies (this@isMet is CertificateConstraintEvaluation.Met)
        returns(false) implies (this@isMet is CertificateConstraintEvaluation.Violated)
    }
    return this is CertificateConstraintEvaluation.Met
}

/**
 * An immutable collection of certificate constraints that define a certificate profile.
 *
 * A certificate profile specifies all the requirements that a certificate must satisfy.
 *
 * @property requirements the list of constraints that must be validated
 */
public data class CertificateProfile(val requirements: List<CertificateConstraint<*>>)

/**
 * A DSL builder for creating certificate profiles.
 *
 * Provides a fluent API for defining certificate constraints using
 * type-safe builder functions.
 */
@CertificateProfileDsl
public class ProfileBuilder {
    private val requirements: MutableList<CertificateConstraint<*>> = mutableListOf()

    /**
     * Defines a constraint on the basic constraints extension.
     */
    public fun basicConstraints(
        check: (BasicConstraintsInfo) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetBasicConstraints, check)
    }

    /**
     * Defines a constraint on the key usage extension.
     */
    public fun keyUsage(
        check: (KeyUsageBits?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetKeyUsage, check)
    }

    /**
     * Defines a constraint on the validity period.
     */
    public fun validity(
        check: (ValidityPeriod) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetValidity, check)
    }

    /**
     * Defines a constraint on the certificate policies.
     */
    public fun policies(
        check: (List<String>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetPolicies, check)
    }

    /**
     * Defines a constraint on whether the certificate is self-signed.
     */
    public fun selfSigned(
        check: (Boolean) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.CheckSelfSigned, check)
    }

    /**
     * Defines a constraint on the AIA extension.
     */
    public fun aia(
        check: (AuthorityInformationAccess?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetAia, check)
    }

    /**
     * Defines a constraint on the AIA extension combined with self-signed status.
     *
     * This is useful for validating AIA requirements that only apply to CA-issued
     * (non-self-signed) certificates.
     */
    public fun aiaWithSelfSigned(
        check: (AiaWithSelfSigned) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(
            CertificateOperationsAlgebra.GetCombined(
                CertificateOperationsAlgebra.GetAia,
                CertificateOperationsAlgebra.CheckSelfSigned,
            ),
        ) { (aia, isSelfSigned) -> check(AiaWithSelfSigned(isSelfSigned = isSelfSigned, aia = aia)) }
    }

    /**
     * Defines a constraint that has access to both certificate policies and all QC statements.
     *
     * Used for policy-conditional QC statement validation where the required QC statements
     * depend on the actual policy OIDs present in the certificate.
     */
    public fun policiesWithQcStatements(
        check: (Pair<List<String>, List<QCStatementInfo>>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(
            CertificateOperationsAlgebra.GetCombined(
                CertificateOperationsAlgebra.GetPolicies,
                CertificateOperationsAlgebra.GetAllQcStatements,
            ),
            check,
        )
    }

    /**
     * Defines a constraint on QCStatements of a specific type.
     *
     * @param qcType the OID of the QC type to extract
     */
    public fun qcStatements(
        qcType: String,
        check: (List<QCStatementInfo>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetQcStatements(qcType), check)
    }

    /**
     * Defines a constraint on the subject Distinguished Name.
     */
    public fun subject(
        check: (DistinguishedName?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubject, check)
    }

    /**
     * Defines a constraint on the issuer Distinguished Name.
     */
    public fun issuer(
        check: (DistinguishedName?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetIssuer, check)
    }

    /**
     * Defines a constraint on Subject Alternative Names.
     */
    public fun subjectAltNames(
        check: (List<SubjectAlternativeName>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubjectAltNames, check)
    }

    /**
     * Defines a constraint on CRL Distribution Points.
     */
    public fun crlDistributionPoints(
        check: (List<CrlDistributionPoint>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetCrlDistributionPoints, check)
    }

    /**
     * Defines a constraint on the Authority Key Identifier extension.
     */
    public fun authorityKeyIdentifier(
        check: (AuthorityKeyIdentifier?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetAuthorityKeyIdentifier, check)
    }

    /**
     * Defines a constraint on the certificate serial number.
     */
    public fun serialNumber(
        check: (SerialNumber) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSerialNumber, check)
    }

    /**
     * Defines a constraint on the certificate version.
     */
    public fun version(
        check: (Version) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetVersion, check)
    }

    /**
     * Defines a constraint on the subject public key information.
     */
    public fun subjectPublicKeyInfo(
        check: (PublicKeyInfo) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubjectPublicKeyInfo, check)
    }

    /**
     * Builds the certificate profile from the accumulated requirements.
     */
    public fun toProfile(): CertificateProfile =
        CertificateProfile(requirements.toList())
}

/**
 * DSL marker for certificate profile builder.
 */
@DslMarker
public annotation class CertificateProfileDsl

/**
 * Creates a certificate profile using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val profile = certificateProfile {
 *     basicConstraints { constraints ->
 *         if (constraints.isCa) CertificateConstraintEvaluation.Met
 *         else CertificateConstraintEvaluation.Violated(...)
 *     }
 *     keyUsage { keyUsage ->
 *         // validate key usage
 *     }
 * }
 * ```
 */
public fun certificateProfile(builder: ProfileBuilder.() -> Unit): CertificateProfile =
    ProfileBuilder().apply(builder).toProfile()
