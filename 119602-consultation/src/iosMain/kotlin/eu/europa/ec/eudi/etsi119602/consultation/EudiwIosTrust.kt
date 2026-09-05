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

import eu.europa.ec.eudi.etsi119602.consultation.EudiwIosTrust.mdlUseCase
import eu.europa.ec.eudi.etsi119602.consultation.EudiwIosTrust.trustAnchors
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import platform.Foundation.NSData
import kotlin.time.Duration.Companion.hours

/**
 * Swift-friendly outcome of a chain validation. Flattens the generic
 * [CertificationChainValidation] sealed type so Swift consumers don't need generic casts.
 *
 * @param isTrusted whether the chain is trusted for the requested context
 * @param matchedAnchor on success, the DER of the trust anchor that validated the chain
 * @param failureReason on failure, a human-readable reason
 */
public data class IosValidationResult(
    val isTrusted: Boolean,
    val matchedAnchor: NSData?,
    val failureReason: String?,
)

/**
 * The chain-validation strategy for [EudiwIosTrust.usingBundledAnchors].
 */
public enum class BundledAnchorMethod {
    /**
     * Full PKIX path validation via Apple `SecTrust`: the presented chain must build to one of the
     * bundled anchors. Use when the bundled anchors are **CA** certificates (roots/intermediates).
     */
    PKIX,

    /**
     * Direct trust (certificate pinning): the chain's leaf must byte-match one of the bundled
     * anchors. Use when the bundled anchors are the **exact end-entity** certificates to pin.
     */
    DIRECT_TRUST,
}

/**
 * LoTE download URL configuration for [EudiwIosTrust.nonCached] and [EudiwIosTrust.cached].
 *
 * Create an instance, set only the contexts you need, and leave the rest `null`.
 *
 * ```swift
 * let urls = TrustListUrls()
 * urls.pidProviders    = DIGITTrustLists.pidProviders
 * urls.walletProviders = DIGITTrustLists.walletProviders
 * urls.mdlProviders    = DIGITTrustLists.mdlProviders
 * ```
 */
public class TrustListUrls {
    public var pidProviders: String? = null
    public var walletProviders: String? = null
    public var wrpacProviders: String? = null
    public var wrprcProviders: String? = null
    public var pubEaaProviders: String? = null
    public var qeaProviders: String? = null
    public var mdlProviders: String? = null
}

/**
 * Bundled (hardcoded) DER certificate anchor configuration for [EudiwIosTrust.usingBundledAnchors].
 *
 * Create an instance, set only the contexts you need, and leave the rest `null`.
 *
 * ```swift
 * let anchors = BundledAnchors()
 * anchors.pid = [myRootCA]
 * ```
 */
public class BundledAnchors {
    public var pid: List<NSData>? = null
    public var wallet: List<NSData>? = null
    public var wrpac: List<NSData>? = null
    public var wrprc: List<NSData>? = null
    public var pubEaa: List<NSData>? = null
    public var qea: List<NSData>? = null
    public var mdl: List<NSData>? = null
}

/**
 * Swift-friendly [LoadLoTE], taking the LoTE location as a plain [String]; [EudiwIosTrust] adapts it
 * internally.
 *
 * [LoadLoTE] itself is not implementable from Swift - its `uri` is a [Uri] value class, which
 * Kotlin/Native erases to an untyped `id`. The method is `loadLoTE` rather than `operator invoke`
 * because an `invoke` collides with [LoadLoTE.invoke] on the exported selector, and the
 * `completionHandler_:` that Kotlin/Native mangles it to suppresses Swift's `async` import.
 */
public fun interface IosLoadLoTE {
    public suspend fun loadLoTE(uri: String): LoadLoTE.Outcome<String>
}

/**
 * One-call assembly of the iOS LoTE-based trust validator, intended for Swift consumers.
 *
 * It wires together: a Darwin [io.ktor.client.HttpClient] → [DownloadSingleLoTE] →
 * [LoadLoTEAndPointers] → [ProvisionTrustAnchorsFromLoTEs.eudiwIos] → [ComposeChainTrust.nonCached],
 * and exposes Swift-friendly entry points that avoid Kotlin value classes ([Uri], [NonEmptyList])
 * at the boundary — LoTE locations are passed via [TrustListUrls] and trust anchors come back as
 * a plain list of DER [NSData].
 *
 * [nonCached] and [cached] each have an overload taking an [IosLoadLoTE], for callers that must
 * fetch LoTEs through their own HTTP stack. Overloads rather than a defaulted parameter:
 * Kotlin defaults are not carried into the generated Objective-C header, so a default would have
 * dropped the existing selectors from the Swift surface.
 */
