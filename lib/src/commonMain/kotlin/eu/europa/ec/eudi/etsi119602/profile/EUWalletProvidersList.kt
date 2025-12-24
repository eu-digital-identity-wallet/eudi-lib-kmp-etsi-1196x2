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

import eu.europa.ec.eudi.etsi119602.*

/**
 * A LoTE profile aimed at supporting the publication by the European Commission of a list of
 * wallet providers according to CIR 2024/2980 [i.2] Article 5(2)
 */
public object EUWalletProvidersList : ListOfTrustedEntitiesProfile {
    override val name: String get() = ETSI19602.EU_WALLET_PROVIDERS_LOTE
    override val scheme: Scheme get() = Scheme.EXPLICIT
    override val statusDeterminationApproach: String get() = ETSI19602.EU_WALLET_PROVIDERS_STATUS_DETERMINATION_APPROACH
    override val schemeCommunityRules: List<MultiLanguageURI>
        get() = listOf(
            MultiLanguageURI(Language.Companion.ENGLISH, URIValue(ETSI19602.EU_WALLET_PROVIDERS_SCHEME_COMMUNITY_RULES))
        )
    override val schemeTerritory: CountryCode get() = CountryCode.Companion.EU
    override val maxMonthsUntilNextUpdate: Int get() = 6
    override val historicalInformationPeriod: ValueRequirement get() = ValueRequirement.Absent

}
