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

import eu.europa.ec.eudi.etsi119602.consultation.ParseJwt.Companion.DefaultJson
import eu.europa.ec.eudi.etsi119602.consultation.ParseJwt.Outcome
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

        public inline fun <reified H : Any, reified P : Any> compact(
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = compact(serializer(), serializer(), json)

        public fun <H : Any, P : Any> compact(
            headerSerializer: KSerializer<H>,
            payloadSerializer: KSerializer<P>,
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = ParseCompactJwt(headerSerializer, payloadSerializer, json)

        public inline fun <reified H : Any, reified P : Any> jwsJson(
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = jwsJson(serializer(), serializer(), json)

        public fun <H : Any, P : Any> jwsJson(
            headerSerializer: KSerializer<H>,
            payloadSerializer: KSerializer<P>,
            json: Json = DefaultJson,
        ): ParseJwt<H, P> = ParseJwsJson(headerSerializer, payloadSerializer, json)

        /**
         * Parse JWT in compact form or JWS JSON format (General, not flat)
         */
        public operator fun <H : Any, P : Any> invoke(
            headerSerializer: KSerializer<H>,
            payloadSerializer: KSerializer<P>,
            json: Json = DefaultJson,
        ): ParseJwt<H, P> {
            val compact = compact(headerSerializer, payloadSerializer, json)
            val jwsJson = jwsJson(headerSerializer, payloadSerializer, json)
            return compact.or(jwsJson)
        }
    }
}

private infix fun <H : Any, P : Any> ParseJwt<H, P>.or(that: ParseJwt<H, P>): ParseJwt<H, P> =
    ParseWithAlternative(this, that)

private class ParseWithAlternative<H : Any, P : Any>(
    val first: ParseJwt<H, P>,
    val second: ParseJwt<H, P>,

) : ParseJwt<H, P> {
    override fun invoke(jwt: String): Outcome<H, P> =
        when (val o1 = first.invoke(jwt)) {
            is Outcome.Parsed -> Outcome.Parsed(o1.header, o1.payload)
            is Outcome.ParseFailed -> when (
                val o2 =
                    second.invoke(jwt)
            ) {
                is Outcome.Parsed -> Outcome.Parsed(o2.header, o2.payload)
                is Outcome.ParseFailed -> {
                    val cause = IllegalArgumentException("Failed to parse JWT to the expected payload")
                    o1.cause?.let { cause.addSuppressed(it) }
                    o2.cause?.let { cause.addSuppressed(it) }
                    Outcome.ParseFailed(cause)
                }
            }
        }
}

private class ParseCompactJwt<H : Any, P : Any>(
    val headerSerializer: KSerializer<H>,
    val payloadSerializer: KSerializer<P>,
    json: Json = DefaultJson,
) : ParseJwt<H, P> {
    private val decode: Decode = Decode(json)

    override fun invoke(jwt: String): Outcome<H, P> =
        try {
            require(jwt.isNotBlank()) { "Input must not be empty" }
            jwt.split(".").let { parts ->
                require(parts.size == 3) { "Input must be a JWS in compact form" }
                val header = decode(headerSerializer, parts[0])
                val payload = decode(payloadSerializer, parts[1])
                Outcome.Parsed(header, payload)
            }
        } catch (e: Exception) {
            Outcome.ParseFailed(e)
        }
}

private class ParseJwsJson<H : Any, P : Any>(
    val headerSerializer: KSerializer<H>,
    val payloadSerializer: KSerializer<P>,
    val json: Json,
) : ParseJwt<H, P> {

    @Serializable
    private data class JwsJson(
        @SerialName("payload") @Required val payload: String,
        @SerialName("signatures") val signatures: List<Signature>,
    ) {
        init {
            require(signatures.isNotEmpty()) { "Signatures must not be empty" }
        }

        @Serializable
        data class Signature(
            @SerialName("header") val header: JsonObject? = null,
            @SerialName("protected") val protected: String,
            @SerialName("signature") val signature: String,
        )
    }

    val decode: Decode = Decode(json)

    override fun invoke(jwt: String): Outcome<H, P> =
        try {
            val jwsJson = json.decodeFromString<JwsJson>(jwt)
            val (_, protected, _) = jwsJson.signatures.first()
            val header = decode(headerSerializer, protected)
            val payload = decode(payloadSerializer, jwsJson.payload)
            Outcome.Parsed(header, payload)
        } catch (e: Exception) {
            Outcome.ParseFailed(e)
        }
}

private class Decode(val json: Json) {
    val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    operator fun <T> invoke(serializer: KSerializer<T>, base64Encoded: String): T {
        val decoded = base64UrlSafeNoPadding.decode(base64Encoded).decodeToString()
        return json.decodeFromString(serializer, decoded)
    }
}
