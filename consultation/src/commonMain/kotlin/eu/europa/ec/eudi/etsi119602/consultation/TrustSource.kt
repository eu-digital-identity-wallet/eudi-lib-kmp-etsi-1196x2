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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.profile.EUListOfTrustedEntitiesProfile

public sealed interface TrustSource {

    public data class LoTE(val loteType: URI, val serviceType: URI) : TrustSource {
        public companion object {
            public operator fun invoke(profile: EUListOfTrustedEntitiesProfile, serviceType: URI): LoTE {
                val loteType = profile.listAndSchemeInformation.type
                require(serviceType in profile.trustedEntities.serviceTypeIdentifiers) {
                    "Service type $serviceType not found in trusted entities of $profile"
                }
                return LoTE(loteType, serviceType)
            }
        }
    }

    public data class LoTL(val lotlType: URI, val serviceType: URI) : TrustSource

    public data class Keystore(val name: String, val filter: (String) -> Boolean) : TrustSource
}
