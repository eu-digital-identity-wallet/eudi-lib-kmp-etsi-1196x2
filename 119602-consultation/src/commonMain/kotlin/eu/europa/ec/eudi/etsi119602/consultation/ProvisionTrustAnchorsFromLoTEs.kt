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
import eu.europa.ec.eudi.etsi119602.consultation.eu.CertificateConstraints
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateOperations
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Metadata associated with a LoTE configuration.
 *
 */
public data class LotEMeta<CTX>(
    val svcTypePerCtx: Map<CTX, URI>,
    val serviceDigitalIdentityCertificateType: ServiceDigitalIdentityCertificateType,
    val endEntityCertificateConstraints: CertificateConstraints?,
)

/**
 * Provides functionality for provisioning trust anchors derived from Lots of Trust Entities (LoTEs) and validating
 * certificate chains. The class supports both cached and non-cached configurations for trust anchor extraction and
 * chain validation.
 *
 * @param CHAIN The type representing a certificate chain.
 * @param CTX The type representing a context within which a trust decision is made.
 * @param TRUST_ANCHOR The type representing a trust anchor.
 *
 * @property loadLoTEAndPointers A loader for fetching LoTEs and their pointers.
 * @property createTrustAnchors A function that creates a list of trust anchors from a service digital identity.
 * Defaults to the string representation of the certificate.
 * @property directTrust A certificate chain validator based on direct trust.
 * @property pkix A certificate chain validator based on PKIX.
 * @property continueOnProblem Strategy indicating whether to continue on specific issues.
 * @property svcTypePerCtx Supported metadata linked to LoTEs, defining service types per specific contexts.
 */
public class ProvisionTrustAnchorsFromLoTEs<CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any, in CERT : Any>(
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val svcTypePerCtx: SupportedLists<LotEMeta<CTX>>,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
    private val directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
    private val pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val certificateOperations: CertificateOperations<CERT>,
    private val endEntityCertificateOf: (CHAIN) -> CERT,
) {

    /**
     * Creates a [ComposeChainTrust] for a given LoTE configuration and trust anchor extraction function, suitable to be
     * used in a low-concurrency environment where caching is not required.
     *
     * @param loteLocationsSupported the list of LoTE locations to use
     *
     * @return a [ComposeChainTrust] instance
     */
    public fun nonCached(loteLocationsSupported: SupportedLists<String>): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> =
        loteLocationsSupported.cfgs().map { cfg ->
            val getTrustAnchors = createGetTrustAnchorsFromLoTE(cfg)
            createValidator(cfg, getTrustAnchors)
        }.compose()

    /**
     * Creates a [ComposeChainTrust] for a given LoTE configuration and trust anchor extraction function, suitable to be
     * used in a high-concurrency environment where caching is required.
     *
     * Each of the individual [GetTrustAnchorsFromLoTE] will be cached, offering thread-safe caching.
     *
     * @param disposableScope the [DisposableScope] to use for binding the cache sources
     * @param loteLocationsSupported the list of LoTE locations to use
     * @param ttl the time-to-live for cached entries
     * @param cacheDispatcher the dispatcher to use for the cache background operations. Defaults to [Dispatchers.Default]
     * @param clock the clock to use by the cache. Defaults to [Clock.System]
     *
     * @see GetTrustAnchorsCachedSource
     * @see DisposableScope
     */
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
        cfg: LoTECfg<CTX>,
        getTrustAnchors: GetTrustAnchors<URI, TRUST_ANCHOR>,
    ): IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR> {
        val certificateChainValidator = certificateChainValidator(cfg)
        val transformation = cfg.metadata.svcTypePerCtx
        return getTrustAnchors.validator(transformation, certificateChainValidator)
    }

    private fun createGetTrustAnchorsFromLoTE(
        cfg: LoTECfg<CTX>,
        args: CacheArguments,
    ): GetTrustAnchorsCachedSource<URI, TRUST_ANCHOR> {
        val getTrustAnchors = createGetTrustAnchorsFromLoTE(cfg)
        return getTrustAnchors.cached(args, cfg.metadata.svcTypePerCtx.size)
    }

    private fun createGetTrustAnchorsFromLoTE(
        cfg: LoTECfg<CTX>,
    ): GetTrustAnchors<URI, TRUST_ANCHOR> =
        GetTrustAnchorsFromLoTE(
            loTEDownloadUrl = cfg.downloadUrl,
            loadLoTEAndPointers = loadLoTEAndPointers,
            continueOnProblem = continueOnProblem,
            createTrustAnchors = createTrustAnchors,
        )

    private fun certificateChainValidator(
        cfg: LoTECfg<CTX>,
    ): ValidateCertificateChain<CHAIN, TRUST_ANCHOR> {
        val endEntityCertificateConstraints = cfg.metadata.endEntityCertificateConstraints?.run { with(certificateOperations) { evaluator() } }
        val validator = when (cfg.metadata.serviceDigitalIdentityCertificateType) {
            ServiceDigitalIdentityCertificateType.EndEntity -> directTrust
            ServiceDigitalIdentityCertificateType.CA -> pkix
            ServiceDigitalIdentityCertificateType.EndEntityOrCA -> directTrust or pkix
        }
        return if (endEntityCertificateConstraints != null) {
            validator.withEndEntityConstraints(endEntityCertificateConstraints, endEntityCertificateOf)
        } else {
            validator
        }
    }

    private fun GetTrustAnchors<URI, TRUST_ANCHOR>.cached(
        args: CacheArguments,
        expectedQueries: Int,
    ): GetTrustAnchorsCachedSource<URI, TRUST_ANCHOR> =
        cached(args.cacheDispatcher, args.clock, args.ttl, expectedQueries)

    private fun SupportedLists<String>.cfgs(): SupportedLists<LoTECfg<CTX>> =
        SupportedLists.combine(this, svcTypePerCtx) { url, ctx ->
            LoTECfg(url, ctx)
        }

    private data class LoTECfg<CTX : Any>(
        val downloadUrl: String,
        val metadata: LotEMeta<CTX>,
    )

    private data class CacheArguments(
        val cacheDispatcher: CoroutineDispatcher,
        val clock: Clock,
        val ttl: Duration,
    )

    public companion object {
        public fun <CHAIN : Any, TRUST_ANCHOR : Any, CERT : Any> eudiw(
            loadLoTEAndPointers: LoadLoTEAndPointers,
            svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>> = SupportedLists.eu(),
            createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
            directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
            pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
            continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
            certificateOperations: CertificateOperations<CERT>,
            endEntityCertificateOf: (CHAIN) -> CERT,
        ): ProvisionTrustAnchorsFromLoTEs<CHAIN, VerificationContext, TRUST_ANCHOR, CERT> =
            ProvisionTrustAnchorsFromLoTEs(
                loadLoTEAndPointers,
                svcTypePerCtx,
                createTrustAnchors,
                directTrust,
                pkix,
                continueOnProblem,
                certificateOperations,
                endEntityCertificateOf,
            )
    }
}
