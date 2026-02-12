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

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object DIGIT {

    private fun loteUrl(
        lote: String,
    ) = Url("https://acceptance.trust.tech.ec.europa.eu/lists/eudiw/$lote")

    private val EU_PID_PROVIDERS_URL = loteUrl("pid-providers.json")
    private val EU_WALLET_PROVIDERS_URL = loteUrl("wallet-providers.json")
    private val EU_WRPAC_PROVIDERS_URL = loteUrl("wrpac-providers.json")
    private val EU_MDL_PROVIDERS_URL = loteUrl("mdl-providers.json")

    val LOTE_LOCATIONS = LoTELocations(
        pidProviders = EU_PID_PROVIDERS_URL,
        walletProviders = EU_WALLET_PROVIDERS_URL,
        wrpacProviders = EU_WRPAC_PROVIDERS_URL,
        eaaProviders = mapOf(
            "mdl" to EU_MDL_PROVIDERS_URL,
        ),
    )
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
