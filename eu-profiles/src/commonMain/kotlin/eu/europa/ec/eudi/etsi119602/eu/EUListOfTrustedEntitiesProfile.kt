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
package eu.europa.ec.eudi.etsi119602.eu

import eu.europa.ec.eudi.etsi119602.*
import eu.europa.ec.eudi.etsi119602.eu.TrustedEntityAssertions.Companion.ensureTrustedEntities
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil

/**
 * Expectations about an EU Specific LoTE
 *
 * @see EUPIDProvidersList
 * @see EUWalletProvidersList
 * @see EUWRPACProvidersList
 * @see EUWRPRCProvidersList
 * @see EUPubEAAProvidersList
 */
public data class EUListOfTrustedEntitiesProfile(
    /**
     * Scheme and information expectations
     */
    val listAndSchemeInformation: EUListAndSchemeInformationProfile,

    /**
     * Trusted entities expectations
     */
    val trustedEntities: EUTrustedEntitiesProfile,
) {

    /**
     * Checks that the provided list of trusted entities satisfies the profile expectations.
     *
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the profile is not satisfied
     */
    @Throws(IllegalStateException::class)
    public fun ListOfTrustedEntities.ensureCompliesToProfile() {
        checkSchemeInformation()
        checkTrustedEntities()
    }

    @Throws(IllegalStateException::class)
    private fun ListOfTrustedEntities.checkSchemeInformation() {
        try {
            with(ListAndSchemeInformationAssertions) {
                schemeInformation.ensureCompliesTo(listAndSchemeInformation)
            }
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Violation of ${listAndSchemeInformation.type}: ${e.message}")
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
            throw IllegalStateException("Violation of ${listAndSchemeInformation.type}, trusted entities errors: ${trustedEntitiesErrors.map { "${it.key}: ${it.value}" }}")
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
public data class EUListAndSchemeInformationProfile(
    /**
     * The type of the list of trusted entities.
     */
    val type: URI,
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

/**
 * Assertions about the scheme of an EU-specific LoTE
 */
internal interface ListAndSchemeInformationAssertions {

    /**
     * Ensures that the type of the list of trusted entities is the expected one.
     * @param expected the expected type
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the type is not the expected one
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureTypeIs(expected: URI) {
        check(type == expected) {
            "Invalid ${ETSI19602.LOTE_TYPE}. Expected $expected, got $type"
        }
    }

    /**
     * Ensures that the status determination approach of the list of trusted entities is the expected one.
     * @param expected the expected status determination approach
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the status determination approach is not the expected one
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureStatusDeterminationApproachIs(expected: String) {
        check(statusDeterminationApproach == expected) {
            "Invalid ${ETSI19602.STATUS_DETERMINATION_APPROACH}. Expected $expected, got $statusDeterminationApproach"
        }
    }

    /**
     * Ensures that the scheme community rules of the list of trusted entities are the expected ones.
     * @param expected the expected scheme community rules
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the scheme community rules are not the expected ones
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureSchemeCommunityRulesIs(expected: List<MultiLanguageURI>) {
        val actual =
            Assertions.checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        check(actual.size == expected.size && actual.none { it !in expected }) {
            "Invalid ${ETSI19602.SCHEME_TYPE_COMMUNITY_RULES}. Expected $expected, got $actual"
        }
    }

    /**
     * Ensures that the scheme territory of the list of trusted entities is the expected one.
     * @param expected the expected scheme territory
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the scheme territory is not the expected one
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureSchemeTerritoryIs(expected: CountryCode) {
        check(schemeTerritory == expected) {
            "Invalid ${ETSI19602.SCHEME_TERRITORY}. Expected $expected, got $schemeTerritory"
        }
    }

    /**
     * Ensures that the next update of the list of trusted entities is within the expected number of months from the issue date.
     * @param months the expected number of months
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the next update is not within the expected number of months
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureNextUpdateIsWithinMonths(
        months: Int,
    ) {
        val monthsUntilNextUpdate = nextUpdate.monthsUntil(listIssueDateTime, TimeZone.UTC)
        check(monthsUntilNextUpdate <= months) {
            "${ETSI19602.NEXT_UPDATE} must be within $months months from ${ETSI19602.LIST_ISSUE_DATE_TIME}, got $monthsUntilNextUpdate months"
        }
    }

    /**
     * Ensures that the historical information period of the list of trusted entities is the expected one.
     * @param requirement the expected historical information period requirement
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the historical information period is not the expected one
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureHistoricalInformationPeriod(requirement: ValueRequirement<HistoricalInformationPeriod>) {
        when (requirement) {
            is ValueRequirement.Required -> {
                Assertions.checkNotNull(historicalInformationPeriod, ETSI19602.HISTORICAL_INFORMATION_PERIOD)
                check(historicalInformationPeriod == requirement.requirement) {
                    "Invalid ${ETSI19602.HISTORICAL_INFORMATION_PERIOD}. Expected $requirement, got $historicalInformationPeriod"
                }
            }

            is ValueRequirement.Absent ->
                Assertions.checkIsNull(historicalInformationPeriod, ETSI19602.HISTORICAL_INFORMATION_PERIOD)
        }
    }

    /**
     * Ensures that the list of trusted entities complies to the expected profile.
     * @param listAndSchemeInformation the expected list and scheme information profile
     * @receiver the list of trusted entities to check
     * @throws IllegalStateException if the list of trusted entities does not comply to the expected profile
     */
    @Throws(IllegalStateException::class)
    fun ListAndSchemeInformation.ensureCompliesTo(listAndSchemeInformation: EUListAndSchemeInformationProfile) {
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

/**
 * Assertions about trusted entities of an EU-specific LoTE
 */
internal interface TrustedEntityAssertions {

    /**
     * Checks that the provided service information has a service type identifier that is any of the expected service types.
     *
     * @param expectedServiceTypes the expected service types
     * @throws IllegalStateException if the service type identifier is not any of the expected service types
     */
    @Throws(IllegalStateException::class)
    fun ServiceInformation.ensureServiceTypeIsAnyOf(expectedServiceTypes: Set<URI>) =
        ensureServiceTypeIsAnyOf(typeIdentifier, expectedServiceTypes)

    @Throws(IllegalStateException::class)
    fun ServiceHistoryInstance.ensureServiceTypeIsAnyOf(expectedServiceTypes: Set<URI>) =
        ensureServiceTypeIsAnyOf(typeIdentifier, expectedServiceTypes)

    @Throws(IllegalStateException::class)
    private fun ensureServiceTypeIsAnyOf(typeIdentifier: URI?, expectedServiceTypes: Set<URI>) {
        Assertions.checkNotNull(typeIdentifier, ETSI19602.SERVICE_TYPE_IDENTIFIER)
        check(typeIdentifier in expectedServiceTypes) {
            "Invalid ${ETSI19602.SERVICE_TYPE_IDENTIFIER}. Expected one of $expectedServiceTypes, got $typeIdentifier"
        }
    }

    /**
     * Checks that the provided service information has a digital identity that contains X509 certificates if required.
     *
     * @param mustContainX509Certificates whether the digital identity must contain X509 certificates
     * @throws IllegalStateException if the digital identity does not contain X509 certificates when required
     */
    @Throws(IllegalStateException::class)
    fun ServiceInformation.ensureDigitalIdentityContainsX509Certificate(mustContainX509Certificates: Boolean) {
        ensureDigitalIdentityContainsX509Certificate(digitalIdentity, mustContainX509Certificates)
    }

    @Throws(IllegalStateException::class)
    fun ServiceHistoryInstance.ensureDigitalIdentityContainsX509Certificate(mustContainX509Certificates: Boolean) {
        ensureDigitalIdentityContainsX509Certificate(digitalIdentity, mustContainX509Certificates)
    }

    @Throws(IllegalStateException::class)
    fun ensureDigitalIdentityContainsX509Certificate(digitalIdentity: ServiceDigitalIdentity, mustContainX509Certificates: Boolean) {
        if (mustContainX509Certificates) {
            // We need to check only that x509Certificates is not null.
            // The ServiceInformation check that if this is not null,
            Assertions.checkNotNull(digitalIdentity.x509Certificates, ETSI19602.X509_CERTIFICATES)
        }
    }

    /**
     * Checks that the provided service information has a service status that is in the expected statuses.
     * If the expected statuses are empty, checks that the service status is not provided.
     * @param statuses the expected statuses
     * @throws IllegalStateException if the service status is not in the expected statuses
     */
    @Throws(IllegalStateException::class)
    fun ServiceInformation.ensureServiceStatusIn(statuses: Set<URI>) {
        if (statuses.isEmpty()) {
            Assertions.checkIsNull(status, ETSI19602.SERVICE_STATUS)
            Assertions.checkIsNull(statusStartingTime, ETSI19602.STATUS_STARTING_TIME)
        } else {
            Assertions.checkNotNull(status, ETSI19602.SERVICE_STATUS)
            Assertions.checkNotNull(statusStartingTime, ETSI19602.STATUS_STARTING_TIME)
            check(status in statuses) {
                "Invalid ${ETSI19602.SERVICE_STATUS}. Expected one of $statuses, got $status"
            }
        }
    }

    /**
     * Ensure that the service information complies with the trusted entities profile.
     * @param trustedEntities the trusted entities profile
     * @throws IllegalStateException if the service information does not comply with the trusted entities profile
     */
    @Throws(IllegalStateException::class)
    fun ServiceInformation.ensureCompliesTo(trustedEntities: EUTrustedEntitiesProfile) {
        ensureServiceTypeIsAnyOf(trustedEntities.serviceTypeIdentifiers)
        ensureDigitalIdentityContainsX509Certificate(trustedEntities.mustContainX509Certificates)
        ensureServiceStatusIn(trustedEntities.serviceStatuses)
    }

    @Throws(IllegalStateException::class)
    fun ServiceHistoryInstance.ensureCompliesTo(trustedEntities: EUTrustedEntitiesProfile) {
        ensureServiceTypeIsAnyOf(trustedEntities.serviceTypeIdentifiers)
        ensureDigitalIdentityContainsX509Certificate(trustedEntities.mustContainX509Certificates)
    }

    @Throws(IllegalStateException::class)
    fun TrustedEntity.ensureTrustedEntities(trustedEntities: EUTrustedEntitiesProfile) {
        services.forEach { service ->
            service.information.ensureCompliesTo(trustedEntities)
            service.history.orEmpty().forEach { historyInstance ->
                historyInstance.ensureCompliesTo(trustedEntities)
            }
        }
    }

    companion object : TrustedEntityAssertions
}
