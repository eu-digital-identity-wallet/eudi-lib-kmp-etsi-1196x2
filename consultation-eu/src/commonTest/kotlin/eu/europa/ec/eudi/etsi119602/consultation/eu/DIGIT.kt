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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.EUListOfTrustedEntitiesProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.EUMDLProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.EUPIDProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.EUWRPACProvidersList
import eu.europa.ec.eudi.etsi119602.consultation.eu.profile.EUWalletProvidersList
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

object DIGIT {

    private const val EU_PID_PROVIDERS_URL = "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/pid-providers.json"
    private const val EU_WALLET_PROVIDERS_URL =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wallet-providers.json"
    private const val EU_WRPAC_PROVIDERS_URL = "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wrpac-providers.json"
    private const val EU_MDL_PROVIDERS_URL = "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/mdl-providers.json"

    val LISTS: Map<EUListOfTrustedEntitiesProfile, String> by lazy {
        mapOf(
            EUPIDProvidersList to EU_PID_PROVIDERS_URL,
            EUWalletProvidersList to EU_WALLET_PROVIDERS_URL,
            EUWRPACProvidersList to EU_WRPAC_PROVIDERS_URL,
            EUMDLProvidersList to EU_MDL_PROVIDERS_URL,
        )
    }

    suspend fun fetchLists(
        debug: DebugOption = DebugOption.Debug,
        filter: (EUListOfTrustedEntitiesProfile) -> Boolean = { true },
    ): Flow<Pair<EUListOfTrustedEntitiesProfile, String>> =
        LISTS.filter { filter(it.key) }.toList().asFlow().map { (profile, uri) -> profile to LoTEFetcher.fetchLoTE(uri, debug) }
}

enum class DebugOption {
    NoDebug,
    Debug,
}

private object LoTEFetcher {

    suspend fun fetchLoTE(uri: String, debug: DebugOption): String =
        createHttpClient().use { httpClient ->
            httpClient
                .get(uri)
                .bodyAsText()
                .also { if (debug == DebugOption.Debug) println(it) }
        }
}

private fun createHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(JsonSupportDebug)
        }
        install(HttpCookies)
    }

private const val TWO_SPACES = "  "
val JsonSupportDebug: Json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = TWO_SPACES
        encodeDefaults = false
        explicitNulls = false
    }
