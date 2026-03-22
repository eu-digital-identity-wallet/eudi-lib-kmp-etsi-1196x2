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

import kotlin.time.Instant

//
// Basic Constraints
//

public fun ProfileBuilder.requireEndEntityCertificate() {
    basicConstraints { constraints -> CertificateConstraintsEvaluations.evaluateEndEntityCertificate(constraints) }
}

/**
 * Requires the certificate to be a CA certificate (cA=TRUE) with the optional path length constraint.
 *
 * @param maxPathLen the maximum allowed pathLenConstraint (null means no limit)
 */
public fun ProfileBuilder.requireCaCertificate(maxPathLen: Int? = null) {
    basicConstraints { constraints -> CertificateConstraintsEvaluations.evaluateCaCertificate(constraints, maxPathLen) }
}

//
// QCStatements
//

public fun ProfileBuilder.requireQcStatement(
    qcType: String,
    requireCompliance: Boolean = false,
) {
    qcStatements(qcType) { statements -> CertificateConstraintsEvaluations.evaluateQcStatement(statements, qcType, requireCompliance) }
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

public fun ProfileBuilder.requireKeyUsage(
    requiredKeyUsage: String,
) {
    keyUsage { keyUsageAndCritical -> CertificateConstraintsEvaluations.evaluateKeyUsage(keyUsageAndCritical, requiredKeyUsage) }
}

//
// Validity Period
//

public fun ProfileBuilder.requireValidAt(time: Instant? = null) {
    validity { period -> CertificateConstraintsEvaluations.evaluateValidAt(period, time) }
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
    policies { policiesInfo -> CertificateConstraintsEvaluations.evaluatePolicy(policiesInfo, oids) }
}

/**
 * Requires the certificate to contain the certificatePolicies extension (at least one policy).
 */
public fun ProfileBuilder.requirePolicyPresence() {
    policies { policiesInfo -> CertificateConstraintsEvaluations.evaluatePolicyPresence(policiesInfo) }
}

//
// Authority Information Access (AIA)
//

public fun ProfileBuilder.requireAiaForCaIssued() {
    combine(
        CertificateOperationsAlgebra.GetAia,
        CertificateOperationsAlgebra.CheckSelfSigned,
    ) { (aiaInfo, isSelfSigned) -> CertificateConstraintsEvaluations.evaluateAiaForCaIssued(aiaInfo, isSelfSigned) }
}

/**
 * Requires the certificate to NOT be self-signed.
 *
 * This is useful for certificates that must be issued by a trusted CA
 * (e.g., WRPAC certificates issued by authorized WRPAC Providers).
 */
public fun ProfileBuilder.requireNoSelfSigned() {
    selfSigned { isSelfSigned -> CertificateConstraintsEvaluations.evaluateNoSelfSigned(isSelfSigned) }
}

//
// Version Constraints
//

public fun ProfileBuilder.requireVersion(expectedVersion: Int) {
    version { version -> CertificateConstraintsEvaluations.evaluateVersion(version, expectedVersion) }
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

public fun ProfileBuilder.requirePositiveSerialNumber() {
    serialNumber { serialNumber -> CertificateConstraintsEvaluations.evaluatePositiveSerialNumber(serialNumber) }
}

//
// Subject/Issuer DN Constraints
//

public fun ProfileBuilder.requireSubjectNaturalPersonAttributes() {
    subject { subject -> CertificateConstraintsEvaluations.evaluateSubjectNaturalPersonAttributes(subject) }
}

public fun ProfileBuilder.requireSubjectLegalPersonAttributes() {
    subject { subject -> CertificateConstraintsEvaluations.evaluateSubjectLegalPersonAttributes(subject) }
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
    issuer { issuer -> CertificateConstraintsEvaluations.evaluateIssuerLegalPersonAttributes(issuer) }
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
        CertificateConstraintsEvaluations.evaluateIssuerAttributes(
            issuer,
            requireCountryName,
            requireOrganizationName,
            requireCommonName,
        )
    }
}

//
// Subject Alternative Name Constraints
//

public fun ProfileBuilder.requireSubjectAltName() {
    subjectAltNames { sanInfo -> CertificateConstraintsEvaluations.evaluateSubjectAltName(sanInfo) }
}

//
// Authority Key Identifier Constraints
//

public fun ProfileBuilder.requireAuthorityKeyIdentifier() {
    authorityKeyIdentifier { aki -> CertificateConstraintsEvaluations.evaluateAuthorityKeyIdentifier(aki) }
}

public fun ProfileBuilder.requireCrlDistributionPointsIfNoOcspAndNotValAssured() {
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

public fun ProfileBuilder.requirePublicKey(options: PublicKeyAlgorithmOptions) {
    subjectPublicKeyInfo { pkInfo -> CertificateConstraintsEvaluations.evaluatePublicKey(pkInfo, options) }
}
