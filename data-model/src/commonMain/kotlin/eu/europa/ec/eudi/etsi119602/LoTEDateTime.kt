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
package eu.europa.ec.eudi.etsi119602

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.parse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

public typealias LoTEDateTime =
    @Serializable(with = LoTEDateTimeSerializer::class)
    Instant

public object LoTEDateTimeSerializer : KSerializer<Instant> {
    private val isoFormat =
        DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LoTEDateTimeSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        val str = value.format(isoFormat)
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return Instant.parse(str, isoFormat)
    }
}
