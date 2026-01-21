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
import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.profile.EUListOfTrustedEntitiesProfile

public fun interface GetTrustAnchorsByVerificationContext<out TRUST_ANCHOR : Any> {
    public suspend operator fun invoke(verificationContext: VerificationContext): Set<TRUST_ANCHOR>

    public operator fun plus(other: GetTrustAnchorsByVerificationContext<@UnsafeVariance TRUST_ANCHOR>): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
        GetTrustAnchorsByVerificationContext { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    public companion object {
        public fun <TRUST_ANCHOR : Any> usingLoTE(
            getListOfTrustedEntitiesByType: GetListOfTrustedEntitiesByType,
            trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
        ): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
            UsingLote(getListOfTrustedEntitiesByType, trustAnchorCreatorByVerificationContext)
    }
}

internal class UsingLote<out TRUST_ANCHOR : Any>(
    private val getListOfTrustedEntitiesByType: GetListOfTrustedEntitiesByType,
    private val trustAnchorCreatorByVerificationContext: TrustAnchorCreatorByVerificationContext<TRUST_ANCHOR>,
) : GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> {

    override suspend fun invoke(verificationContext: VerificationContext): Set<TRUST_ANCHOR> {
        val profile = verificationContext.profile
        val serviceType = verificationContext.serviceType()
        val listOfTrustedEntities = listOf(profile)
        val trustAnchorCreator = trustAnchorCreatorByVerificationContext(verificationContext)
        return listOfTrustedEntities.trustAnchorsOfType(serviceType, trustAnchorCreator)
    }

    @Throws(IllegalStateException::class)
    private suspend fun listOf(profile: EUListOfTrustedEntitiesProfile): ListOfTrustedEntities {
        val lote = getListOfTrustedEntitiesByType(profile.listAndSchemeInformation.type)
        checkNotNull(lote) { "Unable to find List of Trusted Entities for ${profile.listAndSchemeInformation.type}" }
        with(profile) { lote.ensureCompliesToProfile() }
        return lote
    }

    private fun ListOfTrustedEntities.trustAnchorsOfType(
        serviceType: String,
        trustAnchorCreator: (PKIObject) -> TRUST_ANCHOR,
    ): Set<TRUST_ANCHOR> =
        buildSet {
            entities?.forEach { entity ->
                entity.services.forEach { service ->
                    val srvInformation = service.information
                    if (srvInformation.typeIdentifier == serviceType) {
                        srvInformation.digitalIdentity.x509Certificates?.forEach { pkiObj ->
                            add(trustAnchorCreator(pkiObj))
                        }
                    }
                }
            }
        }
}
