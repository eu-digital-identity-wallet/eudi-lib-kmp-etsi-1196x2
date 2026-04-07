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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

/**
 * A LoTE profile aimed at supporting the publication by the European Commission of a list of
 * PID providers according to CIR 2024/2980 Article 5(2).
 *
 * **Important:** Per ETSI TS 119 602 Annex D, the ServiceDigitalIdentity may contain either:
 * - End-entity certificates (Direct Trust validation)
 * - CA certificates (PKIX validation)
 *
 * This profile supports **both** validation methods as specified in LoTE-Certificate-Validation.md v4.5.
 */
public val EUPIDProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_PID_PROVIDERS_LOTE),
            statusDeterminationApproach = URIValue(ETSI19602.EU_PID_PROVIDERS_STATUS_DETERMINATION_APPROACH),
            schemeCommunityRules = listOf(MultiLanguageURI.en(URIValue(ETSI19602.EU_PID_PROVIDERS_SCHEME_COMMUNITY_RULES))),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE,
                revocation = ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            serviceDigitalIdentityMustHaveCertificates = true,
            serviceStatuses = emptySet(),
            serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.EndEntityOrCA,
        ),
    )

/**
 * Creates constraints for PID Provider CA certificates in LoTE.
 *
 * When the LoTE contains a CA certificate (for PKIX validation), different constraints apply:
 * - Certificate type: CA (cA=TRUE)
 * - QCStatement: NOT required (QCStatements are for end-entity certificates only)
 * - Key Usage: keyCertSign REQUIRED (for issuing certificates)
 * - Validity: Must be valid at validation time
 * - Certificate Policy: NOT required for CA certificates
 * - AIA: NOT required (this is a trust anchor)
 *
 * @return a validator configured for PID Provider CA certificates
 */
public fun pidProviderCACertificateProfile(
    at: Instant? = null,
    maxPathLen: Int? = null,
): CertificateProfile =
    certificateProfile {
        ca(maxPathLen)
        keyUsageCertSign()
        validAt(at)
    }