public object EudiwIosTrust {

    /**
     * The use-case key under which the mDL list is registered (in both the locations and the
     * service-type metadata). Swift consumers select the mDL context with
     * `VerificationContextEAA(useCase: EudiwIosTrust.shared.mdlUseCase)`.
     */
    public val mdlUseCase: String get() = "mdl"

    /**
     * Builds a non-cached validator for the LoTE download URLs in [urls], fetching each LoTE with
     * the built-in Darwin (NSURLSession) downloader. Leave any context `null` in [urls] to skip it.
     * Suitable for low-concurrency use (e.g. one validation per screen).
     *
     * @param urls the LoTE download URLs for each context; leave any context `null` to skip it
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     */
    public fun nonCached(
        urls: TrustListUrls,
        verifyJwtSignature: VerifyJwtSignature,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> =
        buildNonCached(urls, verifyJwtSignature, defaultLoadLoTE())

    /**
     * As [nonCached] `(urls:verifyJwtSignature:)`, but each LoTE is obtained from [loadLoTE] instead
     * of the built-in downloader.
     *
     * @param urls the LoTE download URLs for each context; leave any context `null` to skip it
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     * @param loadLoTE resolves a LoTE URL to its raw JWT; a missing list must be reported as
     *        [LoadLoTE.Outcome.NotFound] rather than thrown.
     */
    public fun nonCached(
        urls: TrustListUrls,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: IosLoadLoTE,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> =
        buildNonCached(urls, verifyJwtSignature, loadLoTE.asLoadLoTE())

    /**
     * Builds a **cached** validator, fetching each LoTE with the built-in Darwin (NSURLSession)
     * downloader: trust anchors are resolved from the LoTEs once per context and
     * kept in memory for [ttlHours] hours, so repeated [CachedTrustValidator.trustAnchors] /
     * [CachedTrustValidator.validate] calls within that window are served without re-downloading.
     * Leave any context `null` in [urls] to skip it.
     *
     * Suited to higher-concurrency use (a wallet validating many credentials). The returned
     * [CachedTrustValidator] **owns** the in-memory cache: hold it for the session and call
     * [CachedTrustValidator.dispose] when finished (e.g. from a Swift `deinit`) to release it. Not
     * disposing leaks the cache for the process lifetime — fine for an app-lifetime singleton, not
     * for per-screen handles.
     *
     * @param urls the LoTE download URLs for each context; leave any context `null` to skip it
     * @param ttlHours cache time-to-live in hours (e.g. `24.0`); a plain `Double` to avoid Kotlin's
     *        `Duration` value class at the Swift boundary.
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     */
    public fun cached(
        urls: TrustListUrls,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
    ): CachedTrustValidator =
        buildCached(urls, ttlHours, verifyJwtSignature, defaultLoadLoTE())

    /**
     * As [cached]`(urls:ttlHours:verifyJwtSignature:)` — same ownership contract — but each LoTE is
     * obtained from [loadLoTE] instead of the built-in downloader, and only on a cache miss.
     *
     * @param urls the LoTE download URLs for each context; leave any context `null` to skip it
     * @param ttlHours cache time-to-live in hours (e.g. `24.0`); a plain `Double` to avoid Kotlin's
     *        `Duration` value class at the Swift boundary.
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     * @param loadLoTE resolves a LoTE URL to its raw JWT; a missing list must be reported as
     *        [LoadLoTE.Outcome.NotFound] rather than thrown.
     */
    public fun cached(
        urls: TrustListUrls,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: IosLoadLoTE,
    ): CachedTrustValidator =
        buildCached(urls, ttlHours, verifyJwtSignature, loadLoTE.asLoadLoTE())

    /**
     * Builds a validator backed by **bundled / hardcoded** certificate anchors instead of a
     * downloaded LoTE — no network, JWT, or LoTE is involved. This is the iOS counterpart of the
     * JVM `IsChainTrustedForContext.usingKeyStore(...)`.
     *
     * Set only the contexts you need on [anchors]; leave the rest `null`. The mDL context is
     * registered under [mdlUseCase] (i.e. `VerificationContext.EAA("mdl")`).
     *
     * No ETSI end-entity profile is applied — these contexts validate purely by [method] — so bundled
     * CA anchors are not rejected by the strict EUDI end-entity profiles.
     *
     * @param method [BundledAnchorMethod.PKIX] for chain-to-anchor path validation (anchors are CA
     *        certificates) or [BundledAnchorMethod.DIRECT_TRUST] for leaf pinning (anchors are the
     *        exact end-entity certificates).
     */
    public fun usingBundledAnchors(
        anchors: BundledAnchors,
        method: BundledAnchorMethod,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> {
        val anchorsByContext: Map<VerificationContext, List<NSData>> = buildMap {
            anchors.pid?.let { put(VerificationContext.PID, it) }
            anchors.wallet?.let { put(VerificationContext.WalletProviderAttestation, it) }
            anchors.wrpac?.let { put(VerificationContext.WalletRelyingPartyAccessCertificate, it) }
            anchors.wrprc?.let { put(VerificationContext.WalletRelyingPartyRegistrationCertificate, it) }
            anchors.pubEaa?.let { put(VerificationContext.PubEAA, it) }
            anchors.qea?.let { put(VerificationContext.QEAA, it) }
            anchors.mdl?.let { put(VerificationContext.EAA(mdlUseCase), it) }
        }

        val getTrustAnchors = GetTrustAnchors<VerificationContext, NSData> { ctx ->
            anchorsByContext[ctx]?.let { NonEmptyList.nelOrNull(it) }
        }

        val validateChain: ValidateCertificateChain<List<NSData>, NSData> = when (method) {
            BundledAnchorMethod.PKIX -> ValidateCertificateChainUsingPKIXIos()
            BundledAnchorMethod.DIRECT_TRUST -> ValidateCertificateChainUsingDirectTrustIos
        }

        return ComposeChainTrust(getTrustAnchors.validator(anchorsByContext.keys, validateChain))
    }

    private fun buildLocations(urls: TrustListUrls): SupportedLists<Uri> =
        SupportedLists(
            pidProviders = urls.pidProviders?.let(::Uri),
            walletProviders = urls.walletProviders?.let(::Uri),
            wrpacProviders = urls.wrpacProviders?.let(::Uri),
            wrprcProviders = urls.wrprcProviders?.let(::Uri),
            pubEaaProviders = urls.pubEaaProviders?.let(::Uri),
            qeaProviders = urls.qeaProviders?.let(::Uri),
            eaaProviders = buildMap { urls.mdlProviders?.let { put(mdlUseCase, Uri(it)) } },
        )

    // The baseline EU metadata has no mDL entry, so add one when an mDL URL is supplied.
    // mDL uses a null end-entity profile (the advertised DIGIT lists do not satisfy the strict ETSI
    // profiles), so its validation is pure direct trust / PKIX. Mirrors DIGIT.SVC_TYPE_PER_CTX.
    private fun buildSvcTypePerCtx(mdlProvidersUrl: String?): SupportedLists<LotEMeta<VerificationContext>> =
        SupportedLists.eu().let { baseline ->
            if (mdlProvidersUrl != null) baseline.copy(eaaProviders = mapOf(mdlUseCase to mdlMeta())) else baseline
        }

    private fun buildNonCached(
        urls: TrustListUrls,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> =
        ProvisionTrustAnchorsFromLoTEs
            .eudiwIos(
                loadLoTEAndPointers = buildLoadLoTEAndPointers(verifyJwtSignature, loadLoTE),
                svcTypePerCtx = buildSvcTypePerCtx(urls.mdlProviders),
            )
            .nonCached(buildLocations(urls))

    private fun buildCached(
        urls: TrustListUrls,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
    ): CachedTrustValidator {
        val scope = DisposableContainer()
        val validator = ProvisionTrustAnchorsFromLoTEs
            .eudiwIos(
                loadLoTEAndPointers = buildLoadLoTEAndPointers(verifyJwtSignature, loadLoTE),
                svcTypePerCtx = buildSvcTypePerCtx(urls.mdlProviders),
            )
            .cached(
                disposableScope = scope,
                loteLocationsSupported = buildLocations(urls),
                ttl = ttlHours.hours,
            )
        return CachedTrustValidator(scope, validator)
    }

    private fun defaultLoadLoTE(): LoadLoTE<String> = DownloadSingleLoTE(IosLoTEHttpClient.create())

    private fun IosLoadLoTE.asLoadLoTE(): LoadLoTE<String> = LoadLoTE { uri -> loadLoTE(uri.value) }

    private fun buildLoadLoTEAndPointers(
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
    ): LoadLoTEAndPointers =
        LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
            verifyJwtSignature = verifyJwtSignature,
            loadLoTE = loadLoTE,
        )

    private fun mdlMeta(): LotEMeta<VerificationContext> = LotEMeta(
        svcTypePerCtx = buildMap {
            put(
                VerificationContext.EAA(mdlUseCase),
                LotEMeta.SvcAndEEProfile(Uri(EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE), null),
            )
            put(
                VerificationContext.EAAStatus(mdlUseCase),
                LotEMeta.SvcAndEEProfile(Uri(EUMDLProvidersListSpec.SVC_TYPE_REVOCATION), null),
            )
        },
        serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.EndEntityOrCA,
    )

    /**
     * Resolves the trust anchors (DER bytes) for [context] using [validator], as a plain list.
     * Returns an empty list if the validator has no anchors for that context. Suspends while the
     * underlying LoTE is fetched/parsed.
     *
     * `@Throws` is required: without it, any failure (network, JSON parse, cancellation) would be
     * an unhandled Kotlin/Native exception that crashes the process instead of surfacing to Swift
     * as a catchable error.
     */
    @Throws(Throwable::class)
    public suspend fun trustAnchors(
        validator: ComposeChainTrust<List<NSData>, VerificationContext, NSData>,
        context: VerificationContext,
    ): List<NSData> =
        validator.getTrustAnchors.invoke(context)?.list ?: emptyList()

    /**
     * Validates a DER-encoded certificate [chain] for [context] using [validator].
     *
     * The chain is the leaf-first list of DER certificates from a received credential/attestation
     * (e.g. the `x5c` of a PID or mDL). Returns an [IosValidationResult]; a `null` underlying
     * result (context not supported by the validator) is reported as not-trusted.
     *
     * `@Throws` for the same reason as [trustAnchors]: validation fetches/parses the LoTE and could
     * otherwise crash the process on failure instead of surfacing a Swift error.
     */
    @Throws(Throwable::class)
    public suspend fun validate(
        validator: ComposeChainTrust<List<NSData>, VerificationContext, NSData>,
        chain: List<NSData>,
        context: VerificationContext,
    ): IosValidationResult =
        when (val outcome = validator.invoke(chain, context)) {
            null -> IosValidationResult(
                isTrusted = false,
                matchedAnchor = null,
                failureReason = "No validator configured for this context",
            )

            is CertificationChainValidation.Trusted -> IosValidationResult(
                isTrusted = true,
                matchedAnchor = outcome.trustAnchor,
                failureReason = null,
            )

            is CertificationChainValidation.NotTrusted -> IosValidationResult(
                isTrusted = false,
                matchedAnchor = null,
                failureReason = outcome.cause.message ?: "Chain is not trusted",
            )
        }
}

/**
 * A cached iOS trust validator together with the lifecycle that owns its in-memory anchor cache.
 *
 * Created by [EudiwIosTrust.cached]. Hold it for the session (e.g. a property on a Swift view model
 * or trust manager), then call [dispose] when finished to release the cache. The [trustAnchors] /
 * [validate] entry points mirror the non-cached ones on [EudiwIosTrust], but anchor lookups are
 * served from the in-memory cache within the configured TTL.
 */
public class CachedTrustValidator internal constructor(
    private val scope: DisposableContainer,
    private val validator: ComposeChainTrust<List<NSData>, VerificationContext, NSData>,
) {
    /** Resolves the trust anchors (DER bytes) for [context]; served from cache within the TTL. */
    @Throws(Throwable::class)
    public suspend fun trustAnchors(context: VerificationContext): List<NSData> =
        EudiwIosTrust.trustAnchors(validator, context)

    /**
     * Validates a leaf-first DER [chain] for [context]; the anchor lookup is served from cache
     * within the TTL. Returns an [IosValidationResult] (not-trusted if the context is unsupported).
     */
    @Throws(Throwable::class)
    public suspend fun validate(chain: List<NSData>, context: VerificationContext): IosValidationResult =
        EudiwIosTrust.validate(validator, chain, context)

    /** Releases the in-memory caches. Call once when the validator is no longer needed. */
    public fun dispose(): Unit = scope.dispose()
}
