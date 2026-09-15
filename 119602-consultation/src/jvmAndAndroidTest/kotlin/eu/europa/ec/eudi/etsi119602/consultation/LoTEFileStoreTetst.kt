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
            nextUpdate = now.plus(10.seconds)
        )

        val metadataSerialized = Json.encodeToString(metadata)
        val metadataDeserialized = Json.decodeFromString<LoTEFileMetadata>(metadataSerialized)

        assertEquals(now.toEpochMilliseconds(), metadataDeserialized.loadedAt.toEpochMilliseconds())
        assertEquals(now.plus(20.seconds).toEpochMilliseconds(), metadataDeserialized.expiresAt.toEpochMilliseconds())
        assertEquals(now.plus(10.seconds).toEpochMilliseconds(), metadataDeserialized.nextUpdate?.toEpochMilliseconds())
    }

}