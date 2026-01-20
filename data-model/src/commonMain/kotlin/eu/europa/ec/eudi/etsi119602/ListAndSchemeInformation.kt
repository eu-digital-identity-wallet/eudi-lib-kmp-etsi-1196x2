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
package eu.europa.ec.eudi.etsi119602

import eu.europa.ec.eudi.etsi119602.ListAndSchemeInformation.Companion.explicit
import eu.europa.ec.eudi.etsi119602.ListAndSchemeInformation.Companion.implicit
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.ExperimentalTime

/**
 * Information on the list of trusted entities and its issuing
 * scheme
 * Information about the scheme within which the LoTE is issued may be provided either
 * [implicitly][implicit] or []explicitly][explicit]
 */
@Serializable
public data class ListAndSchemeInformation
@OptIn(ExperimentalTime::class)
internal constructor(
    /**
     * The value of this integer shall be incremented only when the rules for parsing the LoTE
     * in a specific syntax change, e.g. through addition/removal of a field or
     * a change to the values or meaning of an existing field
     */
    @SerialName(ETSI19602.LOTE_VERSION_IDENTIFIER) @Required val versionIdentifier: Int,
    /**
     * The value shall be incremented at each subsequent release of the LoTE and
     * shall not, under any circumstance, be re-cycled to "1" or to any value lower
     * than the one of the LoTE currently in force.
     * At the first release of the LoTE, the value of the sequence number shall be 1
     */
    @SerialName(ETSI19602.LOTE_SEQUENCE_NUMBER) @Required val sequenceNumber: Int,
    /**
     * The type of the list of trusted entities. I
     */
    @SerialName(ETSI19602.LOTE_TYPE) val type: URI? = null,
    /**
     * The name of the entity in charge of establishing, publishing,
     * signing, and maintaining the list of trusted entities
     */
    @SerialName(ETSI19602.SCHEME_OPERATOR_NAME) @Required val schemeOperatorName: List<MultilanguageString>,
    /**
     * The address of the legal entity or mandated organization
     * identified in the 'Scheme operator name' component (clause 6.3.4) for both postal and electronic communication
     */
    @SerialName(ETSI19602.SCHEME_OPERATOR_ADDRESS) val schemeOperatorAddress: SchemeOperatorAddress? = null,
    @SerialName(ETSI19602.SCHEME_NAME) val schemeName: List<MultilanguageString>? = null,
    @SerialName(ETSI19602.SCHEME_INFORMATION_URI) val schemeInformationURI: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.STATUS_DETERMINATION_APPROACH) val statusDeterminationApproach: String? = null,
    @SerialName(ETSI19602.SCHEME_TYPE_COMMUNITY_RULES) val schemeTypeCommunityRules: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SCHEME_TERRITORY) val schemeTerritory: CountryCode? = null,
    @SerialName(ETSI19602.POLICY_OR_LEGAL_NOTICE) val policyOrLegalNotice: List<PolicyOrLegalNotice>? = null,
    @SerialName(ETSI19602.HISTORICAL_INFORMATION_PERIOD) val historicalInformationPeriod: HistoricalInformationPeriod? = null,
    @SerialName(ETSI19602.POINTER_TO_OTHER_LOTE) val pointersToOtherLists: List<OtherLoTEPointer>? = null,
    @SerialName(ETSI19602.LIST_ISSUE_DATE_TIME) val listIssueDateTime: LoTEDateTime,
    @SerialName(ETSI19602.NEXT_UPDATE) val nextUpdate: LoTEDateTime,
    @SerialName(ETSI19602.DISTRIBUTION_POINTS) val distributionPoints: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SCHEME_EXTENSIONS) val schemeExtensions: List<MultiLanguageURI>? = null,
) {
    init {
        versionIdentifier.requireValidVersionIdentifier()
        sequenceNumber.requireValidSequenceNumber()
        requireNonEmpty(schemeOperatorName, ETSI19602.SCHEME_OPERATOR_NAME)
        requireNullOrNonEmpty(schemeName, ETSI19602.SCHEME_NAME)
        requireNullOrNonEmpty(schemeInformationURI, ETSI19602.SCHEME_INFORMATION_URI)
        requireNullOrNonEmpty(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        requireNullOrNonEmpty(policyOrLegalNotice, ETSI19602.POLICY_OR_LEGAL_NOTICE)
        requireNullOrNonEmpty(distributionPoints, ETSI19602.DISTRIBUTION_POINTS)
        requireNullOrNonEmpty(schemeExtensions, ETSI19602.SCHEME_EXTENSIONS)
    }

    public fun ensureIsExplicit() {
        checkNotNull(type, ETSI19602.LOTE_TYPE)
        checkNotNull(schemeOperatorAddress, ETSI19602.SCHEME_OPERATOR_ADDRESS)
        checkNotNull(schemeName, ETSI19602.SCHEME_NAME)
        checkNotNull(schemeInformationURI, ETSI19602.SCHEME_INFORMATION_URI)
        checkNotNull(statusDeterminationApproach, ETSI19602.STATUS_DETERMINATION_APPROACH)
        checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        checkNotNull(schemeTerritory, ETSI19602.SCHEME_TERRITORY)
        checkNotNull(policyOrLegalNotice, ETSI19602.POLICY_OR_LEGAL_NOTICE)
    }

    public fun ensureIsImplicit() {
        checkIsNull(schemeName, ETSI19602.SCHEME_NAME)
        checkIsNull(schemeInformationURI, ETSI19602.SCHEME_INFORMATION_URI)
        checkIsNull(statusDeterminationApproach, ETSI19602.STATUS_DETERMINATION_APPROACH)
        checkIsNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
        checkIsNull(policyOrLegalNotice, ETSI19602.POLICY_OR_LEGAL_NOTICE)
    }

    public companion object {
        @OptIn(ExperimentalTime::class)
        public fun implicit(
            sequenceNumber: Int = ETSI19602.INITIAL_SEQUENCE_NUMBER,
            type: URI? = null,
            schemeOperatorName: List<MultilanguageString>,
            schemeOperatorAddress: SchemeOperatorAddress? = null,
            schemeTerritory: CountryCode? = null,
            historicalInformationPeriod: HistoricalInformationPeriod? = null,
            pointerToOtherLote: List<OtherLoTEPointer>? = null,
            listIssueDateTime: LoTEDateTime,
            nextUpdate: LoTEDateTime,
            distributionPoints: List<MultiLanguageURI>? = null,
            schemeExtensions: List<MultiLanguageURI>? = null,
        ): ListAndSchemeInformation =
            ListAndSchemeInformation(
                versionIdentifier = ETSI19602.LOTE_VERSION,
                sequenceNumber = sequenceNumber,
                type = type,
                schemeOperatorName = schemeOperatorName,
                schemeOperatorAddress = schemeOperatorAddress,
                schemeName = null,
                schemeInformationURI = null,
                statusDeterminationApproach = null,
                schemeTypeCommunityRules = null,
                schemeTerritory = schemeTerritory,
                policyOrLegalNotice = null,
                historicalInformationPeriod = historicalInformationPeriod,
                pointersToOtherLists = pointerToOtherLote,
                listIssueDateTime = listIssueDateTime,
                nextUpdate = nextUpdate,
                distributionPoints = distributionPoints,
                schemeExtensions = schemeExtensions,
            )

        @OptIn(ExperimentalTime::class)
        public fun explicit(
            sequenceNumber: Int = ETSI19602.INITIAL_SEQUENCE_NUMBER,
            type: URI,
            schemeOperatorName: List<MultilanguageString>,
            schemeOperatorAddress: SchemeOperatorAddress,
            schemeName: List<MultilanguageString>,
            schemeInformationURI: List<MultiLanguageURI>,
            statusDeterminationApproach: String,
            schemeTypeCommunityRules: List<MultiLanguageURI>,
            schemeTerritory: CountryCode,
            policyOrLegalNotice: List<PolicyOrLegalNotice>,
            historicalInformationPeriod: HistoricalInformationPeriod? = null,
            pointerToOtherLote: List<OtherLoTEPointer>? = null,
            listIssueDateTime: LoTEDateTime,
            nextUpdate: LoTEDateTime,
            distributionPoints: List<MultiLanguageURI>? = null,
            schemeExtensions: List<MultiLanguageURI>? = null,
        ): ListAndSchemeInformation =
            ListAndSchemeInformation(
                versionIdentifier = ETSI19602.LOTE_VERSION,
                sequenceNumber = sequenceNumber,
                type = type,
                schemeOperatorName = schemeOperatorName,
                schemeOperatorAddress = schemeOperatorAddress,
                schemeName = schemeName,
                schemeInformationURI = schemeInformationURI,
                statusDeterminationApproach = statusDeterminationApproach,
                schemeTypeCommunityRules = schemeTypeCommunityRules,
                schemeTerritory = schemeTerritory,
                policyOrLegalNotice = policyOrLegalNotice,
                historicalInformationPeriod = historicalInformationPeriod,
                pointersToOtherLists = pointerToOtherLote,
                listIssueDateTime = listIssueDateTime,
                nextUpdate = nextUpdate,
                distributionPoints = distributionPoints,
                schemeExtensions = schemeExtensions,
            )

        public fun Int.requireValidVersionIdentifier(): Int =
            apply {
                require(this == ETSI19602.LOTE_VERSION) {
                    "Invalid ${ETSI19602.LOTE_VERSION_IDENTIFIER}. Expected ${ETSI19602.LOTE_VERSION}, got $this"
                }
            }

        public fun Int.requireValidSequenceNumber(): Int =
            apply {
                require(this >= ETSI19602.INITIAL_SEQUENCE_NUMBER) {
                    "${ETSI19602.LOTE_SEQUENCE_NUMBER} me equal or greater than ${ETSI19602.INITIAL_SEQUENCE_NUMBER}, got $this"
                }
            }
    }
}

public object LoTEType {
    public fun of(value: String): URI = "${ETSI19602.LOTE_TYPE_URI}/$value"
}

/**
 * The SchemeOperatorAddress component specifies the address of the legal entity or mandated organization
 * identified in the 'Scheme operator name' component for both postal and electronic communication
 */
@Serializable
public data class SchemeOperatorAddress(
    @SerialName(ETSI19602.SCHEME_OPERATOR_POSTAL_ADDRESS) @Required val postalAddresses: List<PostalAddress>,
    @SerialName(ETSI19602.SCHEME_OPERATOR_ELECTRONIC_ADDRESS) @Required val electronicAddresses: List<MultiLanguageURI>,
) {
    init {
        requireNonEmpty(postalAddresses, ETSI19602.SCHEME_OPERATOR_POSTAL_ADDRESS)
        require(electronicAddresses.size >= 2) {
            "${ETSI19602.SCHEME_OPERATOR_ELECTRONIC_ADDRESS} must contain at least an e-mail and a web-site address"
        }
    }
}

@Serializable(with = PolicyOrLegalNoticeSerializer::class)
public sealed interface PolicyOrLegalNotice {
    @Serializable
    public data class Policy(
        @SerialName(ETSI19602.LOTE_POLICY) @Required val policy: MultiLanguageURI,
    ) : PolicyOrLegalNotice

    @Serializable
    public data class LegalNotice(
        @SerialName(ETSI19602.LOTE_LEGAL_NOTICE) @Required val legalNotice: String,
    ) : PolicyOrLegalNotice
}

public object PolicyOrLegalNoticeSerializer :
    JsonContentPolymorphicSerializer<PolicyOrLegalNotice>(PolicyOrLegalNotice::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PolicyOrLegalNotice> =
        when {
            ETSI19602.LOTE_POLICY in element.jsonObject -> PolicyOrLegalNotice.Policy.serializer()
            ETSI19602.LOTE_LEGAL_NOTICE in element.jsonObject -> PolicyOrLegalNotice.LegalNotice.serializer()
            else -> throw IllegalArgumentException("Invalid policy or legal notice")
        }
}

@Serializable
@JvmInline
public value class HistoricalInformationPeriod(
    public val value: Int,
)

@Serializable
public data class OtherLoTEPointer(
    @SerialName(ETSI19602.LOTE_LOCATION) @Required val location: String, // URI
    @SerialName(ETSI19602.SERVICE_DIGITAL_IDENTITIES) @Required val serviceDigitalIdentities: List<ServiceDigitalIdentity>,
    @SerialName(ETSI19602.LOTE_QUALIFIERS) @Required val qualifiers: List<LoTEQualifier>,
) {
    init {
        requireNonEmpty(serviceDigitalIdentities, ETSI19602.SERVICE_DIGITAL_IDENTITIES)
        requireNonEmpty(qualifiers, ETSI19602.LOTE_QUALIFIERS)
    }
}

@Serializable
public data class LoTEQualifier(
    @SerialName(ETSI19602.LOTE_TYPE) val type: URI,
    @SerialName(ETSI19602.SCHEME_OPERATOR_NAME) @Required val schemeOperatorName: List<MultilanguageString>,
    @SerialName(ETSI19602.SCHEME_TYPE_COMMUNITY_RULES) val schemeTypeCommunityRules: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SCHEME_TERRITORY) val schemeTerritory: CountryCode? = null,
    @SerialName(ETSI19602.MIME_TYPE) val mimeType: String,
) {
    init {
        requireNonEmpty(schemeOperatorName, ETSI19602.SCHEME_OPERATOR_NAME)
        requireNotBlank(mimeType, ETSI19602.MIME_TYPE)
    }
}
