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

import com.eygraber.uri.Uri
import eu.europa.ec.eudi.etsi119602.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

public val EUWRPRCProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_WRPRC_PROVIDERS_LOTE),
            statusDeterminationApproach = Uri.parse(ETSI19602.EU_WRPRC_PROVIDERS_STATUS_DETERMINATION_APPROACH),
            schemeCommunityRules = listOf(
                MultiLanguageURI.en(Uri.parse(ETSI19602.EU_WRPRC_PROVIDERS_SCHEME_COMMUNITY_RULES)),
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
            serviceDigitalIdentityMustHaveCertificates = true,
            serviceStatuses = emptySet(),
            serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.CA,
        ),
    )

/**
 * Creates constraints for WRPRC Provider certificates
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
 * @return WRPRC Provider certificates
 * @see [ETSI TS 119 475 - WRPRC Specification]
 */
public fun wrprcProviderCertificateProfile(
    at: Instant? = null,
    maxPathLen: Int? = null,
): CertificateProfile =
    certificateProfile {
        ca(maxPathLen)
        keyUsageCertSign()
        validAt(at)
        policyIsPresent()
    }
