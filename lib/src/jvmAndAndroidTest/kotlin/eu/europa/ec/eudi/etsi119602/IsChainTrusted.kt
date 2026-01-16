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

import eu.europa.ec.eudi.etsi119602.profile.*
import kotlinx.coroutines.flow.first
import java.security.InvalidAlgorithmParameterException
import java.security.cert.*

enum class VerificationCase {
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

sealed interface ChainTrustResult {
    data object Trusted : ChainTrustResult
    data class Untrusted(val cause: Throwable) : ChainTrustResult
}

fun interface IsChainTrusted {
    suspend operator fun invoke(
        chain: List<X509Certificate>,
        verificationCase: VerificationCase,
    ): ChainTrustResult
}

//
// JVM Implementation
//

val VerificationCase.euProfile: EUListOfTrustedEntitiesProfile
    get() = when (this) {
        VerificationCase.EU_WIA,
        VerificationCase.EU_WUA,
        VerificationCase.EU_WUA_STATUS,
        -> EUWalletProvidersList

        VerificationCase.EU_PID,
        VerificationCase.EU_PID_STATUS,
        -> EUPIDProvidersList

        VerificationCase.EU_PUB_EAA,
        VerificationCase.EU_PUB_EAA_STATUS,
        -> EUPubEAAProvidersList

        VerificationCase.EU_WRPRC,
        VerificationCase.EU_WRPRC_STATUS,
        -> EUWRPRCProvidersList

        VerificationCase.EU_WRPAC,
        VerificationCase.EU_WRPAC_STATUS,
        -> EUWRPACProvidersList
    }

fun IsChainTrusted(
    certificateFactory: CertificateFactory,
    certPathValidator: CertPathValidator,
    getTrustAnchors: GetTrustAnchors,
    enableRevocation: Boolean = false,
): IsChainTrusted =
    IsChainTrusted { chain, verification ->
        val anchors = getTrustAnchors(verification)
        val pkixParameters = PKIXParameters(anchors.toSet())
            .apply {
                isRevocationEnabled = enableRevocation
            }
        try {
            val certPath = certificateFactory.generateCertPath(chain)
            certPathValidator.validate(certPath, pkixParameters)
            ChainTrustResult.Trusted
        } catch (e: CertPathValidatorException) {
            ChainTrustResult.Untrusted(e)
        } catch (e: InvalidAlgorithmParameterException) {
            ChainTrustResult.Untrusted(e)
        }
    }

fun interface GetTrustAnchors {
    suspend operator fun invoke(verificationCase: VerificationCase): Set<TrustAnchor>
}

class GetTrustAnchorsFromLoTE(
    private val getLoTE: GetLoTE,
) : GetTrustAnchors {

    override suspend fun invoke(verificationCase: VerificationCase): Set<TrustAnchor> {
        val listOfTrustedEntities = getLoTE(verificationCase)
        val profile = verificationCase.euProfile
        val svcType = verificationCase.svcType
        val certs = listOfTrustedEntities.certificateOf(profile, svcType)
        return certs.map { TrustAnchor(it, null) }.toSet()
    }

    private fun ListOfTrustedEntities.certificateOf(
        profile: EUListOfTrustedEntitiesProfile,
        svcType: SvcType,
    ): Set<X509Certificate> {
        val searchFor = svcType.asIdentifier(profile)
        with(profile) { ensureProfile() }
        return buildSet {
            entities?.forEach { entity ->
                entity.services.forEach { service ->
                    if (service.information.typeIdentifier == searchFor) {
                        service.information.digitalIdentity.x509Certificates?.forEach { add(it.x509Certificate()) }
                    }
                }
            }
        }
    }

    private val VerificationCase.svcType: SvcType
        get() =
            when (this) {
                VerificationCase.EU_WIA -> SvcType.Issuance
                VerificationCase.EU_WUA -> SvcType.Issuance
                VerificationCase.EU_WUA_STATUS -> SvcType.Revocation
                VerificationCase.EU_PID -> SvcType.Issuance
                VerificationCase.EU_PID_STATUS -> SvcType.Revocation
                VerificationCase.EU_PUB_EAA -> SvcType.Issuance
                VerificationCase.EU_PUB_EAA_STATUS -> SvcType.Revocation
                VerificationCase.EU_WRPRC -> SvcType.Issuance
                VerificationCase.EU_WRPRC_STATUS -> SvcType.Revocation
                VerificationCase.EU_WRPAC -> SvcType.Issuance
                VerificationCase.EU_WRPAC_STATUS -> SvcType.Revocation
            }

    private enum class SvcType {
        Issuance,
        Revocation,
        ;

        fun asIdentifier(p: EUListOfTrustedEntitiesProfile): URI {
            return p.trustedEntities.serviceTypeIdentifiers.first { it.endsWith(name) }
        }
    }
}

fun interface GetLoTE {
    suspend operator fun invoke(verificationCase: VerificationCase): ListOfTrustedEntities
}

class DownloadDIGITLoTE : GetLoTE {

    override suspend fun invoke(verificationCase: VerificationCase): ListOfTrustedEntities {
        val profile = verificationCase.euProfile
        val (_, jwt) = DIGIT.fetchLists { it == profile }.first()
        return JwtUtil.loteOfJwt(jwt)
    }
}
