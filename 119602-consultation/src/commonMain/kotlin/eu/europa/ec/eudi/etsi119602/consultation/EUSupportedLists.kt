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

/**
 * Known combinations of [VerificationContext] and Service Type Identifiers (for LoTEs)
 * Source are the list profiles specified in [ETSI19602],
 * except the PUB EAA Providers List
 */
public fun SupportedLists.Companion.eu(): SupportedLists<LotEMeta<VerificationContext>> =
    SupportedLists(
        pidProviders =
        EUPIDProvidersList.loteMeta(
            issuance = setOf(VerificationContext.PID),
            revocation = setOf(VerificationContext.PIDStatus),
        ),
        walletProviders =
        EUWalletProvidersList.loteMeta(
            issuance = setOf(
                VerificationContext.WalletInstanceAttestation,
                VerificationContext.WalletUnitAttestation,
            ),
            revocation = setOf(VerificationContext.WalletUnitAttestationStatus),
        ),

        wrpacProviders =
        EUWRPACProvidersList.loteMeta(
            issuance = setOf(VerificationContext.WalletRelyingPartyAccessCertificate),
            revocation = emptySet(),
        ),

        wrprcProviders = EUWRPRCProvidersList.loteMeta(
            issuance = setOf(VerificationContext.WalletRelyingPartyRegistrationCertificate),
            revocation = emptySet(),
        ),
        pubEaaProviders = null,
        qeaProviders = null,
    )

private fun <CTX : Any> EUListOfTrustedEntitiesProfile.loteMeta(
    issuance: Set<CTX>,
    revocation: Set<CTX>,
): LotEMeta<CTX> = LotEMeta(
    svcTypePerCtx = svcTypePerCtx(issuance, revocation),
    serviceDigitalIdentityCertificateType = trustedEntities.serviceDigitalIdentityCertificateType,
    endEntityCertificateConstraints = endEntityCertificateConstraints,
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
