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

import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.transform
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

public class ProvisionTrustAnchorsFromLoTEs<CTX : Any, TRUST_ANCHOR : Any>(
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val svcTypePerCtx: SupportedLists<Map<CTX, URI>>,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val createTrustAnchor: (PKIObject) -> TRUST_ANCHOR,
) {

    public suspend operator fun invoke(
        loteLocationsSupported: SupportedLists<String>,
        parallelism: Int = 1,
    ): GetTrustAnchorsForSupportedQueries<CTX, TRUST_ANCHOR> =
        coroutineScope {
            loteLocationsSupported.cfgs().asFlow()
                .map { cfg -> loadLoTEAndCreateTrustAnchorsProvider(cfg) }
                .buffer(parallelism)
                .filterNotNull()
                .reduce { acc, provider -> acc + provider }
        }

    private suspend fun loadLoTEAndCreateTrustAnchorsProvider(
        cfg: LoTECfg<CTX>,
    ): GetTrustAnchorsForSupportedQueries<CTX, TRUST_ANCHOR>? {
        val loaded = loadLoTE(cfg) ?: return null
        val getTrustAnchors = GetTrustAnchorsFromLoTE(loaded, createTrustAnchor)
        return getTrustAnchors.transform(cfg.svcTypePerCtx)
    }

    private suspend fun loadLoTE(cfg: LoTECfg<CTX>): LoadedLoTE? {
        val downloadFlow = loadLoTEAndPointers(cfg.downloadUrl)
        val result = LoTELoadResult.collect(downloadFlow, continueOnProblem)
        return result.loaded()
    }

    private fun LoTELoadResult.loaded(): LoadedLoTE? =
        list?.let { mainList -> LoadedLoTE(list = mainList.lote, otherLists = otherLists.map { it.lote }) }

    private fun SupportedLists<String>.cfgs(): SupportedLists<LoTECfg<CTX>> =
        SupportedLists.combine(this, svcTypePerCtx) { url, ctx ->
            LoTECfg(url, ctx)
        }

    private data class LoTECfg<CTX : Any>(
        val downloadUrl: String,
        val svcTypePerCtx: Map<CTX, URI>,
    )

    public companion object {
        public fun <TRUST_ANCHOR : Any> eudiw(
            loadLoTEAndPointers: LoadLoTEAndPointers,
            svcTypePerCtx: SupportedLists<Map<VerificationContext, URI>> = SupportedLists.EU,
            continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
            createTrustAnchor: (PKIObject) -> TRUST_ANCHOR,
        ): ProvisionTrustAnchorsFromLoTEs<VerificationContext, TRUST_ANCHOR> =
            ProvisionTrustAnchorsFromLoTEs(
                loadLoTEAndPointers,
                svcTypePerCtx,
                continueOnProblem,
                createTrustAnchor,
            )
    }
}
