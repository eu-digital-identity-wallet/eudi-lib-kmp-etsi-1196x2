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
            issuance = VerificationContext.PID,
            revocation = VerificationContext.PIDStatus,
        ),
        walletProviders = UseCase.WalletAttestation.loteMeta(
            issuance = VerificationContext.WalletProviderAttestation,
            revocation = VerificationContext.WalletProviderAttestationStatus,
        ),

        wrpacProviders = UseCase.WRPAC.loteMeta(
            issuance = VerificationContext.WalletRelyingPartyAccessCertificate,
            revocation = null,
        ),

        wrprcProviders = UseCase.WRPC.loteMeta(
            issuance = VerificationContext.WalletRelyingPartyRegistrationCertificate,
            revocation = VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
        ),
        pubEaaProviders = null,
        qeaProviders = null,
    )

private data class UseCase(
    val loteProfile: EUListOfTrustedEntitiesProfile,
    val issuanceCertificateProfile: CertificateProfile?,
    val revocationCertificateProfile: CertificateProfile?,
) {
    companion object {
        val PID = pidUseCase()
        val WalletAttestation = walletAttestationUseCase()
        val WRPAC = wrpacUseCase()
        val WRPC = wrpcUseCase()
        val PUBEAA = pubEAAUseCase()
        val MDL = mdlUseCase()

        private fun pidUseCase(at: Instant? = null): UseCase =
            UseCase(
                loteProfile = EUPIDProvidersList,
                issuanceCertificateProfile = pidSigningCertificateProfile(at = at),
                null,
            )

        private fun pubEAAUseCase(): UseCase =
            UseCase(EUPubEAAProvidersList, issuanceCertificateProfile = null, revocationCertificateProfile = null)

        private fun walletAttestationUseCase(at: Instant? = null): UseCase =
            UseCase(
                EUWalletProvidersList,
                issuanceCertificateProfile = walletProviderSigningCertificateProfile(at = at),
                revocationCertificateProfile = null,
            )

        private fun wrpacUseCase(at: Instant? = null): UseCase =
            UseCase(
                EUWRPACProvidersList,
                issuanceCertificateProfile = wrpAccessCertificateProfile(at = at),
                revocationCertificateProfile = null,
            )

        private fun wrpcUseCase(): UseCase =
            UseCase(EUWRPRCProvidersList, issuanceCertificateProfile = null, revocationCertificateProfile = null)

        private fun mdlUseCase(): UseCase =
            UseCase(EUMDLProvidersList, issuanceCertificateProfile = null, revocationCertificateProfile = null)
    }
}

private fun <CTX : Any> UseCase.loteMeta(
    issuance: CTX?,
    revocation: CTX?,
): LotEMeta<CTX> = LotEMeta(
    svcTypePerCtx = svcTypePerCtx(issuance, revocation),
    serviceDigitalIdentityCertificateType = loteProfile.trustedEntities.serviceDigitalIdentityCertificateType,
)

private fun <CTX : Any> UseCase.svcTypePerCtx(
    issuanceCtx: CTX?,
    revocationCtx: CTX?,
): Map<CTX, LotEMeta.SvcAndEEProfile> =
    when (val serviceTypeIdentifiers = loteProfile.trustedEntities.serviceTypeIdentifiers) {
        is ServiceTypeIdentifiers.OneOrMore -> error("Not supported")
        is ServiceTypeIdentifiers.IssuanceAndRevocation -> {
            buildMap {
                issuanceCtx?.let { ctx ->
                    val value = LotEMeta.SvcAndEEProfile(
                        serviceTypeIdentifiers.issuance,
                        issuanceCertificateProfile,
                    )
                    put(ctx, value)
                }
                revocationCtx?.let { ctx ->
                    val value = LotEMeta.SvcAndEEProfile(
                        serviceTypeIdentifiers.revocation,
                        revocationCertificateProfile,
                    )
                    put(ctx, value)
                }
            }
        }
    }
