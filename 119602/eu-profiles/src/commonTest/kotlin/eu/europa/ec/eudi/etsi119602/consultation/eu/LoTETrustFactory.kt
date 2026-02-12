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
import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi119602.consultation.*
import eu.europa.ec.eudi.etsi119602.consultation.SupportedLoTEs.Companion.EU
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.transform
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope

class LoTETrustFactory(
    private val loadLoTE: LoadLoTE,
    private val svcTypePerCtx: SupportedLoTEs<Map<VerificationContext, URI>> = EU,
) {

    companion object {
        suspend fun fromHttp(
            httpClient: HttpClient,
            loteLocationsSupported: SupportedLoTEs<Url>,
            svcTypePerCtx: SupportedLoTEs<Map<VerificationContext, URI>> = EU,
            parallelism: Int = 2,
            constrains: Constraints = Constraints(1, 20),
        ): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> {
            val loadLoTE = LoadLoTE(parallelism, constrains) {
                val jwt = httpClient.get(it).bodyAsText()
                val (_, payload) = JwtUtil.headerAndPayload(jwt)
                JsonSupportDebug.decodeFromJsonElement(ListOfTrustedEntitiesClaims.serializer(), payload)
            }
            return LoTETrustFactory(loadLoTE, svcTypePerCtx).load(loteLocationsSupported)
        }
    }

    private data class LoTECfg(
        val downloadUrl: String,
        val svcTypePerCtx: Map<VerificationContext, URI>,
    )

    suspend fun load(loteLocationsSupported: SupportedLoTEs<Url>): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> =
        coroutineScope {
            var result = GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject>()
            val cfgs = loteLocationsSupported.cfgs()
            for (it in cfgs) {
                val trustProvider = trustProvider(it)
                if (trustProvider != null) result += trustProvider
            }
            result
        }

    private suspend fun trustProvider(
        cfg: LoTECfg,
    ): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject>? {
        val loaded = download(cfg) ?: return null
        val getTrustAnchors = GetTrustAnchorsFromLoTE(loaded)
        return getTrustAnchors.transform(cfg.svcTypePerCtx)
    }

    private suspend fun download(cfg: LoTECfg): LoadedLoTE? {
        val downloadFlow = loadLoTE(cfg.downloadUrl)
        val result = LoTEDownloadResult.collect(downloadFlow)
        return result.loaded()
    }

    private fun LoTEDownloadResult.loaded(): LoadedLoTE? {
        if (downloaded == null) return null
        return LoadedLoTE(
            list = downloaded.lote,
            otherLists = otherLists.map { it.lote },
        )
    }

    private fun SupportedLoTEs<Url>.cfgs(): SupportedLoTEs<LoTECfg> =
        SupportedLoTEs.combine(this, svcTypePerCtx) { url, ctx ->
            LoTECfg(url.toString(), ctx)
        }
}
