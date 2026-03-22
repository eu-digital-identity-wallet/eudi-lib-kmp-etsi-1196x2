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
 * @property evaluate the validation function applied to the extracted data
 */
public data class CertificateConstraint<T>(
    val op: CertificateOperationsAlgebra<T>,
    val evaluate: (T) -> CertificateConstraintEvaluation,
) {
    public companion object {

        public fun <A, B> combine(
            first: CertificateOperationsAlgebra<A>,
            second: CertificateOperationsAlgebra<B>,
            evaluate: (Pair<A, B>) -> CertificateConstraintEvaluation,
        ): CertificateConstraint<Pair<A, B>> =
            combine(first, second, ::Pair, evaluate)

        public fun <A, B, C> combine(
            first: CertificateOperationsAlgebra<A>,
            second: CertificateOperationsAlgebra<B>,
            third: CertificateOperationsAlgebra<C>,
            evaluate: (Triple<A, B, C>) -> CertificateConstraintEvaluation,
        ): CertificateConstraint<Triple<A, B, C>> =
            combine(first, second, third, ::Triple, evaluate)

        public fun <A, B, C> combine(
            first: CertificateOperationsAlgebra<A>,
            second: CertificateOperationsAlgebra<B>,
            combine: (A, B) -> C,
            evaluate: (C) -> CertificateConstraintEvaluation,
        ): CertificateConstraint<C> {
            val op = CertificateOperationsAlgebra.GetCombined(
                first,
                second,
                combine,
            )
            return CertificateConstraint(op, evaluate)
        }

        public fun <A, B, C, D> combine(
            first: CertificateOperationsAlgebra<A>,
            second: CertificateOperationsAlgebra<B>,
            third: CertificateOperationsAlgebra<C>,
            combine: (A, B, C) -> D,
            evaluate: (D) -> CertificateConstraintEvaluation,
        ): CertificateConstraint<D> {
            val op1 = CertificateOperationsAlgebra.GetCombined(
                first,
                second,
                ::Pair,
            )
            return combine(
                op1,
                third,
                { (a, b), c -> combine(a, b, c) },
                evaluate,
            )
        }
    }
}

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

        public operator fun invoke(builder: MutableList<CertificateConstraintViolation>.() -> Unit): CertificateConstraintEvaluation {
            val violations = buildList(builder)
            return CertificateConstraintEvaluation(violations)
        }
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
        evaluate: (BasicConstraintsInfo) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetBasicConstraints, evaluate)
    }

    /**
     * Defines a constraint on the key usage extension.
     */
    public fun keyUsage(
        evaluate: (ExtensionInfo<KeyUsageBits>?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetKeyUsage, evaluate)
    }

    /**
     * Defines a constraint on the validity period.
     */
    public fun validity(
        evaluate: (ValidityPeriod) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetValidity, evaluate)
    }

    /**
     * Defines a constraint on the certificate policies.
     */
    public fun policies(
        evaluate: (ExtensionInfo<List<String>>?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetPolicies, evaluate)
    }

    /**
     * Defines a constraint on whether the certificate is self-signed.
     */
    public fun selfSigned(
        evaluate: (Boolean) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.CheckSelfSigned, evaluate)
    }

    /**
     * Defines a constraint on the AIA extension.
     */
    public fun aia(
        evaluate: (ExtensionInfo<AuthorityInformationAccess>?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetAia, evaluate)
    }

    /**
     * Defines a constraint on QCStatements of a specific type.
     *
     * @param qcType the OID of the QC type to extract
     */
    public fun qcStatements(
        qcType: String,
        evaluate: (List<QCStatementInfo>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetQcStatements(qcType), evaluate)
    }

    /**
     * Defines a constraint on the subject Distinguished Name.
     */
    public fun subject(
        evaluate: (DistinguishedName?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubject, evaluate)
    }

    /**
     * Defines a constraint on the issuer Distinguished Name.
     */
    public fun issuer(
        evaluate: (DistinguishedName?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetIssuer, evaluate)
    }

    /**
     * Defines a constraint on Subject Alternative Names.
     */
    public fun subjectAltNames(
        evaluate: (ExtensionInfo<List<SubjectAlternativeName>>?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubjectAltNames, evaluate)
    }

    /**
     * Defines a constraint on CRL Distribution Points.
     */
    public fun crlDistributionPoints(
        evaluate: (List<CrlDistributionPoint>) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetCrlDistributionPoints, evaluate)
    }

    /**
     * Defines a constraint on the Authority Key Identifier extension.
     */
    public fun authorityKeyIdentifier(
        evaluate: (AuthorityKeyIdentifier?) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetAuthorityKeyIdentifier, evaluate)
    }

    /**
     * Defines a constraint on the certificate serial number.
     */
    public fun serialNumber(
        evaluate: (SerialNumber) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSerialNumber, evaluate)
    }

    /**
     * Defines a constraint on the certificate version.
     */
    public fun version(
        evaluate: (Version) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetVersion, evaluate)
    }

    /**
     * Defines a constraint on the subject public key information.
     */
    public fun subjectPublicKeyInfo(
        check: (PublicKeyInfo) -> CertificateConstraintEvaluation,
    ) {
        requirements += CertificateConstraint(CertificateOperationsAlgebra.GetSubjectPublicKeyInfo, check)
    }

    public fun <A, B> combine(
        first: CertificateOperationsAlgebra<A>,
        second: CertificateOperationsAlgebra<B>,
        evaluate: (Pair<A, B>) -> CertificateConstraintEvaluation,
    ) {
        requirements +=
            CertificateConstraint.combine(first, second, evaluate)
    }

    public fun <A, B, C> combine(
        first: CertificateOperationsAlgebra<A>,
        second: CertificateOperationsAlgebra<B>,
        third: CertificateOperationsAlgebra<C>,
        evaluate: (Triple<A, B, C>) -> CertificateConstraintEvaluation,
    ) {
        requirements +=
            CertificateConstraint.combine(first, second, third, evaluate)
    }

    public fun <A, B, C> combine(
        first: CertificateOperationsAlgebra<A>,
        second: CertificateOperationsAlgebra<B>,
        combine: (A, B) -> C,
        evaluate: (C) -> CertificateConstraintEvaluation,
    ) {
        requirements +=
            CertificateConstraint.combine(first, second, combine, evaluate)
    }

    public fun <A, B, C, D> combine(
        first: CertificateOperationsAlgebra<A>,
        second: CertificateOperationsAlgebra<B>,
        third: CertificateOperationsAlgebra<C>,
        combine: (A, B, C) -> D,
        evaluate: (D) -> CertificateConstraintEvaluation,
    ) {
        requirements +=
            CertificateConstraint.combine(first, second, third, combine, evaluate)
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
