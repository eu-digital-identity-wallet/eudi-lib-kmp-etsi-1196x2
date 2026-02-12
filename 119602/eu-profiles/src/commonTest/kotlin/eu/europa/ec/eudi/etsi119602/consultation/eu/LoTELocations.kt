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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import io.ktor.http.*

data class LoTELocations(
    val pidProviders: Url? = null,
    val walletProviders: Url? = null,
    val wrpacProviders: Url? = null,
    val wrprcProviders: Url? = null,
    val eaaProviders: Map<String, Url> = emptyMap(),
)

fun LoTELocations.ctxs(): Map<Set<VerificationContext>, Url> =
    buildMap {
        pidProviders?.let {
            val ctxs = setOf(
                VerificationContext.PID,
                VerificationContext.PIDStatus,
            )
            put(ctxs, it)
        }
        walletProviders?.let {
            val ctxs = setOf(
                VerificationContext.WalletInstanceAttestation,
                VerificationContext.WalletUnitAttestation,
                VerificationContext.WalletUnitAttestationStatus,
            )
            put(ctxs, it)
        }
        wrpacProviders?.let {
            val ctxs = setOf(
                VerificationContext.WalletRelyingPartyAccessCertificate,
            )
            put(ctxs, it)
        }
        wrprcProviders?.let {
            val ctxs = setOf(
                VerificationContext.WalletRelyingPartyRegistrationCertificate,
            )
            put(ctxs, it)
        }
        eaaProviders.forEach { (useCase, url) ->
            val ctxs = setOf(
                VerificationContext.EAA(useCase),
                VerificationContext.EAAStatus(useCase),
            )
            put(ctxs, url)
        }
    }

fun svcTypeOf(ctx: VerificationContext): URI {
    fun EUListOfTrustedEntitiesProfile.issuanceAndRevocation() =
        trustedEntities.serviceTypeIdentifiers as ServiceTypeIdentifiers.IssuanceAndRevocation
    return when (ctx) {
        VerificationContext.PID -> EUPIDProvidersList.issuanceAndRevocation().issuance
        VerificationContext.PIDStatus -> EUPIDProvidersList.issuanceAndRevocation().revocation
        VerificationContext.WalletInstanceAttestation,
        VerificationContext.WalletUnitAttestation,
        -> EUWalletProvidersList.issuanceAndRevocation().issuance

        VerificationContext.WalletUnitAttestationStatus -> EUWalletProvidersList.issuanceAndRevocation().revocation
        VerificationContext.WalletRelyingPartyAccessCertificate -> EUWRPRCProvidersList.issuanceAndRevocation().issuance
        VerificationContext.EAA("mdl") -> EUMDLProvidersList.issuanceAndRevocation().issuance
        VerificationContext.EAAStatus("mdl") -> EUMDLProvidersList.issuanceAndRevocation().revocation
        else -> error("Unsupported context: $ctx")
    }
}
