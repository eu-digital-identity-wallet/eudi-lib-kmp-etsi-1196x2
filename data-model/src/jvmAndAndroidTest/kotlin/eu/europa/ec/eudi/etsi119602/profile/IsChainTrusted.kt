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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.URI
import java.security.cert.X509Certificate

fun interface IsChainTrusted {

    enum class SignatureVerification {
        EU_WIA,
        EU_WUA,
        EU_WUA_STATUS,
        EU_PID,
        EU_PID_STATUS,
        EU_PUB_EAA,
        EU_PUB_EAA_STATUS,
        EU_WRPRC,
        EU_WRPRC_STATUS,
        EU_WRPAC,
        EU_WRPAC_STATUS,
    }

    sealed interface Outcome {
        data object Trusted : Outcome
        data class Untrusted(val cause: Throwable) : Outcome
    }

    suspend operator fun invoke(
        chain: List<X509Certificate>,
        signatureVerification: SignatureVerification,
    ): Outcome
}

val IsChainTrusted.SignatureVerification.profile: EUListOfTrustedEntitiesProfile
    get() = when (this) {
        IsChainTrusted.SignatureVerification.EU_WIA,
        IsChainTrusted.SignatureVerification.EU_WUA,
        IsChainTrusted.SignatureVerification.EU_WUA_STATUS,
        -> EUWalletProvidersList

        IsChainTrusted.SignatureVerification.EU_PID,
        IsChainTrusted.SignatureVerification.EU_PID_STATUS,
        -> EUPIDProvidersList

        IsChainTrusted.SignatureVerification.EU_PUB_EAA,
        IsChainTrusted.SignatureVerification.EU_PUB_EAA_STATUS,
        -> EUPubEAAProvidersList

        IsChainTrusted.SignatureVerification.EU_WRPRC,
        IsChainTrusted.SignatureVerification.EU_WRPRC_STATUS,
        -> EUWRPRCProvidersList

        IsChainTrusted.SignatureVerification.EU_WRPAC,
        IsChainTrusted.SignatureVerification.EU_WRPAC_STATUS,
        -> EUWRPACProvidersList
    }

fun IsChainTrusted.SignatureVerification.serviceType(): URI {
    val suffix = run {
        val issuance = "Issuance"
        val revocation = "Revocation"
        when (this) {
            IsChainTrusted.SignatureVerification.EU_WIA -> issuance
            IsChainTrusted.SignatureVerification.EU_WUA -> issuance
            IsChainTrusted.SignatureVerification.EU_WUA_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_PID -> issuance
            IsChainTrusted.SignatureVerification.EU_PID_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_PUB_EAA -> issuance
            IsChainTrusted.SignatureVerification.EU_PUB_EAA_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_WRPRC -> issuance
            IsChainTrusted.SignatureVerification.EU_WRPRC_STATUS -> revocation
            IsChainTrusted.SignatureVerification.EU_WRPAC -> issuance
            IsChainTrusted.SignatureVerification.EU_WRPAC_STATUS -> revocation
        }
    }
    val result = profile.trustedEntities.serviceTypeIdentifiers.firstOrNull { it.endsWith(suffix) }
    return checkNotNull(result) { "Unable to find service type for $this with suffix $suffix" }
}
