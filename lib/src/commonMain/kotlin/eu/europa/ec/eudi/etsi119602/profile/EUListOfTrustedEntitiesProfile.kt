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
import eu.europa.ec.eudi.etsi119602.profile.TrustedEntityAssertions.Companion.ensureTrustedEntities
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil

/**
 * Expectations about an EU Specific LoTE
 */
public data class EUListOfTrustedEntitiesProfile(
    /**
     * Scheme expectations
     */
    val listAndSchemeInformation: ListAndSchemeInformationProfile,

    /**
     * Trusted entities expectations
     */
    val trustedEntities: EUTrustedEntitiesProfile,
) {

    @Throws(IllegalStateException::class)
    public fun ListOfTrustedEntities.ensureProfile() {
        checkSchemeInformation()
        checkTrustedEntities()
    }

    @Throws(IllegalStateException::class)
    private fun ListOfTrustedEntities.checkSchemeInformation() {
        try {
            with(ListAndSchemeInformationAssertions) {
                schemeInformation.ensureListAndSchemeInformation(listAndSchemeInformation)
            }
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

public sealed interface ValueRequirement<out T> {
    public data class Required<out T>(val requirement: T) : ValueRequirement<T>
    public data object Absent : ValueRequirement<Nothing>
}

/**
 * Expectations about the scheme of an EU-specific LoTE
 */
public data class ListAndSchemeInformationProfile(
    /**
     * The type of the list of trusted entities.
     */
    val type: LoTEType,
    val statusDeterminationApproach: String,
    val schemeCommunityRules: List<MultiLanguageURI>,
    val schemeTerritory: CountryCode,
    val maxMonthsUntilNextUpdate: Int,
    val historicalInformationPeriod: ValueRequirement<HistoricalInformationPeriod>,
)

/**
 * Expectations about trusted entities of an EU-specific LoTE
 */
public data class EUTrustedEntitiesProfile(
    /**
     * Exclusive set of service type identifiers that trusted entities of the LoTE may support
     */
    val serviceTypeIdentifiers: Set<URI>,
    /**
     * Indicates whether the LoTE must contain services that are identified in
     * terms of X509 certificates
     */
    val mustContainX509Certificates: Boolean,
    /**
     * Exclusive set of service statuses that trusted entities of the LoTE may support.
     */
    val serviceStatuses: Set<URI>,
) {
    init {
        require(serviceTypeIdentifiers.isNotEmpty()) { "Service type identifiers cannot be empty" }
    }
}

internal interface ListAndSchemeInformationAssertions {

    fun ListAndSchemeInformation.ensureTypeIs(expected: LoTEType) {
        check(type == expected) {
            "Invalid ${ETSI19602.LOTE_TYPE}. Expected $expected, got $type"
        }
    }

    fun ListAndSchemeInformation.ensureStatusDeterminationApproachIs(expected: String) {
        check(statusDeterminationApproach == expected) {
            "Invalid ${ETSI19602.STATUS_DETERMINATION_APPROACH}. Expected $expected, got $statusDeterminationApproach"
        }
    }

    fun ListAndSchemeInformation.ensureSchemeCommunityRulesIs(expected: List<MultiLanguageURI>) {
        val actual =
            checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        check(actual.size == expected.size && actual.none { it !in expected }) {
            "Invalid ${ETSI19602.SCHEME_TYPE_COMMUNITY_RULES}. Expected $expected, got $actual"
        }
    }

    fun ListAndSchemeInformation.ensureSchemeTerritoryIs(expected: CountryCode) {
        check(schemeTerritory == expected) {
            "Invalid ${ETSI19602.SCHEME_TERRITORY}. Expected $expected, got $schemeTerritory"
        }
    }

    fun ListAndSchemeInformation.ensureNextUpdateIsWithinMonths(
        months: Int,
    ) {
        val monthsUntilNextUpdate = nextUpdate.monthsUntil(listIssueDateTime, TimeZone.UTC)
        check(monthsUntilNextUpdate <= months) {
            "${ETSI19602.NEXT_UPDATE} must be within $months months from ${ETSI19602.LIST_ISSUE_DATE_TIME}, got $monthsUntilNextUpdate months"
        }
    }

    fun ListAndSchemeInformation.ensureHistoricalInformationPeriod(requirement: ValueRequirement<HistoricalInformationPeriod>) {
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

    fun ListAndSchemeInformation.ensureListAndSchemeInformation(listAndSchemeInformation: ListAndSchemeInformationProfile) {
        ensureIsExplicit()
        ensureTypeIs(listAndSchemeInformation.type)
        ensureStatusDeterminationApproachIs(listAndSchemeInformation.statusDeterminationApproach)
        ensureSchemeCommunityRulesIs(listAndSchemeInformation.schemeCommunityRules)
        ensureSchemeTerritoryIs(listAndSchemeInformation.schemeTerritory)
        ensureHistoricalInformationPeriod(listAndSchemeInformation.historicalInformationPeriod)
        ensureNextUpdateIsWithinMonths(listAndSchemeInformation.maxMonthsUntilNextUpdate)
    }

    companion object : ListAndSchemeInformationAssertions
}

internal interface TrustedEntityAssertions {

    fun ServiceInformation.ensureServiceTypeIsAnyOf(expectedServiceTypes: Set<URI>) {
        checkNotNull(typeIdentifier, ETSI19602.SERVICE_TYPE_IDENTIFIER)
        check(typeIdentifier in expectedServiceTypes) {
            "Invalid ${ETSI19602.SERVICE_TYPE_IDENTIFIER}. Expected one of $expectedServiceTypes, got $typeIdentifier"
        }
    }

    fun ServiceInformation.ensureDigitalIdentityContainsX509Certificate(mustContainX509Certificates: Boolean) {
        if (mustContainX509Certificates) {
            // We need to check only that x509Certificates is not null.
            // The ServiceInformation check that if this is not null,
            checkNotNull(digitalIdentity.x509Certificates, ETSI19602.X509_CERTIFICATES)
        }
    }

    fun ServiceInformation.ensureServiceStatusIn(statuses: Set<URI>) {
        if (statuses.isEmpty()) {
            checkIsNull(status, ETSI19602.SERVICE_STATUS)
            checkIsNull(statusStartingTime, ETSI19602.STATUS_STARTING_TIME)
        } else {
            checkNotNull(status, ETSI19602.SERVICE_STATUS)
            checkNotNull(statusStartingTime, ETSI19602.STATUS_STARTING_TIME)
            check(status in statuses) {
                "Invalid ${ETSI19602.SERVICE_STATUS}. Expected one of $statuses, got $status"
            }
        }
    }

    fun TrustedEntity.ensureTrustedEntities(trustedEntities: EUTrustedEntitiesProfile) {
        services.forEach { service ->
            service.information.ensureServiceTypeIsAnyOf(trustedEntities.serviceTypeIdentifiers)
            service.information.ensureDigitalIdentityContainsX509Certificate(trustedEntities.mustContainX509Certificates)
            service.information.ensureServiceStatusIn(trustedEntities.serviceStatuses)
        }
    }

    companion object : TrustedEntityAssertions
}
