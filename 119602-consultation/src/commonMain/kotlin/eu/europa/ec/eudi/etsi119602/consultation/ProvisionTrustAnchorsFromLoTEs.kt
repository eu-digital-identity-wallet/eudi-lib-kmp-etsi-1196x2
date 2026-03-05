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

import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateCertificateConstraint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

public data class LotEMata<CTX, in CERT : Any>(
    val svcTypePerCtx: Map<CTX, URI>,
    val directTrust: Boolean,
    val certificateConstraints: EvaluateCertificateConstraint<CERT>?,
)

public typealias CertInfo = String

public class ProvisionTrustAnchorsFromLoTEs<CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any, CERT : Any>(
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val svcTypePerCtx: SupportedLists<LotEMata<CTX, CERT>>,
    private val extractCertificate: (TRUST_ANCHOR) -> CERT,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
    private val pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
    private val getCertInfo: suspend (CERT) -> CertInfo,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
) {

    public suspend operator fun invoke(
        loteLocationsSupported: SupportedLists<String>,
        parallelism: Int = 1,
    ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> =
        coroutineScope {
            loteLocationsSupported.cfgs().asFlow()
                .map { cfg -> loadLoTEAndCreateTrustAnchorsProvider(cfg) }
                .buffer(parallelism)
                .filterNotNull()
                .fold(ComposeChainTrust.empty()) { acc, provider -> acc + provider }
        }

    private suspend fun loadLoTEAndCreateTrustAnchorsProvider(
        cfg: LoTECfg<CTX, CERT>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>? {
        val loaded = loadLoTE(cfg) ?: return null
        val baseGetTrustAnchors = GetTrustAnchorsFromLoTE(loaded, createTrustAnchors)
        val getTrustAnchors = GetTrustAnchors<URI, TRUST_ANCHOR> { query ->
            baseGetTrustAnchors(query)?.also { anchors ->
                cfg.metadata.certificateConstraints?.let { evaluator ->
                    val violations = mutableMapOf<CertInfo, CertificateConstraintEvaluation.Violated>()
                    for (anchor in anchors.list) {
                        val cert = extractCertificate(anchor)
                        val evaluation = evaluator.invoke(cert)
                        if (evaluation is CertificateConstraintEvaluation.Violated) {
                            val certInfo = getCertInfo(cert)
                            violations[certInfo] = evaluation
                        }
                    }
                    ensureNoViolations(cfg, anchors.list.size, violations)
                }
            }
        }
        val validateCertificateChain =
            if (cfg.metadata.directTrust) directTrust else pkix
        val transformation = cfg.metadata.svcTypePerCtx
        return getTrustAnchors.validator(transformation, validateCertificateChain)
    }

    private suspend fun loadLoTE(cfg: LoTECfg<CTX, CERT>): LoadedLoTE? {
        val downloadFlow = loadLoTEAndPointers(cfg.downloadUrl)
        val result = LoTELoadResult.collect(downloadFlow, continueOnProblem)
        return result.loaded()
    }

    private fun LoTELoadResult.loaded(): LoadedLoTE? =
        list?.let { mainList -> LoadedLoTE(list = mainList.lote, otherLists = otherLists.map { it.lote }) }

    private fun SupportedLists<String>.cfgs(): SupportedLists<LoTECfg<CTX, CERT>> =
        SupportedLists.combine(this, svcTypePerCtx) { url, ctx ->
            LoTECfg(url, ctx)
        }

    private fun ensureNoViolations(
        cfg: LoTECfg<CTX, CERT>,
        anchorsNo: Int,
        violationsPerCert: Map<CertInfo, CertificateConstraintEvaluation.Violated>,
    ) {
        check(violationsPerCert.isEmpty()) {
            buildString {
                appendLine("Profile violations on ${violationsPerCert.size} / $anchorsNo anchors")
                appendLine("- Download URL: ${cfg.downloadUrl}")
                for ((certInfo, violated) in violationsPerCert) {
                    appendLine("- Certificate: $certInfo")
                    appendLine("- Violations: ")
                    violated.violations.forEachIndexed { index, violation ->
                        appendLine("  ${index + 1}. ${violation.reason}")
                    }
                }
            }
        }
    }
    private data class LoTECfg<CTX : Any, CERT : Any>(
        val downloadUrl: String,
        val metadata: LotEMata<CTX, CERT>,
    )

    public companion object {
        public fun <CHAIN : Any, TRUST_ANCHOR : Any, CERT : Any> eudiw(
            loadLoTEAndPointers: LoadLoTEAndPointers,
            svcTypePerCtx: SupportedLists<LotEMata<VerificationContext, CERT>>,
            extractCertificate: (TRUST_ANCHOR) -> CERT,
            continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
            directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
            pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
            getCertInfo: suspend (CERT) -> CertInfo,
            createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
        ): ProvisionTrustAnchorsFromLoTEs<CHAIN, VerificationContext, TRUST_ANCHOR, CERT> =
            ProvisionTrustAnchorsFromLoTEs(
                loadLoTEAndPointers,
                svcTypePerCtx,
                extractCertificate,
                continueOnProblem,
                directTrust,
                pkix,
                getCertInfo,
                createTrustAnchors,
            )
    }
}
