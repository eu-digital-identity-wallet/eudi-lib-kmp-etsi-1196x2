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
import eu.europa.ec.eudi.etsi119602.consultation.VerifyJwtSignature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64

val NotValidating = VerifyJwtSignature.nonValidating(
    JsonObject.serializer(),
    ListOfTrustedEntitiesClaims.serializer(),
)

fun <H : Any, P : Any> VerifyJwtSignature.Companion.nonValidating(
    headerSerializer: KSerializer<H>,
    payloadSerializer: KSerializer<P>,
): VerifyJwtSignature<H, P> = VerifyJwtSignature { jwt ->
    println("WARNING: Skipping JWT signature verification")
    val (h, p) = headerAndPayload(headerSerializer, payloadSerializer, jwt)
    VerifyJwtSignature.Outcome.Verified(h, p)
}

inline fun <reified H : Any, reified P : Any> VerifyJwtSignature.Companion.headerAndPayload(
    compact: String,
): Pair<H, P> = headerAndPayload(serializer(), serializer(), compact)

fun <H : Any, P : Any> VerifyJwtSignature.Companion.headerAndPayload(
    headerSerializer: KSerializer<H>,
    payloadSerializer: KSerializer<P>,
    compact: String,
): Pair<H, P> {
    require(compact.isNotBlank()) { "Input must not be empty" }
    return compact.split(".").let { parts ->
        require(parts.size == 3) { "Input must be a JWS in compact form" }
        val header =
            JsonSupportDebug.decodeFromString(
                headerSerializer,
                base64UrlSafeNoPadding.decode(parts[0]).decodeToString(),
            )
        val payload =
            JsonSupportDebug.decodeFromString(
                payloadSerializer,
                base64UrlSafeNoPadding.decode(parts[1]).decodeToString(),
            )
        header to payload
    }
}

private val base64UrlSafeNoPadding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
