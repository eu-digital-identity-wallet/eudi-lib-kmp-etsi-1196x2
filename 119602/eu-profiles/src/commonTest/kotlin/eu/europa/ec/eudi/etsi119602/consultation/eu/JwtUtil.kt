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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntitiesClaims
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

object JwtUtil {
    fun headerAndPayload(compact: String): Pair<JsonObject, JsonObject> {
        require(compact.isNotBlank()) { "Input must not be empty" }
        return compact.split(".").let { parts ->
            require(parts.size == 3) { "Input must be a JWS in compact form" }
            val header =
                JsonSupportDebug.parseToJsonElement(base64UrlSafeNoPadding.decode(parts[0]).decodeToString()).jsonObject
            val payload =
                JsonSupportDebug.parseToJsonElement(base64UrlSafeNoPadding.decode(parts[1]).decodeToString()).jsonObject
            header to payload
        }
    }

    fun loteOfJwt(jwt: String): ListOfTrustedEntities {
        val (_, payload) = headerAndPayload(jwt)
        val claims =
            Json.decodeFromJsonElement(ListOfTrustedEntitiesClaims.serializer(), payload)
        return claims.listOfTrustedEntities
    }

    private val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
}
