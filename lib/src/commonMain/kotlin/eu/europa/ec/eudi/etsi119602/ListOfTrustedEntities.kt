package eu.europa.ec.eudi.etsi119602

import eu.europa.ec.eudi.etsi119602.ETSI19602
import eu.europa.ec.eudi.etsi119602.ListAndSchemeInformation
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ListOfTrustedEntities(
    @SerialName(ETSI19602.LIST_AND_SCHEME_INFORMATION) @Required val schemeInformation: ListAndSchemeInformation?,
)

@Serializable
public data class ListOfTrustedEntitiesClaims(
    @SerialName(ETSI19602.LOTE) @Required val listOfTrustedEntities: ListOfTrustedEntities,
)
