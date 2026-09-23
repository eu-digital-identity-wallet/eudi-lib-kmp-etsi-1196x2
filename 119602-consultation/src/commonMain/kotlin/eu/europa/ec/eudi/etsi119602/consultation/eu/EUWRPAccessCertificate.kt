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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.NCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.NCP_N_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.QCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.QCP_N_EUDIWRP
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

/**
 * Wallet Relying Party Access Certificate Profile
 *
 * ETSI 119 411-8
 */
public fun wrpAccessCertificateProfile(
    at: Instant? = null,
): CertificateProfile = certificateProfile {
    // Basic certificate requirements
    endEntity()
    keyUsageDigitalSignature()
    wrpacExplicitExtensionCriticality()
    validAt(at)
    policyOneOf(NCP_N_EUDIWRP, NCP_L_EUDIWRP, QCP_N_EUDIWRP, QCP_L_EUDIWRP)
    notSelfSigned()

    // X.509 v3 required (for extensions)
    version3()

    // Serial number must be positive (RFC 5280)
    positiveSerialNumber()

    // AIA required for CA-issued certificates
    authorityInformationAccessIfCAIssued()

    // Authority Key Identifier required (EN 319 412-2)
    authorityKeyIdentifier()

    // Subject Alternative Name with contact info required (TS 119 411-8)
    wrpacSubjectAlternativeNames()

    // WRPAC must NOT be a validity-assured short-term certificate
    // (ETSI TS 119 411-8, GEN-6.6.1-01 note)
    wrpacMustNotBeValidityAssuredShortTerm()

    // CRL Distribution Points required if no OCSP (EN 319 412-2)
    crlDistributionPointsIfNoOcspAndNotValAssured()

    // Public key requirements (TS 119 312)
    publicKey(
        options = PublicKeyAlgorithmOptions.of(
            PublicKeyAlgorithmOptions.AlgorithmRequirement.RSA_2048,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.EC_256,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.ECDSA_256,
        ),
    )

    // QCStatements required based on the certificate's ACTUAL policy (EN 319 412-5).
    // NCP_N and NCP_L do not require QC statements; QCP_N and QCP_L do.
    requireQcStatementsForPolicy { policyOid ->
        when (policyOid) {
            QCP_N_EUDIWRP -> listOf(
                QCStatementInfo.OtherQcStatement(ETSI319412.QC_COMPLIANCE),
                QCStatementInfo.OtherQcStatement(ETSI319412.QC_SSCD),
            )
            QCP_L_EUDIWRP -> listOf(
                QCStatementInfo.OtherQcStatement(ETSI319412.QC_COMPLIANCE),
                QCStatementInfo.OtherQcStatement(ETSI319412.QC_SSCD),
                QCStatementInfo.QcType(ETSI319412.ID_ETSI_QCT_ESEAL),
            )
            else -> emptyList()
        }
    }

    // Subject DN attributes required based on certificate policy (natural person vs legal person)
    wrpacSubject()

    // Issuer DN attributes required (WRPAC Provider CA is always a legal person)
    issuerLegalPerson()
}

/**
 * EN 319 412-1 GEN-4.1-2
 */
internal fun ProfileBuilder.wrpacExplicitExtensionCriticality() {
    fun basicConstraintOrKeyUsage(oid: String) =
        oid == RFC5280.EXT_BASIC_CONSTRAINTS || oid == RFC5280.EXT_KEY_USAGE
    extensionCriticality(mustBeCritical = true) { oid ->
        basicConstraintOrKeyUsage(oid)
    }
    extensionCriticality(mustBeCritical = false) { oid ->
        !basicConstraintOrKeyUsage(oid)
    }
}
internal fun ProfileBuilder.wrpacSubjectAlternativeNames() =
    subjectAltNames { subjectAltNames ->
        validateSubjectAltNameForWRPAC(subjectAltNames)
    }

/**
 * WRPAC must not be a validity-assured short-term certificate.
 *
 * Per ETSI TS 119 411-8, GEN-6.6.1-01 note: "Neither website authentication
 * certificates nor short-term certificates (validity assured) are applicable
 * to wallet-relying party access certificates." A certificate is considered
 * validity-assured short-term when it carries the ext-etsi-valassured-ST-certs
 * extension (ETSI EN 319 412-1 clause 5.2).
 */
