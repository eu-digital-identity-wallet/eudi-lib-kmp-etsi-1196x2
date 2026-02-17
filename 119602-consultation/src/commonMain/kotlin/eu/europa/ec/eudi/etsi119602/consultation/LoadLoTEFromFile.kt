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

import eu.europa.ec.eudi.etsi119602.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * Loads a List of Trusted Entities (LoTE) JWT from disk.
 *
 * @property mapUriToPath maps the LoTE URI to a local [Path]
 * @property fileSystem the filesystem to use for reading the file, defaults to [SystemFileSystem]
 * @property ioDispatcher the dispatcher used for reading the file, defaults to [Dispatchers.IO]
 */
public class LoadLoTEFromFile(
    private val mapUriToPath: (URI) -> Path,
    private val fileSystem: FileSystem = SystemFileSystem,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LoadLoTE<String> {

    private val actualIoDispatcher = ioDispatcher.limitedParallelism(1)

    @Throws(IOException::class)
    override suspend operator fun invoke(uri: URI): LoadLoTE.Outcome<String> =
        try {
            val path = mapUriToPath(uri)
            val content = readJwt(path)
            LoadLoTE.Outcome.Loaded(content)
        } catch (e: FileNotFoundException) {
            LoadLoTE.Outcome.NotFound(e)
        }

    @Throws(FileNotFoundException::class, IOException::class)
    internal suspend fun readJwt(path: Path): String = withContext(actualIoDispatcher) {
        currentCoroutineContext().ensureActive()
        fileSystem.source(path).buffered().use { source ->
            source.readString()
        }
    }
}
