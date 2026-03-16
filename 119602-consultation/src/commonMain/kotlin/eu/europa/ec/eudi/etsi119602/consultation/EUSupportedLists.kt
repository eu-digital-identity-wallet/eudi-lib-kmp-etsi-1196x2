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

import eu.europa.ec.eudi.etsi119602.ETSI19602
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.eu.*
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import kotlin.time.Instant

/**
 * Known combinations of [VerificationContext] and Service Type Identifiers (for LoTEs)
 * Source are the list profiles specified in [ETSI19602],
 * except the PUB EAA Providers List
 */
public fun SupportedLists.Companion.eu(): SupportedLists<LotEMeta<VerificationContext>> =
    SupportedLists(
        pidProviders = UseCase.PID.loteMeta(
            issuance = setOf(VerificationContext.PID),
            revocation = setOf(VerificationContext.PIDStatus),
        ),
        walletProviders = UseCase.WalletAttestation.loteMeta(
            issuance = setOf(
                VerificationContext.WalletInstanceAttestation,
                VerificationContext.WalletUnitAttestation,
            ),
            revocation = setOf(VerificationContext.WalletUnitAttestationStatus),
        ),

        wrpacProviders = UseCase.WRPAC.loteMeta(
            issuance = setOf(VerificationContext.WalletRelyingPartyAccessCertificate),
            revocation = emptySet(),
        ),

        wrprcProviders = UseCase.WRPC.loteMeta(
            issuance = setOf(VerificationContext.WalletRelyingPartyRegistrationCertificate),
            revocation = emptySet(),
        ),
        pubEaaProviders = null,
        qeaProviders = null,
    )

private data class UseCase(
    val loteProfile: EUListOfTrustedEntitiesProfile,
    val endEntityCertificateProfile: CertificateProfile?,
) {
    companion object {
        val PID = pidUseCase()
        val WalletAttestation = walletAttestationUseCase()
        val WRPAC = wrpacUseCase()
        val WRPC = wrpcUseCase()
        val PUBEAA = pubEAAUseCase()
        val MDL = mdlUseCase()

        private fun pidUseCase(at: Instant? = null): UseCase =
            UseCase(loteProfile = EUPIDProvidersList, endEntityCertificateProfile = pidProviderCertificateProfile(at = at))

        private fun pubEAAUseCase(): UseCase =
            UseCase(EUPubEAAProvidersList, endEntityCertificateProfile = null)
        private fun walletAttestationUseCase(at: Instant? = null): UseCase =
            UseCase(EUWalletProvidersList, walletProviderCertificateProfile(at = at))

        private fun wrpacUseCase(at: Instant? = null, policy: String? = null): UseCase =
            UseCase(EUWRPACProvidersList, wrpAccessCertificateProfile(at = at, policy = policy))

        private fun wrpcUseCase(): UseCase =
            UseCase(EUWRPRCProvidersList, endEntityCertificateProfile = null)

        private fun mdlUseCase(): UseCase =
            UseCase(EUMDLProvidersList, endEntityCertificateProfile = null)
    }
}

private fun <CTX : Any> UseCase.loteMeta(
    issuance: Set<CTX>,
    revocation: Set<CTX>,
): LotEMeta<CTX> = LotEMeta(
    svcTypePerCtx = loteProfile.svcTypePerCtx(issuance, revocation),
    serviceDigitalIdentityCertificateType = loteProfile.trustedEntities.serviceDigitalIdentityCertificateType,
    endEntityCertificateProfile = endEntityCertificateProfile,
)

private fun <CTX : Any> EUListOfTrustedEntitiesProfile.svcTypePerCtx(
    issuance: Set<CTX>,
    revocation: Set<CTX>,
): Map<CTX, URI> =
    when (val ss = trustedEntities.serviceTypeIdentifiers) {
        is ServiceTypeIdentifiers.OneOrMore -> error("Not supported")
        is ServiceTypeIdentifiers.IssuanceAndRevocation -> {
            buildMap {
                issuance.forEach { put(it, ss.issuance) }
                revocation.forEach { put(it, ss.revocation) }
            }
        }
    }
