/*
 * Copyright (c) 2026 European Commission
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

@Serializable
public data class PostalAddress
@Throws(IllegalArgumentException::class)
public constructor(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.POSTAL_ADDRESS_STREET_ADDRESS) @Required val streetAddress: String,
    @SerialName(ETSI19602.POSTAL_ADDRESS_COUNTRY) @Required val country: CountryCode,
    @SerialName(ETSI19602.POSTAL_ADDRESS_LOCALITY) val locality: String? = null,
    @SerialName(ETSI19602.POSTAL_ADDRESS_STATE_OR_PROVINCE) val stateOrProvince: String? = null,
    @SerialName(ETSI19602.POSTAL_ADDRESS_POSTAL_CODE) val postalCode: String? = null,
) {
    init {
        with(Assertions) {
            requireNotBlank(streetAddress, ETSI19602.POSTAL_ADDRESS_STREET_ADDRESS)
            requireNullOrNotBlank(locality, ETSI19602.POSTAL_ADDRESS_LOCALITY)
            requireNullOrNotBlank(stateOrProvince, ETSI19602.POSTAL_ADDRESS_STATE_OR_PROVINCE)
            requireNullOrNotBlank(postalCode, ETSI19602.POSTAL_ADDRESS_POSTAL_CODE)
        }
    }
}
