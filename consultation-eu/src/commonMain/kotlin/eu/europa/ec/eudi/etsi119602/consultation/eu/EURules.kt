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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.TrustSource
import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.*

public interface EURules {

    public fun VerificationContext.trustSource(usedProfiles: List<URI>): TrustSource? {
        fun EUListOfTrustedEntitiesProfile.svcTypeWithSuffix(suffix: String): URI {
            val svcType = trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith(suffix) }
            return checkNotNull(svcType) { "Unable to find service type for $this with suffix $suffix" }
        }

        fun EUListOfTrustedEntitiesProfile.trustSource(suffix: String): TrustSource.LoTE? {
            val loteType = listAndSchemeInformation.type
            return if (loteType in usedProfiles) {
                TrustSource.LoTE(loteType, svcTypeWithSuffix(suffix))
            } else {
                null
            }
        }

        val issuance = "Issuance"
        val revocation = "Revocation"
        return when (this) {
            VerificationContext.WalletInstanceAttestation -> EUWalletProvidersList.trustSource(issuance)
            VerificationContext.WalletUnitAttestation -> EUWalletProvidersList.trustSource(issuance)
            VerificationContext.WalletUnitAttestationStatus -> EUWalletProvidersList.trustSource(revocation)
            VerificationContext.PID -> EUPIDProvidersList.trustSource(issuance)
            VerificationContext.PIDStatus -> EUPIDProvidersList.trustSource(revocation)
            VerificationContext.PubEAA -> PubEAATrustSource.takeIf { it.lotlType in usedProfiles }
            VerificationContext.PubEAAStatus -> PubEAAStatusTrustSource.takeIf { it.lotlType in usedProfiles }
            VerificationContext.WalletRelyingPartyRegistrationCertificate -> EUWRPRCProvidersList.trustSource(issuance)
            VerificationContext.WalletRelyingPartyAccessCertificate -> EUWRPACProvidersList.trustSource(issuance)
            is VerificationContext.EAA -> {
                when (case) {
                    MOBILE_DRIVING_LICENCE -> EUMDLProvidersList.trustSource(issuance)
                    else -> null
                }
            }

            is VerificationContext.EAAStatus -> {
                when (case) {
                    MOBILE_DRIVING_LICENCE -> EUMDLProvidersList.trustSource(revocation)
                    else -> null
                }
            }
        }
    }

    public companion object : EURules {
        public const val MOBILE_DRIVING_LICENCE: String = "mdl"
        public const val PUB_EAA_LOTL_TYPE: URI = "sample"
        public val PubEAATrustSource: TrustSource.LoTL = TrustSource.LoTL(PUB_EAA_LOTL_TYPE, "bar")
        public val PubEAAStatusTrustSource: TrustSource.LoTL = TrustSource.LoTL(PUB_EAA_LOTL_TYPE, "bar")
    }
}
