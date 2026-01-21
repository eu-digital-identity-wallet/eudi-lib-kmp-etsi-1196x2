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

import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.profile.EUListOfTrustedEntitiesProfile

public fun interface CreateTrustAnchor<out TRUST_ANCHOR : Any> {

    public operator fun invoke(
        profile: EUListOfTrustedEntitiesProfile,
        serviceType: URI,
    ): (PKIObject) -> TRUST_ANCHOR

    public operator fun invoke(
        verificationContext: IsChainTrusted.VerificationContext,
    ): (PKIObject) -> TRUST_ANCHOR {
        val profile = verificationContext.profile
        val serviceType = verificationContext.serviceType()
        return invoke(profile, serviceType)
    }
}
