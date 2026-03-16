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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411.NCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411.NCP_N_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411.QCP_L_EUDIWRP
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411.QCP_N_EUDIWRP
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

/**
 * Creates constraints for Wallet Relying Party Access Certificate (issued to Wallet Relying Parties).
 *
 * Per ETSI TS 119 411-8 Clause 6.6.1:
 * - Certificate type: End-entity (cA=FALSE)
 * - Key Usage: digitalSignature REQUIRED (for electronic signatures/seals)
 * - Validity: Must be valid at validation time
 * - Certificate Policy: MUST include one of the four policy OIDs from Clause 5.3
 *
 * Note: ETSI TS 119 411-8 does NOT specify a WRPAC-specific QCStatement.
 * For qualified certificates (QCP-n-eudiwrp, QCP-l-eudiwrp), the general
 * QCStatement requirements from ETSI EN 319 412-5 apply (e.g., QcCompliance, QcType),
 * but these are not WRPAC-specific and are not validated by this profile.
 *
 * Certificate Policy OIDs (ETSI TS 119 411-8 Clause 5.3):
 * - NCP-n-eudiwrp (0.4.0.194118.1.1): Natural persons, non-qualified, for electronic signature
 * - NCP-l-eudiwrp (0.4.0.194118.1.2): Legal persons, non-qualified, for electronic seal
 * - QCP-n-eudiwrp (0.4.0.194118.1.3): Natural persons, qualified, for electronic signature
 * - QCP-l-eudiwrp (0.4.0.194118.1.4): Legal persons, qualified, for electronic seal
 *
 * @param at Instant for validity check (null = current time)
 * @param policy Optional specific policy OID requirement. If provided, MUST be one of the four
 *               WRPAC policy OIDs. If null, any of the four policies is accepted.
 *
 * @return a WRP Access Certificate Profile
 *
 * @see [ETSI TS 119 411-8 Clause 5.3 - Certificate Policy OIDs]
 * @see [ETSI TS 119 411-8 Clause 6.6.1 - Certificate Profile]
 * @see [ETSI119411]
 */
public fun wrpAccessCertificateProfile(
    at: Instant? = null,
    policy: String? = null,
): CertificateProfile {
    val allowedPolicies = setOf(
        NCP_N_EUDIWRP,
        NCP_L_EUDIWRP,
        QCP_N_EUDIWRP,
        QCP_L_EUDIWRP,
    )

    val policies =
        if (policy != null) {
            require(policy in allowedPolicies) {
                buildString {
                    append("Certificate policy OID '$policy' is not a valid WRPAC policy OID. ")
                    append("Must be one of: ${allowedPolicies.joinToString(", ")}")
                }
            }
            setOf(policy)
        } else {
            allowedPolicies
        }

    return certificateProfile {
        requireEndEntityCertificate()
        requireDigitalSignature()
        requireValidAt(at)
        requirePolicy(policies)
        requireNoSelfSigned()
    }
}
