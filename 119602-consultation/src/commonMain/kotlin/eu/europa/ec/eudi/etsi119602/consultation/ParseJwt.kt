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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64

public fun interface ParseJwt<out Header : Any, out Payload : Any> {
    public operator fun invoke(jwt: String): Outcome<Header, Payload>

    public sealed interface Outcome<out H : Any, out P : Any> {
        public data class Parsed<out H : Any, out P : Any>(val header: H, val payload: P) : Outcome<H, P>
        public data class ParseFailed(val cause: Throwable?) : Outcome<Nothing, Nothing>
    }

    public companion object {
        public val DefaultJson: Json = Json { ignoreUnknownKeys = true }

        public inline operator fun <reified H : Any, reified P : Any> invoke(
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = invoke(serializer(), serializer(), json)

        public operator fun <H : Any, P : Any> invoke(
            headerSerializer: KSerializer<H>,
            payloadSerializer: KSerializer<P>,
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = ParseJwt { compact ->
            try {
                require(compact.isNotBlank()) { "Input must not be empty" }
                compact.split(".").let { parts ->
                    require(parts.size == 3) { "Input must be a JWS in compact form" }
                    val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                    val header =
                        json.decodeFromString(
                            headerSerializer,
                            base64UrlSafeNoPadding.decode(parts[0]).decodeToString(),
                        )

                    val payload =
                        json.decodeFromString(
                            payloadSerializer,
                            base64UrlSafeNoPadding.decode(parts[1]).decodeToString(),
                        )

                    Outcome.Parsed(header, payload)
                }
            } catch (e: Exception) {
                Outcome.ParseFailed(e)
            }
        }
    }
}
