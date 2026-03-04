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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateOperations

/**
 * Known combinations of [VerificationContext] and Service Type Identifiers (for LoTEs)
 * Source are the list profiles specified in [ETSI19602],
 * except the PUB EAA Providers List
 */
public fun <CERT : Any> SupportedLists.Companion.eu(
    certificateOperations: CertificateOperations<CERT>,
): SupportedLists<LotEMata<VerificationContext, CERT>> {
    fun EUListOfTrustedEntitiesProfile.loteMeta(
        issuance: Set<VerificationContext>,
        revocation: Set<VerificationContext> = emptySet(),
    ) = loteMeta(certificateOperations, issuance, revocation)

    return SupportedLists(
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
}

private fun <CTX : Any, CERT : Any> EUListOfTrustedEntitiesProfile.loteMeta(
    certificateOperations: CertificateOperations<CERT>,
    issuance: Set<CTX>,
    revocation: Set<CTX>,
): LotEMata<CTX, CERT> = LotEMata(
    svcTypePerCtx = svcTypePerCtx(issuance, revocation),
    directTrust = directTrust,
    certificateConstraints = certificateConstraintsEvaluator(certificateOperations),
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

private val EUListOfTrustedEntitiesProfile.directTrust: Boolean
    get() = when (trustedEntities.chainValidationAlgorithm) {
        ChainValidationAlgorithm.Direct -> true
        ChainValidationAlgorithm.PKIX -> false
    }
