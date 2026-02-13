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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntitiesClaims
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.LoadLoTEAndPointers
import eu.europa.ec.eudi.etsi119602.consultation.ProvisionTrustAnchorsFromLoTEs
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun <CTX : Any> ProvisionTrustAnchorsFromLoTEs.Companion.fromHttp(
    httpClient: HttpClient,
    svcTypePerCtx: SupportedLists<Map<CTX, URI>>,
    constrains: LoadLoTEAndPointers.Constraints,
): ProvisionTrustAnchorsFromLoTEs<CTX> {
    val loadLoTEAndPointers = LoadLoTEAndPointers(constrains) {
        val jwt = httpClient.get(it).bodyAsText()
        val (_, payload) = JwtUtil.headerAndPayload(jwt)
        JsonSupportDebug.decodeFromJsonElement(
            ListOfTrustedEntitiesClaims.serializer(),
            payload,
        )
    }
    return ProvisionTrustAnchorsFromLoTEs(loadLoTEAndPointers, svcTypePerCtx)
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
