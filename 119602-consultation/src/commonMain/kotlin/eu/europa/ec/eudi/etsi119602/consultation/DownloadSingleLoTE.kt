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
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Loads a List of Trusted Entities (LoTE) JWT from a remote HTTP endpoint.
 *
 * @param httpClient the Ktor HTTP client to use for requests
 */
public class DownloadSingleLoTE(
    private val httpClient: HttpClient,
) : LoadLoTE<String> {

    override suspend fun invoke(uri: URI): LoadLoTE.Outcome<String> {
        val httpResponse = httpClient.get(uri) { expectSuccess = false }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                val content = httpResponse.bodyAsText()
                LoadLoTE.Outcome.Loaded(content)
            }

            HttpStatusCode.NotFound -> LoadLoTE.Outcome.NotFound(null)
            else -> error("Unexpected response status: ${httpResponse.status}")
        }
    }
}
