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

public sealed interface ValueRequirement<out T> {
    public data class Required<out T>(val requirement: T) : ValueRequirement<T>
    public data object Absent : ValueRequirement<Nothing>
}

public data class ListAndSchemeInformationProfile(
    val type: LoTEType,
    val statusDeterminationApproach: String,
    val schemeCommunityRules: List<MultiLanguageURI>,
    val schemeTerritory: CountryCode,
    val maxMonthsUntilNextUpdate: Int,
    val historicalInformationPeriod: ValueRequirement<HistoricalInformationPeriod>,
) {
    public companion object {

        public operator fun invoke(
            name: String,
            statusDeterminationApproach: String,
            schemeCommunityRules: List<MultiLanguageURI>,
            schemeTerritory: CountryCode,
            maxMonthsUntilNextUpdate: Int,
            historicalInformationPeriod: ValueRequirement<HistoricalInformationPeriod>
        ): ListAndSchemeInformationProfile = ListAndSchemeInformationProfile(
            LoTEType.of(name),
            statusDeterminationApproach,
            schemeCommunityRules,
            schemeTerritory,
            maxMonthsUntilNextUpdate,
            historicalInformationPeriod
        )
    }

}

public data class TrustedEntitiesProfile(
    val issuanceServiceTypeIdentifier: URI,
    val revocationServiceTypeIdentifier: URI,
)

public interface ListOfTrustedEntitiesProfile {
    public val listAndSchemeInformation: ListAndSchemeInformationProfile
    public val trustedEntities: TrustedEntitiesProfile


    @Throws(IllegalStateException::class)
    public fun ListOfTrustedEntities.ensureProfile()

    public companion object {
        public operator fun invoke(
            listAndSchemeInformation: ListAndSchemeInformationProfile,
            trustedEntities: TrustedEntitiesProfile
        ): ListOfTrustedEntitiesProfile =
            DefaultListOfTrustedEntitiesProfile(listAndSchemeInformation, trustedEntities)
    }
}

@Suppress("FunctionName")
internal fun DefaultListOfTrustedEntitiesProfile(
    listAndSchemeInformation: ListAndSchemeInformationProfile,
    trustedEntities: TrustedEntitiesProfile
): ListOfTrustedEntitiesProfile =
    object : ListOfTrustedEntitiesProfile, ListAndSchemeInformationAssertions, TrustedEntityAssertions {
        public override val listAndSchemeInformation: ListAndSchemeInformationProfile
            get() = listAndSchemeInformation
        public override val trustedEntities: TrustedEntitiesProfile
            get() = trustedEntities


        @Throws(IllegalStateException::class)
        public override fun ListOfTrustedEntities.ensureProfile() {
            checkSchemeInformation()
            checkTrustedEntities()
        }

        @Throws(IllegalStateException::class)
        private fun ListOfTrustedEntities.checkSchemeInformation() {
            try {
                schemeInformation.ensureListAndSchemeInformation(listAndSchemeInformation)
            } catch (e: IllegalStateException) {
                throw IllegalStateException("Violation of ${listAndSchemeInformation.type.value}: ${e.message}")
            }
        }

        @Throws(IllegalStateException::class)
        private fun ListOfTrustedEntities.checkTrustedEntities() {
            val trustedEntitiesErrors = mutableMapOf<Int, String>()
            entities?.forEachIndexed { index, entity ->
                try {
                    entity.ensureTrustedEntities(trustedEntities)
                } catch (e: IllegalStateException) {
                    trustedEntitiesErrors[index] = e.message ?: "Unknown error"
                }
            }
            if (trustedEntitiesErrors.isNotEmpty()) {
                throw IllegalStateException("Violation of ${listAndSchemeInformation.type.value}, trusted entities errors: ${trustedEntitiesErrors.map { "${it.key}: ${it.value}" }}")
            }
        }
    }

public interface ListOfTrustedEntitiesProfileAndLens : ListOfTrustedEntitiesProfile, EULens

public interface ListAndSchemeInformationAssertions {

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

    public fun ListAndSchemeInformation.ensureHistoricalInformationPeriod(requirement: ValueRequirement<HistoricalInformationPeriod>) {
        when (requirement) {
            is ValueRequirement.Required -> {
                checkNotNull(historicalInformationPeriod, ETSI19602.HISTORICAL_INFORMATION_PERIOD)
                check(historicalInformationPeriod == requirement.requirement) {
                    "Invalid ${ETSI19602.HISTORICAL_INFORMATION_PERIOD}. Expected $requirement, got $historicalInformationPeriod"
                }
            }

            is ValueRequirement.Absent ->
                checkIsNull(historicalInformationPeriod, ETSI19602.HISTORICAL_INFORMATION_PERIOD)
        }
    }

    public fun ListAndSchemeInformation.ensureListAndSchemeInformation(listAndSchemeInformation: ListAndSchemeInformationProfile) {
        ensureIsExplicit()
        ensureTypeIs(listAndSchemeInformation.type)
        ensureStatusDeterminationApproachIs(listAndSchemeInformation.statusDeterminationApproach)
        ensureSchemeCommunityRulesIs(listAndSchemeInformation.schemeCommunityRules)
        ensureSchemeTerritoryIs(listAndSchemeInformation.schemeTerritory)
        ensureHistoricalInformationPeriod(listAndSchemeInformation.historicalInformationPeriod)
        ensureNextUpdateIsWithinMonths(listAndSchemeInformation.maxMonthsUntilNextUpdate)
    }
}

public interface TrustedEntityAssertions {

    public fun ServiceInformation.ensureServiceTypeIsOneOf(vararg expected: URI) {
        checkNotNull(typeIdentifier, ETSI19602.SERVICE_TYPE_IDENTIFIER)
        check(typeIdentifier in expected) {
            "Invalid ${ETSI19602.SERVICE_TYPE_IDENTIFIER}. Expected one of $expected, got $typeIdentifier"
        }
    }

    public fun ServiceInformation.ensureDigitalIdentityContainsX509Certificate() {
        // We need to check only that x509Certificates is not null.
        // The ServiceInformation check that if this is not null,
        checkNotNull(digitalIdentity.x509Certificates, ETSI19602.X509_CERTIFICATES)

    }

    public fun ServiceInformation.ensureServiceStatusIsNotUsed() {
        checkIsNull(status, ETSI19602.SERVICE_STATUS)
        checkIsNull(statusStartingTime, ETSI19602.STATUS_STARTING_TIME)
    }

    public fun TrustedEntity.ensureTrustedEntities(trustedEntities: TrustedEntitiesProfile) {
        services.forEach { service ->
            service.information.ensureServiceTypeIsOneOf(
                trustedEntities.issuanceServiceTypeIdentifier,
                trustedEntities.revocationServiceTypeIdentifier
            )
            service.information.ensureDigitalIdentityContainsX509Certificate()
            service.information.ensureServiceStatusIsNotUsed()
        }
    }


    public companion object : TrustedEntityAssertions
}
