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

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
public data class TrustedEntity(
    @SerialName(ETSI19602.TRUSTED_ENTITY_INFORMATION) @Required val information: TrustedEntityInformation,
    @SerialName(ETSI19602.TRUSTED_ENTITY_SERVICES) @Required val services: List<TrustedEntityService>,
) {
    init {
        requireNonEmpty(services, ETSI19602.TRUSTED_ENTITY_SERVICES)
    }
}

@Serializable
public data class TrustedEntityInformation(
    /**
     * The name of the legal entity responsible for the services
     */
    @SerialName(ETSI19602.TE_NAME) @Required val name: List<MultilanguageString>,
    @SerialName(ETSI19602.TE_ADDRESS) @Required val address: TEAddress,
    @SerialName(ETSI19602.TE_INFORMATION_URI) @Required val informationURI: List<MultiLanguageURI>,
    /**
     * An official registration identifier as registered in official records, where such
     * a registered identifier exists, that unambiguously identifies the trusted entity.
     *
     * It may additionally include any name under which the legal entity, or when applicable the natural person, responsible
     * for the TE operates, in the specific context of the delivery of those of its services which are to be found in this LoTE.
     */
    @SerialName(ETSI19602.TE_TRADE_NAME) val tradeName: List<MultilanguageString>? = null,
) {
    init {
        requireNonEmpty(name, ETSI19602.TE_NAME)
        requireNullOrNonEmpty(tradeName, ETSI19602.TE_TRADE_NAME)
        requireNonEmpty(informationURI, ETSI19602.TE_INFORMATION_URI)
    }
}

@Serializable
public data class TEAddress(
    @SerialName(ETSI19602.TE_POSTAL_ADDRESS) @Required val postalAddresses: List<PostalAddress>,
    @SerialName(ETSI19602.TE_ELECTRONIC_ADDRESS) @Required val electronicAddresses: List<MultiLanguageURI>,
) {
    init {
        requireNonEmpty(postalAddresses, ETSI19602.TE_POSTAL_ADDRESS)
        requireNonEmpty(electronicAddresses, ETSI19602.TE_ELECTRONIC_ADDRESS)
    }
}

@Serializable
public data class TrustedEntityService(
    @SerialName(ETSI19602.SERVICE_INFORMATION) @Required val information: ServiceInformation,
    @SerialName(ETSI19602.SERVICE_HISTORY) val history: ServiceHistory? = null,
)

@Serializable
public data class ServiceInformation(
    @SerialName(ETSI19602.SERVICE_NAME) @Required val name: List<MultilanguageString>,
    @SerialName(ETSI19602.SERVICE_DIGITAL_IDENTITY) @Required val digitalIdentity: ServiceDigitalIdentity,
    @SerialName(ETSI19602.SERVICE_TYPE_IDENTIFIER) val typeIdentifier: URI? = null,
    @SerialName(ETSI19602.SERVICE_STATUS) val status: URI? = null,
    @SerialName(ETSI19602.STATUS_STARTING_TIME) val statusStartingTime: LoTEDateTime? = null,
    @SerialName(ETSI19602.SCHEME_SERVICE_DEFINITION_URI) val schemeServiceDefinitionURI: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SERVICE_SUPPLY_POINTS) val supplyPoints: List<ServiceSupplyPointURI>? = null,
    @SerialName(ETSI19602.SERVICE_DEFINITION_URI) val definitionURI: List<MultiLanguageURI>? = null,
    @SerialName(ETSI19602.SERVICE_INFORMATION_EXTENSIONS) val informationExtensions: JsonArray? = null,
) {
    init {
        requireNonEmpty(name, ETSI19602.SERVICE_NAME)
        requireNullOrNonEmpty(supplyPoints, ETSI19602.SERVICE_SUPPLY_POINTS)
        requireNullOrNonEmpty(schemeServiceDefinitionURI, ETSI19602.SCHEME_SERVICE_DEFINITION_URI)
        requireNullOrNonEmpty(definitionURI, ETSI19602.SERVICE_DEFINITION_URI)
        requireNullOrNonEmpty(informationExtensions, ETSI19602.SERVICE_INFORMATION_EXTENSIONS)
    }
}

public typealias ServiceHistory = JsonElement
public typealias URI = String

@Serializable
public data class ServiceSupplyPointURI(
    @SerialName(ETSI19602.SERVICE_SUPPLY_POINT_URI_VALUE) @Required val uriValue: URI,
    @SerialName(ETSI19602.SERVICE_SUPPLY_POINT_URI_TYPE) val serviceType: URI? = null,
) {
    init {
        requireNotBlank(uriValue, ETSI19602.SERVICE_SUPPLY_POINT_URI_VALUE)
        requireNullOrNotBlank(serviceType, ETSI19602.SERVICE_SUPPLY_POINT_URI_TYPE)
    }
}
