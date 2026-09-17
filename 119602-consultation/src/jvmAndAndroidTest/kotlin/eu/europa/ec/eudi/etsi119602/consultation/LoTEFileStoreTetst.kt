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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class LoTEFileStoreTetst {

    @Test
    fun testFileMetadataSerialization() {
        val now = Clock.System.now()
        val metadata = LoTEFileMetadata(
            loadedAt = now,
            expiresAt = now.plus(20.seconds),
            nextUpdate = now.plus(10.seconds),
        )

        val metadataSerialized = Json.encodeToString(metadata)
        val metadataDeserialized = Json.decodeFromString<LoTEFileMetadata>(metadataSerialized)

        assertEquals(now.toEpochMilliseconds(), metadataDeserialized.loadedAt.toEpochMilliseconds())
        assertEquals(now.plus(20.seconds).toEpochMilliseconds(), metadataDeserialized.expiresAt.toEpochMilliseconds())
        assertEquals(now.plus(10.seconds).toEpochMilliseconds(), metadataDeserialized.nextUpdate?.toEpochMilliseconds())
    }
}