internal fun ProfileBuilder.wrpacMustNotBeValidityAssuredShortTerm() {
    hasExtension(ETSI319412Part1.EXT_ETSI_VAL_ASSURED_ST_CERTS) { hasValAssured ->
        if (hasValAssured) {
            CertificateConstraintEvaluation {
                add(
                    CertificateConstraintViolation(
                        "WRPAC must not be a validity-assured short-term certificate (ETSI TS 119 411-8, GEN-6.6.1-01)",
                    ),
                )
            }
        } else {
            CertificateConstraintEvaluation.Met
        }
    }
}

internal fun ProfileBuilder.wrpacSubject() =
    combine(
        CertificateOperationsAlgebra.GetPolicies,
        CertificateOperationsAlgebra.GetSubject,
    ) { (policies, subject) ->
        validateSubjectForWRPAC(policies, subject)
    }

/**
 * Requires the certificate to contain a Subject Alternative Name with contact information.
 *
 * Per ETSI TS 119 411-8 clause 6.6.1, WRPAC certificates MUST contain contact information
 * in the subjectAltName extension (URI, email, or telephone).
 */
internal fun validateSubjectAltNameForWRPAC(
    subjectAltNames: List<SubjectAlternativeName>?,
): CertificateConstraintEvaluation =
    CertificateConstraintEvaluation {
        if (subjectAltNames.isNullOrEmpty()) {
            val missingSubjectAltName =
                CertificateConstraintViolation("Certificate missing subjectAltName extension")
            add(missingSubjectAltName)
            return@CertificateConstraintEvaluation
        }

        val hasContactInfo = subjectAltNames.any { san ->
            san is SubjectAlternativeName.Uri ||
                san is SubjectAlternativeName.Email ||
                san is SubjectAlternativeName.Telephone
        }

        if (!hasContactInfo) {
            val subjectAltNameMissingContactInfo =
                CertificateConstraintViolation(
                    "subjectAltName extension missing required contact information (URI, email, or telephone per ETSI TS 119 411-8)",
                )
            add(subjectAltNameMissingContactInfo)
        }
    }

/**
 * Requires the subject DN attributes based on the certificate policy (natural person vs legal person).
 *
 * Per ETSI TS 119 411-8 and ETSI EN 319 412-2/3:
 * - Natural person certificates (NCP-n, QCP-n) MUST contain: countryName, givenName/surname/pseudonym,
 *   commonName, and serialNumber
 * - Legal person certificates (NCP-l, QCP-l) MUST contain: countryName, organizationName,
 *   organizationIdentifier, and commonName
 *
 * The subject is validated against the DN rules of every person-type policy present. Per
 * GEN-6.6.1-03 a certificate may carry more than one policy identifier, so when both a
 * natural-person and a legal-person policy are present the subject must satisfy both sets of
 * requirements.
 */
internal fun validateSubjectForWRPAC(
    policies: List<String>?,
    subject: DistinguishedName?,
): CertificateConstraintEvaluation {
    if (policies.isNullOrEmpty()) return CertificateConstraintEvaluation.Met

    val isNaturalPerson = policies.any { it in listOf(NCP_N_EUDIWRP, QCP_N_EUDIWRP) }
    val isLegalPerson = policies.any { it in listOf(NCP_L_EUDIWRP, QCP_L_EUDIWRP) }
    if (!isNaturalPerson && !isLegalPerson) {
        // No WRPAC policy present; policy OIDs are enforced by policyOneOf()
        return CertificateConstraintEvaluation.Met
    }

    val legalPersonEvaluation =
        if (isLegalPerson) CertificateConstraintsEvaluations.legalPersonDN("Subject", subject) else CertificateConstraintEvaluation.Met

    val naturalPersonEvaluation =
        if (isNaturalPerson) CertificateConstraintsEvaluations.naturalPersonDN("Subject", subject) else CertificateConstraintEvaluation.Met

    return legalPersonEvaluation + naturalPersonEvaluation
}
