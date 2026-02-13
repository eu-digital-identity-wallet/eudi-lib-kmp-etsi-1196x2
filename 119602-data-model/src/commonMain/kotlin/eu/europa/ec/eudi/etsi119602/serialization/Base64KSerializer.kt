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
package eu.europa.ec.eudi.etsi119602.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

internal open class Base64KSerializer<T>(
    serialName: String,
    internal val base64: Base64,
    private val toByteArray: (T) -> ByteArray,
    private val fromByteArray: (ByteArray) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        val bytes = toByteArray(value)
        val base64String = base64.encode(bytes)
        encoder.encodeString(base64String)
    }

    override fun deserialize(decoder: Decoder): T {
        val base64String = decoder.decodeString()
        val bytes =
            try {
                base64.decode(base64String)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Failed to base64 decode", e)
            }
        return fromByteArray(bytes)
    }
}
