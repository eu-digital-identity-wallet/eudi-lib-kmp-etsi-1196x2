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

import eu.europa.ec.eudi.etsi119602.consultation.eu.EUListOfTrustedEntitiesProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUPubEAAProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceTypeIdentifiers
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext

/**
 * Ready-made [TrustList]s for the EU Lists of Trusted Entities
 *
 */
public object EUTrustLists {

    private val eu: SupportedLists<LotEMeta<VerificationContext>> = SupportedLists.eu()

    /** PID Providers list: [VerificationContext.PID] and [VerificationContext.PIDStatus]. */
    public fun pid(url: String): TrustList = eu.pidProviders.toTrustList(
        url = url,
        issuance = VerificationContext.PID,
        revocation = VerificationContext.PIDStatus,
    )

    /**
     * Wallet Providers list: [VerificationContext.WalletProviderAttestation] and
     * [VerificationContext.WalletOrKeyStorageStatus].
     */
    public fun walletProviders(url: String): TrustList = eu.walletProviders.toTrustList(
        url = url,
        issuance = VerificationContext.WalletProviderAttestation,
        revocation = VerificationContext.WalletOrKeyStorageStatus,
    )

    /** WRPAC Providers list: [VerificationContext.WalletRelyingPartyAccessCertificate] (no status context). */
    public fun wrpac(url: String): TrustList = eu.wrpacProviders.toTrustList(
        url = url,
        issuance = VerificationContext.WalletRelyingPartyAccessCertificate,
        revocation = null,
    )

    /**
     * WRPRC Providers list: [VerificationContext.WalletRelyingPartyRegistrationCertificate] and
     * [VerificationContext.WalletRelyingPartyRegistrationCertificateStatus].
     */
    public fun wrprc(url: String): TrustList = eu.wrprcProviders.toTrustList(
        url = url,
        issuance = VerificationContext.WalletRelyingPartyRegistrationCertificate,
        revocation = VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
    )

    /**
     * PubEAA Providers list: [VerificationContext.PubEAA] and [VerificationContext.PubEAAStatus].
     *
     * Opt-in — not part of [SupportedLists.eu]. Built from [EUPubEAAProvidersList], with no end-entity
     * certificate profile. Note that this profile does not require services to carry X.509
     * certificates; services without certificates contribute no trust anchors.
     */
    public fun pubEaa(url: String): TrustList = EUPubEAAProvidersList.toTrustList(url)

    /**
     * mDL Providers list, registered under [useCase]: validate with
     * `VerificationContext.EAA(useCase)` / `VerificationContext.EAAStatus(useCase)`.
     *
     * Opt-in — not part of [SupportedLists.eu]. Built from [EUMDLProvidersList], with no end-entity
     * certificate profile.
     */
    public fun mdl(url: String, useCase: String): EAATrustList = EAATrustList(useCase, EUMDLProvidersList.toTrustList(url))
}

private fun LotEMeta<VerificationContext>?.toTrustList(
    url: String,
    issuance: VerificationContext,
    revocation: VerificationContext?,
): TrustList {
    val meta = checkNotNull(this) { "SupportedLists.eu() defines no metadata for $issuance" }
    val issuanceSvc = checkNotNull(meta.svcTypePerCtx[issuance]) { "SupportedLists.eu() has no service type for $issuance" }
    val revocationSvc = revocation?.let { ctx ->
        checkNotNull(meta.svcTypePerCtx[ctx]) { "SupportedLists.eu() has no service type for $ctx" }
    }
    require(revocationSvc?.endEntityProfile == null) {
        "TrustList cannot carry an end-entity profile for the revocation service ($revocation)"
    }
    return TrustList(
        url = url,
        issuanceService = issuanceSvc.svcTypeIdentifier.value,
        revocationService = revocationSvc?.svcTypeIdentifier?.value,
        certificateProfile = issuanceSvc.endEntityProfile,
        serviceDigitalIdentityCertificateType = meta.serviceDigitalIdentityCertificateType,
    )
}

private fun EUListOfTrustedEntitiesProfile.toTrustList(url: String): TrustList {
    val serviceTypes = trustedEntities.serviceTypeIdentifiers
    check(serviceTypes is ServiceTypeIdentifiers.IssuanceAndRevocation) {
        "Only issuance/revocation LoTE profiles can be mapped to a TrustList"
    }
    return TrustList(
        url = url,
        issuanceService = serviceTypes.issuance.value,
        revocationService = serviceTypes.revocation.value,
        certificateProfile = null,
        serviceDigitalIdentityCertificateType = trustedEntities.serviceDigitalIdentityCertificateType,
    )
}
