/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.CountryCode
import eu.europa.ec.eudi.etsi119602.ETSI19602
import eu.europa.ec.eudi.etsi119602.LoTEType
import eu.europa.ec.eudi.etsi119602.MultiLanguageURI
import eu.europa.ec.eudi.etsi119602.URIValue

public val EUPubEAAProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        ListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_PUB_EAA_PROVIDERS_LOTE),
            statusDeterminationApproach = ETSI19602.EU_PUB_EAA_PROVIDERS_STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(MultiLanguageURI.en(URIValue(ETSI19602.EU_PUB_EAA_PROVIDERS_SCHEME_COMMUNITY_RULES))),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = setOf(
                ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_ISSUANCE,
                ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            mustContainX509Certificates = false,
            serviceStatuses = setOf(
                "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/notified",
                "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/withdrawn",
            ),
        ),
    )
