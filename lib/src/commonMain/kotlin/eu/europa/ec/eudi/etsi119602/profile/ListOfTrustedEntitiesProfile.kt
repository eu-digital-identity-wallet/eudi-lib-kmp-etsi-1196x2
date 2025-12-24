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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil
import org.graalvm.compiler.lir.profiling.MoveProfiler.profile

public enum class Scheme {
    EXPLICIT,
    IMPLICIT,
    BOTH,
}

public sealed interface ValueRequirement {
    public data object Requirement : ValueRequirement
    public data object Optional : ValueRequirement
    public data object Absent : ValueRequirement
}

public interface ListOfTrustedEntitiesProfile {
    public val name: String
    public val scheme: Scheme
    public val type: LoTEType get() = LoTEType.of(name)
    public val statusDeterminationApproach: String
    public val schemeCommunityRules: List<MultiLanguageURI>
    public val schemeTerritory: CountryCode
    public val maxMonthsUntilNextUpdate: Int
    public val historicalInformationPeriod: ValueRequirement

    public fun ListOfTrustedEntities.ensureScheme() {
        with(ListAndSchemeInformationAssertions) {
            try {
                schemeInformation.ensureWalletProvidersScheme(this@ListOfTrustedEntitiesProfile)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Violation of $name: ${e.message}")
            }
        }
    }
}

public object ListAndSchemeInformationAssertions {

    public fun ListAndSchemeInformation.ensureIsExplicit() {
        checkNotNull(type, ETSI19602.LOTE_TYPE)
        checkNotNull(schemeOperatorAddress, ETSI19602.SCHEME_OPERATOR_ADDRESS)
        checkNotNull(schemeName, ETSI19602.SCHEME_NAME)
        checkNotNull(schemeInformationURI, ETSI19602.SCHEME_INFORMATION_URI)
        checkNotNull(statusDeterminationApproach, ETSI19602.STATUS_DETERMINATION_APPROACH)
        checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        checkNotNull(schemeTerritory, ETSI19602.SCHEME_TERRITORY)
        checkNotNull(policyOrLegalNotice, ETSI19602.POLICY_OR_LEGAL_NOTICE)
    }

    public fun ListAndSchemeInformation.ensureTypeIs(expected: LoTEType) {
        check(type == expected) {
            "Invalid ${ETSI19602.LOTE_TYPE}. Expected $expected, got $type"
        }
    }

    public fun ListAndSchemeInformation.ensureStatusDeterminationApproachIs(expected: String) {
        check(statusDeterminationApproach == expected) {
            "Invalid ${ETSI19602.STATUS_DETERMINATION_APPROACH}. Expected $expected, got $statusDeterminationApproach"
        }
    }

    public fun ListAndSchemeInformation.ensureSchemeCommunityRulesIs(expected: List<MultiLanguageURI>) {
        val actual =
            checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        check(actual.size == expected.size && actual.none { it !in expected }) {
            "Invalid ${ETSI19602.SCHEME_TYPE_COMMUNITY_RULES}. Expected $expected, got $actual"
        }
    }

    public fun ListAndSchemeInformation.ensureSchemeTerritoryIs(expected: CountryCode) {
        check(schemeTerritory == expected) {
            "Invalid ${ETSI19602.SCHEME_TERRITORY}. Expected $expected, got $schemeTerritory"
        }
    }

    public fun ListAndSchemeInformation.ensureNextUpdateIsWithinMonths(
        months: Int,
    ) {
        val monthsUntilNextUpdate = nextUpdate.monthsUntil(listIssueDateTime, TimeZone.UTC)
        check(monthsUntilNextUpdate <= months) {
            "${ETSI19602.NEXT_UPDATE} must be within $months months from ${ETSI19602.LIST_ISSUE_DATE_TIME}, got $monthsUntilNextUpdate months"
        }
    }

    public fun ListAndSchemeInformation.ensureHistoricalInformationPeriod(requirement: ValueRequirement) {
        when (requirement) {
            is ValueRequirement.Requirement -> TODO()
            is ValueRequirement.Optional -> TODO()
            is ValueRequirement.Absent -> {
                check(historicalInformationPeriod == null) {
                    "${ETSI19602.HISTORICAL_INFORMATION_PERIOD} is not allowed"
                }
            }
        }
    }

    public fun ListAndSchemeInformation.ensureWalletProvidersScheme(profile: ListOfTrustedEntitiesProfile) {
        if (profile.scheme == Scheme.EXPLICIT) {
            ensureIsExplicit()
        }
        ensureTypeIs(profile.type)
        ensureStatusDeterminationApproachIs(profile.statusDeterminationApproach)
        ensureSchemeCommunityRulesIs(profile.schemeCommunityRules)
        ensureSchemeTerritoryIs(profile.schemeTerritory)
        ensureHistoricalInformationPeriod(profile.historicalInformationPeriod)
        ensureNextUpdateIsWithinMonths(profile.maxMonthsUntilNextUpdate)
    }
}
