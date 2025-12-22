package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class PostalAddress(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.POSTAL_ADDRESS_STREET_ADDRESS) @Required val streetAddress: String,
    @SerialName(ETSI19602.POSTAL_ADDRESS_LOCALITY) val locality: String? = null,
    @SerialName(ETSI19602.POSTAL_ADDRESS_STATE_OR_PROVINCE) val stateOrProvince: String? = null,
    @SerialName(ETSI19602.POSTAL_ADDRESS_POSTAL_CODE) val postalCode: String? = null,
    @SerialName(ETSI19602.POSTAL_ADDRESS_COUNTRY) @Required val country: CountryCode,
) {
    init {
        requireNotBlank(streetAddress,ETSI19602.POSTAL_ADDRESS_STREET_ADDRESS)
        requireNullOrNotBlank(locality, ETSI19602.POSTAL_ADDRESS_LOCALITY)
        requireNullOrNotBlank(stateOrProvince, ETSI19602.POSTAL_ADDRESS_STATE_OR_PROVINCE)
        requireNullOrNotBlank(postalCode, ETSI19602.POSTAL_ADDRESS_POSTAL_CODE)
    }
}
