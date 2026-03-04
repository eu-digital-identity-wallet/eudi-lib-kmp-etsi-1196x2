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
import eu.europa.ec.eudi.etsi119602.consultation.ETSI19412
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateOperations
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificatePolicyConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateBasicConstraintsConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateMultipleCertificateConstraints
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriodConstraint

public val EUWRPRCProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_WRPRC_PROVIDERS_LOTE),
            statusDeterminationApproach = ETSI19602.EU_WRPRC_PROVIDERS_STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(
                MultiLanguageURI.en(URIValue(ETSI19602.EU_WRPRC_PROVIDERS_SCHEME_COMMUNITY_RULES)),
            ),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = ETSI19602.EU_WRPRC_PROVIDERS_SVC_TYPE_ISSUANCE,
                revocation = ETSI19602.EU_WRPRC_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            mustContainX509Certificates = true,
            serviceStatuses = emptySet(),
            chainValidationAlgorithm = ChainValidationAlgorithm.PKIX,
            hasConstraints = object : CertificateConstraints {
                override fun <CERT : Any> CertificateOperations<CERT>.evaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    wrprcProviderCertificateConstraintsEvaluator()
            },
        ),
    )

/**
 * Creates constraints for WRPRC Provider certificates (LoTE CA).
 *
 * Per ETSI TS 119 602 Annex G:
 * - Certificate type: CA certificate (cA=TRUE)
 * - QCStatement: NOT required
 * - Key Usage: keyCertSign REQUIRED
 * - Validity: Must be valid at validation time
 * - Certificate Policy: ETSI TS 119 411-8 (or equivalent)
 *
 * Note: WRPRC Providers are CAs that sign WRPRC JWT attestations.
 * The LoTE contains the WRPRC Provider's CA certificate.
 * WRPRC validation involves both PKIX (certificate chain) and JWT signature verification.
 *
 * @param maxPathLen optional maximum path length constraint (default: null, no constraint)
 *
 * @return a validator configured for WRPRC Provider certificates
 */
public fun <CERT : Any> CertificateOperations<CERT>.wrprcProviderCertificateConstraintsEvaluator(
    maxPathLen: Int? = null,
): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints.of(
    EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, ::getBasicConstraints),
    KeyUsageConstraint.requireKeyCertSign(::getKeyUsage),
    ValidityPeriodConstraint.validateAtCurrentTime(::getValidityPeriod),
    CertificatePolicyConstraint.requirePolicy(ETSI19412.POLICY_WRPRC_PROVIDER, ::getCertificatePolicies),
)
