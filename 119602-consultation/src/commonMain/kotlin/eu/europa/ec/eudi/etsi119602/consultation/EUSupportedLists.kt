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
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUPIDProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUWRPACProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUWRPRCProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUWalletProvidersList
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
): SupportedLists<LotEMata<VerificationContext, CERT>> = SupportedLists(
    pidProviders = LotEMata(
        svcTypePerCtx = mapOf(
            VerificationContext.PID to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.PIDStatus to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_REVOCATION,
        ),
        directTrust = true,
        certificateConstraints = EUPIDProvidersList.certificateConstraintsEvaluator(certificateOperations),
    ),
    walletProviders = LotEMata(
        svcTypePerCtx = mapOf(
            VerificationContext.WalletInstanceAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.WalletUnitAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.WalletUnitAttestationStatus to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_REVOCATION,
        ),
        directTrust = true,
        certificateConstraints = EUWalletProvidersList.certificateConstraintsEvaluator(certificateOperations),
    ),
    wrpacProviders = LotEMata(
        svcTypePerCtx = mapOf(
            VerificationContext.WalletRelyingPartyAccessCertificate to ETSI19602.EU_WRPAC_PROVIDERS_SVC_TYPE_ISSUANCE,
        ),
        directTrust = false,
        certificateConstraints = EUWRPACProvidersList.certificateConstraintsEvaluator(certificateOperations),
    ),
    wrprcProviders = LotEMata(
        svcTypePerCtx = mapOf(
            VerificationContext.WalletRelyingPartyRegistrationCertificate to ETSI19602.EU_WRPRC_PROVIDERS_SVC_TYPE_ISSUANCE,
        ),
        directTrust = false,
        certificateConstraints = EUWRPRCProvidersList.certificateConstraintsEvaluator(certificateOperations),
    ),
    pubEaaProviders = null,
    qeaProviders = null,
)
