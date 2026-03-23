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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

//
// Basic Constraints
//

public fun ProfileBuilder.endEntity() {
    basicConstraints { constraints -> CertificateConstraintsEvaluations.isEndEntity(constraints) }
}

/**
 * Requires the certificate to be a CA certificate (cA=TRUE) with the optional path length constraint.
 *
 * @param maxPathLen the maximum allowed pathLenConstraint (null means no limit)
 */
public fun ProfileBuilder.ca(maxPathLen: Int? = null) {
    basicConstraints { constraints -> CertificateConstraintsEvaluations.isCA(constraints, maxPathLen) }
}

//
// QCStatements
//

public fun ProfileBuilder.mandatoryQcStatement(
    qcType: String,
    requireCompliance: Boolean = false,
) {
    qcStatements(qcType) { statements -> CertificateConstraintsEvaluations.mandatoryQcStatement(statements, qcType, requireCompliance) }
}

//
// Key Usage
//

public fun ProfileBuilder.keyUsageDigitalSignature() {
    mandatoryKeyUsage("digitalSignature")
}

public fun ProfileBuilder.keyUsageCertSign() {
    mandatoryKeyUsage("keyCertSign")
}

public fun ProfileBuilder.mandatoryKeyUsage(
    requiredKeyUsage: String,
) {
    keyUsage { keyUsageAndCritical -> CertificateConstraintsEvaluations.mandatoryKeyUsage(keyUsageAndCritical, requiredKeyUsage) }
}

//
// Validity Period
//

public fun ProfileBuilder.validAt(time: Instant? = null) {
    validity { period -> CertificateConstraintsEvaluations.validAt(period, time) }
}

//
// Certificate Policies
//

/**
 * Requires the certificate to contain at least one of the specified policy OIDs.
 */
public fun ProfileBuilder.policyOneOf(vararg oids: String) {
    policyOneOf(oids.toSet())
}

public fun ProfileBuilder.policyOneOf(oids: Set<String>) {
    policies { policiesInfo -> CertificateConstraintsEvaluations.policyOneOf(policiesInfo, oids) }
}

/**
 * Requires the certificate to contain the certificatePolicies extension (at least one policy).
 */
public fun ProfileBuilder.policyIsPresent() {
    policies { policiesInfo -> CertificateConstraintsEvaluations.policyIsPresent(policiesInfo) }
}

//
// Authority Information Access (AIA)
//

public fun ProfileBuilder.authorityInformationAccessIfCAIssued() {
    combine(
        CertificateOperationsAlgebra.GetAia,
        CertificateOperationsAlgebra.CheckSelfSigned,
    ) { (aiaInfo, isSelfSigned) -> CertificateConstraintsEvaluations.aiaForCaIssued(aiaInfo, isSelfSigned) }
}

/**
 * Requires the certificate to NOT be self-signed.
 *
 * This is useful for certificates that must be issued by a trusted CA
 * (e.g., WRPAC certificates issued by authorized WRPAC Providers).
 */
public fun ProfileBuilder.notSelfSigned() {
    selfSigned { isSelfSigned -> CertificateConstraintsEvaluations.notSelfSigned(isSelfSigned) }
}

//
// Version Constraints
//

public fun ProfileBuilder.isVersion(expectedVersion: Int) {
    version { version -> CertificateConstraintsEvaluations.isVersion(version, expectedVersion) }
}

/**
 * Requires the certificate to be X.509 version 3 (required for extensions).
 */
public fun ProfileBuilder.version3() {
    isVersion(2)
}

//
// Serial Number Constraints
//

public fun ProfileBuilder.positiveSerialNumber() {
    serialNumber { serialNumber -> CertificateConstraintsEvaluations.positiveSerialNumber(serialNumber) }
}

//
// Subject/Issuer DN Constraints
//

public fun ProfileBuilder.subjectNaturalPerson() {
    subject { subject -> CertificateConstraintsEvaluations.naturalPersonDN("Subject", subject) }
}

public fun ProfileBuilder.subjectLegalPerson() {
    subject { subject -> CertificateConstraintsEvaluations.legalPersonDN("Subject", subject) }
}

public fun ProfileBuilder.issuerNaturalPerson() {
    issuer { issuer -> CertificateConstraintsEvaluations.naturalPersonDN("Issuer", issuer) }
}

public fun ProfileBuilder.issuerLegalPerson() {
    issuer { issuer -> CertificateConstraintsEvaluations.legalPersonDN("Issuer", issuer) }
}

//
// Subject Alternative Name Constraints
//

public fun ProfileBuilder.subjectAltName() {
    subjectAltNames { sanInfo -> CertificateConstraintsEvaluations.subjectAltName(sanInfo) }
}

//
// Authority Key Identifier Constraints
//

public fun ProfileBuilder.authorityKeyIdentifier() {
    authorityKeyIdentifier { aki -> CertificateConstraintsEvaluations.authorityKeyIdentifier(aki) }
}

public fun ProfileBuilder.subjectKeyIdentifier() {
    subjectKeyIdentifier { ski -> CertificateConstraintsEvaluations.subjectKeyIdentifier(ski) }
}

public fun ProfileBuilder.crlDistributionPointsIfNoOcspAndNotValAssured() {
    combine(
        CertificateOperationsAlgebra.GetCrlDistributionPoints,
        CertificateOperationsAlgebra.GetAia,
        CertificateOperationsAlgebra.GetAllQcStatements,
    ) { (crldp, aiaInfo, qcStatements) ->
        CertificateConstraintsEvaluations.evaluateCrlDistributionPointsIfNoOcspAndNotValAssured(
            crldp,
            aiaInfo,
            qcStatements,
        )
    }
}

/**
 * Requires the certificate to contain CRL Distribution Points.
 */
public fun ProfileBuilder.requireCrlDistributionPoints() {
    crlDistributionPoints { crldp -> CertificateConstraintsEvaluations.evaluateCrlDistributionPoints(crldp) }
}

//
// QC Statement Policy Constraints
//

public fun ProfileBuilder.requireQcStatementsForPolicy(rules: (String) -> List<String>) {
    combine(
        CertificateOperationsAlgebra.GetPolicies,
        CertificateOperationsAlgebra.GetAllQcStatements,
    ) { (policiesInfo, qcStatements) -> CertificateConstraintsEvaluations.evaluateQcStatementsForPolicy(policiesInfo, qcStatements, rules) }
}

//
// Public Key Constraints
//

public fun ProfileBuilder.publicKey(options: PublicKeyAlgorithmOptions) {
    subjectPublicKeyInfo { pkInfo -> CertificateConstraintsEvaluations.evaluatePublicKey(pkInfo, options) }
}

/**
 * Requires the certificate to follow short-term certificate requirements
 * if it contains the validity-assured extension.
 *
 * Requirements (ETSI EN 319 412-1 and RFC 9608):
 * - Validity period must be <= 7 days.
 * - Must contain noRevocationAvail extension (OID 2.5.29.56).
 */
public fun ProfileBuilder.validityAssuredShortTerm(maxShortTermDuration: Duration = 7.days) {
    combine(
        CertificateOperationsAlgebra.GetValidity,
        CertificateOperationsAlgebra.GetAllQcStatements,
        CertificateOperationsAlgebra.HasExtension(ETSI319412Part1.EXT_NO_REVOCATION_AVAIL),
        ::Triple,
    ) { (validity, qcStatements, hasNoRevAvail) ->
        CertificateConstraintsEvaluations.evaluateValidityAssuredShortTerm(
            maxShortTermDuration,
            validity,
            qcStatements,
            hasNoRevAvail,
        )
    }
}
