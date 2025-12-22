package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.ExperimentalTime

/**
 * Information on the list of trusted entities and its issuing
 * scheme
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
    @SerialName(ETSI19602.LOTE_TYPE) val type: LoTEType? = null,
    /**
     * The name of the entity in charge of establishing, publishing,
     * signing, and maintaining the list of trusted entities
     */
    @SerialName(ETSI19602.SCHEME_OPERATOR_NAME) @Required val schemeOperatorName: List<MultiLangString>,
    @SerialName(ETSI19602.SCHEME_OPERATOR_ADDRESS) val schemeOperatorAddress: SchemeOperatorAddress? = null,
    @SerialName(ETSI19602.SCHEME_NAME) val schemeName: List<MultiLangString>? = null,
    @SerialName(ETSI19602.SCHEME_INFORMATION_URI) val schemeInformationURI: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.STATUS_DETERMINATION_APPROACH) val statusDeterminationApproach: String? = null,
    @SerialName(ETSI19602.SCHEME_TYPE_COMMUNITY_RULES) val schemeTypeCommunityRules: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SCHEME_TERRITORY) val schemeTerritory: SchemeTerritory? = null,
    @SerialName(ETSI19602.POLICY_OR_LEGAL_NOTICE) val policyOrLegalNotice: List<PolicyOrLegalNotice>? = null,
    @SerialName(ETSI19602.HISTORICAL_INFORMATION_PERIOD) val historicalInformationPeriod: HistoricalInformationPeriod? = null,
    @SerialName(ETSI19602.POINTER_TO_OTHER_LOTE) val pointerToOtherLote: MultiLanguageURI? = null,
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

    public fun ensureIsExplicit(): ListAndSchemeInformation =
        apply {
            fun checkNotNull(
                v: Any?,
                field: String,
            ) = checkNotNull(v != null) { "$field must be set for explicit list" }
            checkNotNull(type, ETSI19602.LOTE_TYPE)
            checkNotNull(schemeOperatorAddress, ETSI19602.SCHEME_OPERATOR_ADDRESS)
            checkNotNull(schemeName, ETSI19602.SCHEME_NAME)
            checkNotNull(schemeInformationURI, ETSI19602.SCHEME_INFORMATION_URI)
            checkNotNull(statusDeterminationApproach, ETSI19602.STATUS_DETERMINATION_APPROACH)
            checkNotNull(schemeTypeCommunityRules, ETSI19602.SCHEME_TYPE_COMMUNITY_RULES)
            checkNotNull(schemeTerritory, ETSI19602.SCHEME_TERRITORY)
            checkNotNull(policyOrLegalNotice, ETSI19602.POLICY_OR_LEGAL_NOTICE)
        }

    public companion object {
        @OptIn(ExperimentalTime::class)
        public fun implicit(
            sequenceNumber: Int = ETSI19602.INITIAL_SEQUENCE_NUMBER,
            type: LoTEType? = null,
            schemeOperatorName: List<MultiLangString>,
            schemeOperatorAddress: SchemeOperatorAddress? = null,
            schemeTerritory: SchemeTerritory? = null,
            historicalInformationPeriod: HistoricalInformationPeriod? = null,
            pointerToOtherLote: MultiLanguageURI? = null,
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
                pointerToOtherLote = pointerToOtherLote,
                listIssueDateTime = listIssueDateTime,
                nextUpdate = nextUpdate,
                distributionPoints = distributionPoints,
                schemeExtensions = schemeExtensions,
            )

        @OptIn(ExperimentalTime::class)
        public fun explicit(
            sequenceNumber: Int = ETSI19602.INITIAL_SEQUENCE_NUMBER,
            type: LoTEType,
            schemeOperatorName: List<MultiLangString>,
            schemeOperatorAddress: SchemeOperatorAddress,
            schemeName: List<MultiLangString>,
            schemeInformationURI: List<MultiLanguageURI>,
            statusDeterminationApproach: String,
            schemeTypeCommunityRules: List<MultiLanguageURI>,
            schemeTerritory: SchemeTerritory,
            policyOrLegalNotice: List<PolicyOrLegalNotice>,
            historicalInformationPeriod: HistoricalInformationPeriod? = null,
            pointerToOtherLote: MultiLanguageURI? = null,
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
                pointerToOtherLote = pointerToOtherLote,
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

// TODO Change LoTEType to URI

@Serializable
@JvmInline
public value class LoTEType(
    public val value: String,
)

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
        requireNonEmpty(electronicAddresses, ETSI19602.SCHEME_OPERATOR_ELECTRONIC_ADDRESS)
    }
}

@Serializable
@JvmInline
public value class SchemeTerritory(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Scheme territory cannot be blank" }
        require(value.length == 2) { "Scheme territory size must be 2 characters" }
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

