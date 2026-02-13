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
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext

/**
 * Known combinations of [VerificationContext] and Service Type Identifiers (for LoTEs)
 * Source are the list profiles specified in [ETSI19602],
 * except the PUB EAA Providers List
 */
public val SupportedLists.Companion.EU: SupportedLists<Map<VerificationContext, URI>>
    get() = SupportedLists(
        pidProviders = mapOf(
            VerificationContext.PID to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.PIDStatus to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_REVOCATION,
        ),
        walletProviders = mapOf(
            VerificationContext.WalletInstanceAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.WalletUnitAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
            VerificationContext.WalletUnitAttestationStatus to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_REVOCATION,
        ),
        wrpacProviders = mapOf(
            VerificationContext.WalletRelyingPartyAccessCertificate to ETSI19602.EU_WRPAC_PROVIDERS_SVC_TYPE_ISSUANCE,
        ),
        wrprcProviders = mapOf(
            VerificationContext.WalletRelyingPartyRegistrationCertificate to ETSI19602.EU_WRPRC_PROVIDERS_SVC_TYPE_ISSUANCE,
        ),
        pubEaaProviders = null,
        qeaProviders = null,
    )
