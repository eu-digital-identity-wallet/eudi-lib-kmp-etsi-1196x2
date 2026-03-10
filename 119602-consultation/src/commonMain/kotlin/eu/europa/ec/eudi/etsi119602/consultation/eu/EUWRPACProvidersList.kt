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

import eu.europa.ec.eudi.etsi119602.CountryCode
import eu.europa.ec.eudi.etsi119602.ETSI19602
import eu.europa.ec.eudi.etsi119602.LoTEType
import eu.europa.ec.eudi.etsi119602.MultiLanguageURI
import eu.europa.ec.eudi.etsi119602.URIValue
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119411
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateOperations
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificatePolicyConstraint.Companion.requireAnyPolicy
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateBasicConstraintsConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateMultipleCertificateConstraints
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriodConstraint

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
        endEntityCertificateConstraints = null,
    )

/**
 * Creates constraints for WRPAC Provider certificates (LoTE CA).
 *
 * Per ETSI TS 119 602 Annex F:
 * - Certificate type: CA certificate (cA=TRUE)
 * - QCStatement: NOT required
 * - Key Usage: keyCertSign REQUIRED
 * - Validity: Must be valid at validation time
 * - Certificate Policy: ETSI TS 119 411-8 (Access Certificate Policy)
 *
 * Note: WRPAC Providers are CAs that issue WRPAC (end-entity) certificates to Wallet Relying Parties.
 * The LoTE contains the WRPAC Provider's CA certificate, not the WRPAC itself.
 *
 * Per ETSI TS 119 411-8 Clause 5.3, WRPAC Providers may issue certificates under any of four policies:
 * - NCP-n-eudiwrp: Natural persons, non-qualified
 * - NCP-l-eudiwrp: Legal persons, non-qualified
 * - QCP-n-eudiwrp: Natural persons, qualified
 * - QCP-l-eudiwrp: Legal persons, qualified
 *
 * @param maxPathLen Optional maximum path length constraint for CA certificates.
 *                   Per RFC 5280 Section 4.2.1.9, pathLenConstraint specifies the maximum number
 *                   of non-self-issued intermediate certificates that may follow this certificate
 *                   in a valid certification path.
 *                   - `null` (default): No path length constraint enforced
 *                   - `0`: This CA can only issue end-entity certificates
 *                   - `1`: This CA can issue one intermediate CA certificate (recommended for most deployments)
 *                   - `2+`: This CA can issue multiple levels of intermediate CA certificates
 *
 * @return a validator configured for WRPAC Provider certificates
 *
 * @see [RFC 5280 Section 4.2.1.9 - Basic Constraints](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.9)
 */
public fun <CERT : Any> CertificateOperations<CERT>.wrpacProviderCertificateConstraintsEvaluator(maxPathLen: Int? = null): EvaluateMultipleCertificateConstraints<CERT> =
    EvaluateMultipleCertificateConstraints.of(
        EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, ::getBasicConstraints),
        KeyUsageConstraint.requireKeyCertSign(::getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(::getValidityPeriod),
        requireAnyPolicy(ETSI119411.ALL, ::getCertificatePolicies),
    )
