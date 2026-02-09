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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.eu.*
import eu.europa.ec.eudi.etsi1196x2.consultation.*

public class GetTrustAnchorsFromLoTE(
    private val lote: ListOfTrustedEntities,
) : GetTrustAnchors<URI, PKIObject> {

    override suspend fun invoke(query: URI): NonEmptyList<PKIObject>? {
        val certs = lote.entities?.flatMap { trustedEntity ->
            trustedEntity.services
                .filter { it.information.typeIdentifier == query }
                .flatMap { it.information.digitalIdentity.x509Certificates.orEmpty() }
        }
        return if (certs == null) {
            null
        } else {
            NonEmptyList.nelOrNull(certs)
        }
    }
}

public fun GetTrustAnchorsForSupportedQueries.Companion.usingLoTE(
    lotesPerProfile: Map<EUListOfTrustedEntitiesProfile, ListOfTrustedEntities>,
): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> {
    var result =
        GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject>()

    lotesPerProfile.forEach { (profile, lote) ->

        with(profile) {
            lote.ensureCompliesToProfile()
        }
        val ctx = profile.ctx()
        if (ctx.isNotEmpty()) {
            result += GetTrustAnchorsFromLoTE(lote).transform(ctx)
        }
    }
    return result
}

private fun EUListOfTrustedEntitiesProfile.ctx(): Map<VerificationContext, URI> =
    buildMap {
        val issuanceSvcType = issuanceSvcType()
        val revocationSvcType = revocationSvcType()
        when (this@ctx) {
            EUPIDProvidersList -> {
                issuanceSvcType
                    ?.let { put(VerificationContext.PID, it) }
                revocationSvcType
                    ?.let { put(VerificationContext.PIDStatus, it) }
            }

            EUWalletProvidersList -> {
                issuanceSvcType
                    ?.let {
                        put(VerificationContext.WalletInstanceAttestation, it)
                        //        put(VerificationContext.WalletUnitAttestation, it)
                    }
                revocationSvcType?.let { put(VerificationContext.WalletUnitAttestationStatus, it) }
            }

            EUWRPACProvidersList -> {
                issuanceSvcType
                    ?.let { put(VerificationContext.WalletRelyingPartyAccessCertificate, it) }
            }

            EUWRPRCProvidersList -> {
                issuanceSvcType?.let { put(VerificationContext.WalletRelyingPartyRegistrationCertificate, it) }
            }

            EUMDLProvidersList -> {
                issuanceSvcType?.let { put(VerificationContext.EAA("mdl"), it) }
                revocationSvcType?.let { put(VerificationContext.EAAStatus("mdl"), it) }
            }
            else -> {}
        }
    }

private fun EUListOfTrustedEntitiesProfile.issuanceSvcType(): URI? =
    trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith("Issuance") }

private fun EUListOfTrustedEntitiesProfile.revocationSvcType(): URI? =
    trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith("Revocation") }
