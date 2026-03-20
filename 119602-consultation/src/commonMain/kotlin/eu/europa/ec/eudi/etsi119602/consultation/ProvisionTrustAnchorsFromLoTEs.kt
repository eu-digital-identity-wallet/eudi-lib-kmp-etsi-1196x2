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
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Metadata associated with a specific LoTE configuration, used by [ProvisionTrustAnchorsFromLoTEs]
 *
 * Requirements
 * - The LoTE must include a [eu.europa.ec.eudi.etsi119602.ServiceInformation.typeIdentifier] (in general it is optional), for every information
 * - All service type identifies in [svcTypePerCtx] are available to the same LoTE profile
 * - For each trusted service there is at least one certificate within [ServiceDigitalIdentity.x509Certificates]
 *
 * @param svcTypePerCtx A map which correlates a verification context [CTX] to a [eu.europa.ec.eudi.etsi119602.ServiceInformation.typeIdentifier].
 * @param serviceDigitalIdentityCertificateType A hint, indicating whether the [ServiceDigitalIdentity.x509Certificates]
 * are expecting/required to be End-Entity, CA or Both. This will drive the selection of chain validation method (direct trust, PKIX, or both)
 *
 * @param CTX the type representing a verification context
 * @see ProvisionTrustAnchorsFromLoTEs
 */
public data class LotEMeta<CTX>(
    val svcTypePerCtx: Map<CTX, SvcTypeIdentifierAndEndEntityCertificateProfile>,
    val serviceDigitalIdentityCertificateType: ServiceDigitalIdentityCertificateType,
) {
    public data class SvcTypeIdentifierAndEndEntityCertificateProfile(
        val svcTypeIdentifier: URI,
        val profile: CertificateProfile?,
    )
}

/**
 * Provides functionality for provisioning trust anchors derived from Lots of Trust Entities (LoTEs) and validating
 * certificate chains. The class supports both cached and non-cached configurations for trust anchor extraction and
 * chain validation.
 *
 * @param CHAIN The type representing a certificate chain.
 * @param CTX The type representing a context within which a trust decision is made.
 * @param TRUST_ANCHOR The type representing a trust anchor.
 * @param CERT The type representing a X509 Certificate, found in [CHAIN]
 *
 * @property loadLoTEAndPointers A loader for fetching LoTEs and their pointers.
 * @property svcTypePerCtx Supported metadata linked to LoTEs, defining service types per specific contexts.
 * @property createTrustAnchors A function that creates a list of trust anchors from a service digital identity.
 * @property directTrust A certificate chain validator based on direct trust.
 * @property pkix A certificate chain validator based on PKIX.
 * @property continueOnProblem Strategy indicating whether to continue on specific problems while loading a LoTE.
 * Defaults to [ContinueOnProblem.Never]
 * @property certificateProfileValidator an abstraction for certificate operations. It will be used to thread validation
 * of end-entity certificate of a chain to the chain validation.
 * @param endEntityCertificateOf A way to obtain the end entity certificate from a chain.
 */
public class ProvisionTrustAnchorsFromLoTEs<CHAIN : Any, CTX : Any, TRUST_ANCHOR : Any, in CERT : Any>(
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val svcTypePerCtx: SupportedLists<LotEMeta<CTX>>,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
    private val directTrust: ValidateCertificateChainUsingDirectTrust<CHAIN, TRUST_ANCHOR, *>,
    private val pkix: ValidateCertificateChainUsingPKIX<CHAIN, TRUST_ANCHOR>,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val certificateProfileValidator: CertificateProfileValidator<CERT>,
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
    public fun nonCached(loteLocationsSupported: SupportedLists<String>): ComposeChainTrust<CHAIN, CTX, TRUST_ANCHOR> {
        return loteLocationsSupported.cfgs().flatMap { cfg ->
            val getTrustAnchors = createGetTrustAnchorsFromLoTE(cfg)
            createValidators(cfg.metadata, getTrustAnchors)
        }.compose()
    }

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
            loteLocationsSupported.cfgs().associate { cfg -> cfg.metadata to createGetTrustAnchorsFromLoTE(cfg, args) }
        with(disposableScope) {
            sources.values.forEach { it.bind() }
        }

        val composeChainTrust =
            sources.flatMap { (metadata, getTrustAnchors) -> createValidators(metadata, getTrustAnchors) }
                .compose()
        return composeChainTrust
    }

    private fun createValidators(
        metadata: LotEMeta<CTX>,
        getTrustAnchors: GetTrustAnchors<URI, TRUST_ANCHOR>,
    ): List<IsChainTrustedForContext<CHAIN, CTX, TRUST_ANCHOR>> {
        val baseChainValidator = baseCertificateChainValidators(metadata)
        val transformation = metadata.svcTypePerCtx.mapValues { it.value.svcTypeIdentifier }
        val countEndEntityCertProfiles = metadata.svcTypePerCtx.count { (_, v) -> v.profile != null }
        return when {
            countEndEntityCertProfiles == 0 -> {
                val isChainTrustedForCtx = getTrustAnchors.validator(transformation, baseChainValidator)
                listOf(isChainTrustedForCtx)
            }
            else -> {
                metadata.svcTypePerCtx.map { (ctx, svcTypeAndCertProf) ->
                    val (_, certProf) = svcTypeAndCertProf
                    val singleTransformation = transformation.filterKeys { it == ctx }
                    val cv = certProf?.let { baseChainValidator.withEndEntityProfile(certificateProfileValidator, it, endEntityCertificateOf) } ?: baseChainValidator
                    val isChainTrustedForCtx = getTrustAnchors.validator(singleTransformation, cv)
                    isChainTrustedForCtx
                }
            }
        }
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

    private fun baseCertificateChainValidators(
        metadata: LotEMeta<CTX>,
    ): ValidateCertificateChain<CHAIN, TRUST_ANCHOR> =
        when (metadata.serviceDigitalIdentityCertificateType) {
            ServiceDigitalIdentityCertificateType.EndEntity -> directTrust
            ServiceDigitalIdentityCertificateType.CA -> pkix
            ServiceDigitalIdentityCertificateType.EndEntityOrCA -> directTrust or pkix
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
            certificateProfileValidator: CertificateProfileValidator<CERT>,
            endEntityCertificateOf: (CHAIN) -> CERT,
        ): ProvisionTrustAnchorsFromLoTEs<CHAIN, VerificationContext, TRUST_ANCHOR, CERT> =
            ProvisionTrustAnchorsFromLoTEs(
                loadLoTEAndPointers,
                svcTypePerCtx,
                createTrustAnchors,
                directTrust,
                pkix,
                continueOnProblem,
                certificateProfileValidator,
                endEntityCertificateOf,
            )
    }
}
