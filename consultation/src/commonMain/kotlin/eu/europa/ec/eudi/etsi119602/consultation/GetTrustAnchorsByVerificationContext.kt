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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.profile.EUListOfTrustedEntitiesProfile

public fun interface GetTrustAnchorsByVerificationContext<out TRUST_ANCHOR : Any> {
    public suspend operator fun invoke(verificationContext: VerificationContext): Set<TRUST_ANCHOR>

    public operator fun plus(other: GetTrustAnchorsByVerificationContext<@UnsafeVariance TRUST_ANCHOR>): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
        GetTrustAnchorsByVerificationContext { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    public companion object {
        public fun <TRUST_ANCHOR : Any> usingLoTE(
            getListByProfile: GetListByProfile,
            trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
        ): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
            UsingLote(getListByProfile, trustAnchorCreatorByVerificationContext)
    }
}

internal class UsingLote<out TRUST_ANCHOR : Any>(
    private val getListByProfile: GetListByProfile,
    private val trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
) : GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> {

    override suspend fun invoke(verificationContext: VerificationContext): Set<TRUST_ANCHOR> {
        val profile = verificationContext.profile
        val serviceType = verificationContext.serviceType()
        val listOfTrustedEntities = listOf(profile)
        return listOfTrustedEntities.trustAnchorsOfType(profile, serviceType)
    }

    @Throws(IllegalStateException::class)
    private suspend fun listOf(profile: EUListOfTrustedEntitiesProfile): ListOfTrustedEntities {
        val lote = getListByProfile(profile)
        return checkNotNull(lote) { "Unable to find List of Trusted Entities for ${profile.listAndSchemeInformation.type}" }
    }

    private fun ListOfTrustedEntities.trustAnchorsOfType(
        profile: EUListOfTrustedEntitiesProfile,
        serviceType: String,
    ): Set<TRUST_ANCHOR> =
        buildSet {
            val createTrustAnchor = trustAnchorCreatorByVerificationContext(profile, serviceType)
            entities?.forEach { entity ->
                entity.services.forEach { service ->
                    val srvInformation = service.information
                    if (srvInformation.typeIdentifier == serviceType) {
                        srvInformation.digitalIdentity.x509Certificates?.forEach { pkiObj ->
                            add(createTrustAnchor(pkiObj))
                        }
                    }
                }
            }
        }
}
