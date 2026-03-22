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
    subjectAltNameForWRPAC()

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
            QCP_N_EUDIWRP -> listOf(ETSI319412.QC_COMPLIANCE, ETSI319412.QC_SSCD)
            QCP_L_EUDIWRP -> listOf(ETSI319412.QC_COMPLIANCE, ETSI319412.QC_SSCD, ETSI319412.QC_TYPE)
            else -> emptyList()
        }
    }

    // Subject DN attributes required based on certificate policy (natural person vs legal person)
    subjectNameForWRPAC()

    // Issuer DN attributes required (WRPAC Provider CA is always a legal person)
    issuerLegalPersonAttributes()
}

internal fun ProfileBuilder.subjectAltNameForWRPAC() =
    subjectAltNames { subjectAltNames ->
        validateSubjectAltNameForWRPAC(subjectAltNames)
    }

internal fun ProfileBuilder.subjectNameForWRPAC() =
    combine(
        CertificateOperationsAlgebra.GetPolicies,
        CertificateOperationsAlgebra.GetSubject,
    ) { (policies, subject) ->
        validateSubjectNameForWRPAC(policies, subject)
    }

/**
 * Requires the certificate to contain a Subject Alternative Name with contact information.
 *
 * Per ETSI TS 119 411-8 clause 6.6.1, WRPAC certificates MUST contain contact information
 * in the subjectAltName extension (URI, email, or telephone).
 */
internal fun validateSubjectAltNameForWRPAC(
    subjectAltNames: ExtensionInfo<List<SubjectAlternativeName>>?,
): CertificateConstraintEvaluation =
    CertificateConstraintEvaluation {
        if (subjectAltNames == null || subjectAltNames.value.isEmpty()) {
            val missingSubjectAltName =
                CertificateConstraintViolation("Certificate missing subjectAltName extension")
            add(missingSubjectAltName)
            return@CertificateConstraintEvaluation
        }

        val hasContactInfo = subjectAltNames.value.any { san ->
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
 * The certificate policy OID determines which set of attributes is required.
 */
internal fun validateSubjectNameForWRPAC(
    policies: ExtensionInfo<List<String>>?,
    subject: DistinguishedName?,
): CertificateConstraintEvaluation {
    if (policies == null || policies.value.isEmpty()) return CertificateConstraintEvaluation.Met

    val isNaturalPerson = policies.value.any { it in listOf(NCP_N_EUDIWRP, QCP_N_EUDIWRP) }
    val isLegalPerson = policies.value.any { it in listOf(NCP_L_EUDIWRP, QCP_L_EUDIWRP) }
    return when {
        isNaturalPerson && !isLegalPerson ->
            CertificateConstraintsEvaluations.subjectNaturalPersonAttributes(subject)
        isLegalPerson && !isNaturalPerson ->
            CertificateConstraintsEvaluations.validSubjectLegalPersonAttributes(subject)
        else -> {
            // Not a concern of this rule to enforce policy OIDs
            CertificateConstraintEvaluation.Met
        }
    }
}
