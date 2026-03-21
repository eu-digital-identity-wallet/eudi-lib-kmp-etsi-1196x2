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

import eu.europa.ec.eudi.etsi119602.*
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.NCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.NCP_N_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.QCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411Part8.QCP_N_EUDIWRP
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

public val EUWRPACProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_WRPAC_PROVIDERS_LOTE),
            statusDeterminationApproach = ETSI19602.EU_WRPAC_PROVIDERS_STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(
                MultiLanguageURI.en(URIValue(ETSI19602.EU_WRPAC_PROVIDERS_SCHEME_COMMUNITY_RULES)),
            ),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = ETSI19602.EU_WRPAC_PROVIDERS_SVC_TYPE_ISSUANCE,
                revocation = ETSI19602.EU_WRPAC_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            serviceDigitalIdentityMustHaveCertificates = true,
            serviceStatuses = emptySet(),
            serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.CA,
        ),
    )

/**
 * Creates constraints for WRPAC Provider certificates (LoTE CA).
 *
 * @param at Instant for validity check (null = current time)
 * @param maxPathLen Optional maximum path length constraint for CA certificates.
 *                   Per RFC 5280 Section 4.2.1.9, pathLenConstraint specifies the maximum number
 *                   of non-self-issued intermediate certificates that may follow this certificate
 *                   in a valid certification path.
 *                   - `null` (default): No path length constraint enforced
 *                   - `0`: This CA can only issue end-entity certificates
 *                   - `1`: This CA can issue one intermediate CA certificate (recommended for most deployments)
 *                   - `2+`: This CA can issue multiple levels of intermediate CA certificates
 *
 * @return The certificate profile of WRP Access Certificate Provider
 * @see [ETSI TS 119 411-8]
 */
public fun wrpacProviderCertificateProfile(
    at: Instant? = null,
    maxPathLen: Int? = null,
): CertificateProfile =
    certificateProfile {
        requireCaCertificate(maxPathLen)
        requireKeyCertSign()
        requireValidAt(at)
        requirePolicyPresence()
    }

// TODO Add KDOC pointing to ETSI 119 411-8
public fun wrpAccessCertificateProfile(
    at: Instant? = null,
): CertificateProfile = certificateProfile {
    // Basic certificate requirements
    requireEndEntityCertificate()
    requireDigitalSignature()
    requireValidAt(at)
    requirePolicy(NCP_N_EUDIWRP, NCP_L_EUDIWRP, QCP_N_EUDIWRP, QCP_L_EUDIWRP)
    requireNoSelfSigned()

    // X.509 v3 required (for extensions)
    requireV3()

    // Serial number must be positive (RFC 5280)
    requirePositiveSerialNumber()

    // AIA required for CA-issued certificates
    requireAiaForCaIssued()

    // Authority Key Identifier required (EN 319 412-2)
    requireAuthorityKeyIdentifier()

    // Subject Alternative Name with contact info required (TS 119 411-8)
    requireSubjectAltNameForWRPAC()

    // CRL Distribution Points required if no OCSP (EN 319 412-2)
    requireCrlDistributionPointsIfNoOcspAndNotValAssured()

    // Public key requirements (TS 119 312)
    requirePublicKey(
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
    requireSubjectNameForWRPAC()
}

/**
 * Requires the certificate to contain a Subject Alternative Name with contact information.
 *
 * Per ETSI TS 119 411-8 clause 6.6.1, WRPAC certificates MUST contain contact information
 * in the subjectAltName extension (URI, email, or telephone).
 */
public fun ProfileBuilder.requireSubjectAltNameForWRPAC() {
    val missingSubjectAltName =
        CertificateConstraintViolation("Certificate missing subjectAltName extension")

    val subjectAltNameMissingContactInfo =
        CertificateConstraintViolation(
            "subjectAltName extension missing required contact information (URI, email, or telephone per ETSI TS 119 411-8)",
        )
    subjectAltNames { sanList ->
        evaluation {
            if (sanList.isEmpty()) {
                add(missingSubjectAltName)
                return@evaluation
            }

            val hasContactInfo = sanList.any { san ->
                san is SubjectAlternativeName.Uri ||
                    san is SubjectAlternativeName.Email ||
                    san is SubjectAlternativeName.Telephone
            }

            if (!hasContactInfo) {
                add(subjectAltNameMissingContactInfo)
            }
        }
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
internal fun ProfileBuilder.requireSubjectNameForWRPAC() {
    combine(
        CertificateOperationsAlgebra.GetPolicies,
        CertificateOperationsAlgebra.GetSubject,
    ) { (policies, subject) ->
        val isNaturalPerson = policies.any { it in listOf(NCP_N_EUDIWRP, QCP_N_EUDIWRP) }
        val isLegalPerson = policies.any { it in listOf(NCP_L_EUDIWRP, QCP_L_EUDIWRP) }
        when {
            isNaturalPerson -> evaluateSubjectNaturalPersonAttributes(subject)
            isLegalPerson -> evaluateSubjectLegalPersonAttributes(subject)
            else -> {
                // Not a concern of this rule to enforce policy OIDs
                CertificateConstraintEvaluation.Met
            }
        }
    }
}

internal fun evaluation(builder: MutableList<CertificateConstraintViolation>.() -> Unit): CertificateConstraintEvaluation {
    val violations = buildList(builder)
    return CertificateConstraintEvaluation(violations)
}
