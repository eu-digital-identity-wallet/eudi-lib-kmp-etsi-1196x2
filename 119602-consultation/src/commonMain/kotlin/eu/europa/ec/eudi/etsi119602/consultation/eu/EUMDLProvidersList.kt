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
import eu.europa.ec.eudi.etsi119602.MultiLanguageURI
import eu.europa.ec.eudi.etsi119602.URIValue

/**
 * [mDL Providers List Specification](https://eidas.ec.europa.eu/efda/wallet/lists-of-trusted-entities/mdl-providers)
 */
public object EUMDLProvidersListSpec {
    public const val LOTE_TYPE: String = "http://trust.ec.europa.eu/lists/mDL/mDLProvidersListType"
    public const val STATUS_DETERMINATION_APPROACH: String =
        "http://trust.ec.europa.eu/lists/mDL/mDLProvidersListStatusDetn"
    public const val SCHEME_COMMUNITY_RULES: String = "http://trust.ec.europa.eu/lists/mDL/schemerules"
    public const val SVC_TYPE_ISSUANCE: String = "http://trust.ec.europa.eu/lists/mDL/SvcType/Issuance"
    public const val SVC_TYPE_REVOCATION: String = "http://trust.ec.europa.eu/lists/mDL/SvcType/Revocation"

    @Deprecated("This value is not included in the specification. It is used though to DIGIT's LOTE")
    public const val SVC_TYPE_ISSUANCE_2: String = "http://uri.etsi.org/19602/SvcType/mDL/Issuance"
}

public val EUMDLProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation = EUListAndSchemeInformationProfile(
            type = EUMDLProvidersListSpec.LOTE_TYPE,
            statusDeterminationApproach = EUMDLProvidersListSpec.STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(MultiLanguageURI.en(URIValue(EUMDLProvidersListSpec.SCHEME_COMMUNITY_RULES))),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities = EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE,
                revocation = EUMDLProvidersListSpec.SVC_TYPE_REVOCATION,
            ),
            mustContainX509Certificates = true,
            serviceStatuses = emptySet(),
        ),
    )
