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

import eu.europa.ec.eudi.etsi119602.consultation.eu.LoTEFetcher.fetchLoTE
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

object DIGIT {

    internal const val EU_PID_PROVIDERS_URL = "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/pid-providers.json"
    private const val EU_WALLET_PROVIDERS_URL =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wallet-providers.json"
    private const val EU_WRPAC_PROVIDERS_URL =
        "https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/wrpac-providers.json"
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
        createHttpClient().use { httpClient ->
            LISTS.filter { filter(it.key) }.toList().asFlow()
                .map { (profile, uri) -> profile to httpClient.fetchLoTE(uri, debug) }
        }
}

enum class DebugOption {
    NoDebug,
    Debug,
}

internal object LoTEFetcher {

    suspend fun HttpClient.fetchLoTE(uri: String, debug: DebugOption): String =
        get(uri)
            .bodyAsText()
            .also { if (debug == DebugOption.Debug) println(it) }
}

internal fun createHttpClient(): HttpClient =
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
