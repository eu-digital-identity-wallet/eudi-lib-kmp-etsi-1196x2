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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateCertificateConstraint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Metadata associated with a LoTE configuration.
 *
 * @param svcTypePerCtx mapping of contexts to service type URIs
 * @param directTrust whether to use direct trust validation
 * @param certificateConstraints optional certificate constraints to validate
 */
public data class LotEMata<CTX, in CERT : Any>(
    val svcTypePerCtx: Map<CTX, URI>,
    val directTrust: Boolean,
    val certificateConstraints: EvaluateCertificateConstraint<CERT>?,
)

public class ProvisionTrustAnchorsFromLoTEs<CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any, CERT : Any>(
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
    private val extractCertificate: (TRUST_ANCHOR) -> CERT,
    private val getCertInfo: suspend (CERT) -> String = { it.toString() },
    private val directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
    private val pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val svcTypePerCtx: SupportedLists<LotEMata<CTX, CERT>>,
) {

    public fun nonCached(loteLocationsSupported: SupportedLists<String>): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> =
        loteLocationsSupported.cfgs().map { cfg ->
            val getTrustAnchors = createGetTrustAnchorsFromLoTE(cfg)
            createValidator(cfg, getTrustAnchors)
        }.compose()

    public fun cached(
        disposableScope: DisposableScope,
        loteLocationsSupported: SupportedLists<String>,
        ttl: Duration,
        cacheDispatcher: CoroutineDispatcher = Dispatchers.Default,
        clock: Clock = Clock.System,
    ): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> {
        val args = CacheArguments(cacheDispatcher, clock, ttl)
        val sources =
            loteLocationsSupported.cfgs().associateWith { cfg -> createGetTrustAnchorsFromLoTE(cfg, args) }
        with(disposableScope) {
            sources.values.forEach { it.bind() }
        }

        val composeChainTrust =
            sources.map { (cfg, getTrustAnchors) -> createValidator(cfg, getTrustAnchors) }
                .compose()
        return composeChainTrust
    }

    private fun createValidator(
        cfg: LoTECfg<CTX, CERT>,
        getTrustAnchors: GetTrustAnchors<URI, TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
        val certificateChainValidator = certificateChainValidator(cfg)
        val transformation = cfg.metadata.svcTypePerCtx
        return getTrustAnchors.validator(transformation, certificateChainValidator)
    }

    private fun createGetTrustAnchorsFromLoTE(
        cfg: LoTECfg<CTX, CERT>,
        args: CacheArguments,
    ): GetTrustAnchorsCachedSource<URI, TRUST_ANCHOR> {
        val getTrustAnchors = createGetTrustAnchorsFromLoTE(cfg)
        return getTrustAnchors.cached(args, cfg.metadata.svcTypePerCtx.size)
    }

    private fun createGetTrustAnchorsFromLoTE(
        cfg: LoTECfg<CTX, CERT>,
    ): GetTrustAnchors<URI, TRUST_ANCHOR> =
        GetTrustAnchorsFromLoTE(
            loTEDownloadUrl = cfg.downloadUrl,
            certificateConstraints = cfg.metadata.certificateConstraints,
            loadLoTEAndPointers = loadLoTEAndPointers,
            continueOnProblem = continueOnProblem,
            createTrustAnchors = createTrustAnchors,
            extractCertificate = extractCertificate,
            getCertInfo = getCertInfo,
        )

    private fun certificateChainValidator(
        cfg: LoTECfg<CTX, CERT>,
    ): ValidateCertificateChain<CHAIN, TRUST_ANCHOR> =
        if (cfg.metadata.directTrust) directTrust else pkix

    private fun GetTrustAnchors<URI, TRUST_ANCHOR>.cached(
        args: CacheArguments,
        expectedQueries: Int,
    ): GetTrustAnchorsCachedSource<URI, TRUST_ANCHOR> =
        cached(args.cacheDispatcher, args.clock, args.ttl, expectedQueries)

    private fun SupportedLists<String>.cfgs(): SupportedLists<LoTECfg<CTX, CERT>> =
        SupportedLists.combine(this, svcTypePerCtx) { url, ctx ->
            LoTECfg(url, ctx)
        }

    private data class LoTECfg<CTX : Any, CERT : Any>(
        val downloadUrl: String,
        val metadata: LotEMata<CTX, CERT>,
    )

    private data class CacheArguments(
        val cacheDispatcher: CoroutineDispatcher,
        val clock: Clock,
        val ttl: Duration,
    )

    public companion object {
        public fun <CHAIN : Any, TRUST_ANCHOR : Any, CERT : Any> eudiw(
            loadLoTEAndPointers: LoadLoTEAndPointers,
            svcTypePerCtx: SupportedLists<LotEMata<VerificationContext, CERT>>,
            extractCertificate: (TRUST_ANCHOR) -> CERT,
            continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
            directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
            pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
            getCertInfo: suspend (CERT) -> String,
            createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
        ): ProvisionTrustAnchorsFromLoTEs<CHAIN, VerificationContext, TRUST_ANCHOR, CERT> =
            ProvisionTrustAnchorsFromLoTEs(
                loadLoTEAndPointers,
                createTrustAnchors,
                extractCertificate,
                getCertInfo,
                directTrust,
                pkix,
                continueOnProblem,
                svcTypePerCtx,
            )
    }
}
