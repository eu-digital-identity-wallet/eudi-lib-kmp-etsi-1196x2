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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LoadLoTEFromFileTest {

    @Test
    fun `successfully loads JWT from disk`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uri = "https://example.org/lote.jwt"
        val jwtContent = "test-jwt-content"
        val tempFile = Path("test_lote.jwt")
        val fileSystem = SystemFileSystem
        try {
            // Setup: write a dummy file
            fileSystem.sink(tempFile).buffered().use {
                it.writeString(jwtContent)
            }

            val loader = LoadLoTEFromFile(
                mapUriToPath = { tempFile },
                ioDispatcher = testDispatcher,
                fileSystem = fileSystem,
            )

            val result = loader(uri)
            assertIs<LoadLoTE.Outcome.Loaded<String>>(result)
            assertEquals(jwtContent, result.content)
        } finally {
            if (fileSystem.exists(tempFile)) {
                fileSystem.delete(tempFile)
            }
        }
    }

    @Test
    fun `fails when file does not exist`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uri = "https://example.org/missing.jwt"
        val missingPath = Path("missing.jwt")

        val loader = LoadLoTEFromFile(
            mapUriToPath = { missingPath },
            ioDispatcher = testDispatcher,
            fileSystem = SystemFileSystem,
        )

        val notFound = assertIs<LoadLoTE.Outcome.NotFound>(loader(uri))
        assertIs<FileNotFoundException>(notFound.cause)
    }

    @Test
    fun `respects coroutine cancellation`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val uri = "https://example.org/lote.jwt"
        val tempFile = Path("cancel_test.jwt")
        val fileSystem = SystemFileSystem
        try {
            fileSystem.sink(tempFile).buffered().use {
                it.writeString("some content")
            }

            val loader = LoadLoTEFromFile(
                mapUriToPath = { tempFile },
                ioDispatcher = testDispatcher,
            )

            val job = launch {
                loader(uri)
            }

            job.cancelAndJoin()
            // If the test reaches here without hanging or failing, it respects cancellation
        } finally {
            if (fileSystem.exists(tempFile)) {
                fileSystem.delete(tempFile)
            }
        }
    }
}
