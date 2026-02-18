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

import eu.europa.ec.eudi.etsi119602.URI
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class LoadLoTEFromHttp(
    private val httpClient: HttpClient,
) : LoadLoTE<String> {

    override suspend fun invoke(uri: URI): LoadLoTE.Outcome<String> {
        val httpResponse = httpClient.get(uri)
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val content = httpResponse.bodyAsText()
                LoadLoTE.Outcome.Loaded(content)
            }

            HttpStatusCode.NotFound -> LoadLoTE.Outcome.NotFound(null)
            else -> throw IllegalStateException("Unexpected response status: ${httpResponse.status}")
        }
    }
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
