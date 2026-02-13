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

import eu.europa.ec.eudi.etsi119602.consultation.EU
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext

object DIGIT {

    private fun loteUrl(lote: String): String =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/$lote"

    private val EU_PID_PROVIDERS_URL = loteUrl("pid-providers.json")
    private val EU_WALLET_PROVIDERS_URL = loteUrl("wallet-providers.json")
    private val EU_WRPAC_PROVIDERS_URL = loteUrl("wrpac-providers.json")
    private val EU_MDL_PROVIDERS_URL = loteUrl("mdl-providers.json")

    val LOTE_LOCATIONS = SupportedLists(
        pidProviders = EU_PID_PROVIDERS_URL,
        walletProviders = EU_WALLET_PROVIDERS_URL,
        wrpacProviders = EU_WRPAC_PROVIDERS_URL,
        eaaProviders = mapOf(
            "mdl" to EU_MDL_PROVIDERS_URL,
        ),
    )

    val SVC_TYPE_PER_CTX = SupportedLists.EU.copy(
        eaaProviders = mapOf(
            "mdl" to mapOf(
                VerificationContext.EAA("mdl") to EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE,
                VerificationContext.EAAStatus("mdl") to EUMDLProvidersListSpec.SVC_TYPE_REVOCATION,
            ),
        ),
    )
}
