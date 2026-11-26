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

import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
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
 * The Lists of Trusted Entities (LoTEs) used by [EudiwIosTrust.nonCached] and [EudiwIosTrust.cached].
 *
 * Create an instance, set only the lists you need, and leave the rest `null`. Each [TrustList]
 * carries both the download URL and the metadata needed to interpret it. Use [EUTrustLists] for the
 * EU lists; build a [TrustList] directly only for lists it does not cover (e.g. QEAA).
 *
 * ```swift
 * let lists = TrustLists()
 * lists.pidProviders    = EUTrustLists.shared.pid(url: DIGITTrustLists.pidProviders)
 * lists.walletProviders = EUTrustLists.shared.walletProviders(url: DIGITTrustLists.walletProviders)
 * lists.pubEaaProviders = EUTrustLists.shared.pubEaa(url: pubEaaUrl)
 * lists.eaaProviders    = [EUTrustLists.shared.mdl(url: DIGITTrustLists.mdlProviders, useCase: "mdl")]
 * ```
 *
 * For a list [EUTrustLists] does not cover, build the [TrustList] yourself. The service type
 * identifiers must match the `ServiceTypeIdentifier`s the LoTE actually publishes; otherwise no
 * services match and the context gets no trust anchors.
 *
 * ```swift
 * // A QEAA list (no ETSI TS 119 602 profile exists, so the metadata is caller-defined)
 * lists.qEaaProviders = TrustList(
 *     url: "https://example.com/qeaa-providers.json",
 *     issuanceService: "https://example.com/SvcType/QEAA/Issuance",
 *     revocationService: "https://example.com/SvcType/QEAA/Revocation",
 *     certificateProfile: nil,                      // no end-entity profile: pure direct trust / PKIX
 *     serviceDigitalIdentityCertificateType: .ca    // listed certificates are CAs → PKIX
 * )
 *
 * // A custom EAA list, validated with VerificationContextEAA(useCase: "diploma")
 * lists.eaaProviders = [
 *     EAATrustList(
 *         useCase: "diploma",
 *         trustList: TrustList(
 *             url: "https://example.com/diploma-providers.json",
 *             issuanceService: "https://example.com/SvcType/Diploma/Issuance",
 *             revocationService: nil,               // no status context
 *             certificateProfile: nil,
 *             serviceDigitalIdentityCertificateType: .endentityorca
 *         )
 *     ),
 * ]
 * ```
 */
public class TrustLists {
    public var pidProviders: TrustList? = null
    public var walletProviders: TrustList? = null
    public var wrpacProviders: TrustList? = null
    public var wrprcProviders: TrustList? = null
    public var pubEaaProviders: TrustList? = null
    public var qEaaProviders: TrustList? = null
    public var eaaProviders: List<EAATrustList>? = null
}

/**
 * A single LoTE: where to download it and how to map its services to verification contexts.
 *
 * Prefer the [EUTrustLists] builders for the EU lists.
 *
 * @param url the LoTE download URL
 * @param issuanceService the service type identifier whose services provide the issuance anchors
 * @param revocationService the service type identifier whose services provide the status/revocation
 *        anchors, or `null` if the list has no status context
 * @param certificateProfile the end-entity certificate profile enforced for the issuance context;
 *        `null` applies no profile (pure direct trust / PKIX). See [EUCertificateProfiles] for the EU profiles
 * @param serviceDigitalIdentityCertificateType whether the listed certificates are end-entity
 *        (direct trust), CA (PKIX), or either
 */
public data class TrustList(
    public var url: String,
    public var issuanceService: String,
    public var revocationService: String?,
    public var certificateProfile: CertificateProfile?,
    public var serviceDigitalIdentityCertificateType: ServiceDigitalIdentityCertificateType,
)

/**
 * An EAA LoTE registered under [useCase]; validate with `VerificationContext.EAA(useCase)` and
 * `VerificationContext.EAAStatus(useCase)`.
 */
public data class EAATrustList(
    public var useCase: String,
    public var trustList: TrustList,
)

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
    public var qEaa: List<NSData>? = null
    public var eaa: Map<String, List<NSData>>? = null
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
 * at the boundary — LoTE locations are passed via [TrustLists] and trust anchors come back as
 * a plain list of DER [NSData].
 *
 * [nonCached] and [cached] each have an overload taking an [IosLoadLoTE], for callers that must
 * fetch LoTEs through their own HTTP stack. Overloads rather than a defaulted parameter:
 * Kotlin defaults are not carried into the generated Objective-C header, so a default would have
 * dropped the existing selectors from the Swift surface.
 */
public object EudiwIosTrust {

    /**
     * Builds a non-cached validator for the LoTEs in [trustLists], fetching each LoTE with
     * the built-in Darwin (NSURLSession) downloader. Leave any context `null` in [trustLists] to skip it.
     * Suitable for low-concurrency use (e.g. one validation per screen).
     *
     * @param trustLists the LoTEs to use; leave any list `null` to skip it
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     */
    public fun nonCached(
        trustLists: TrustLists,
        verifyJwtSignature: VerifyJwtSignature,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = buildNonCached(trustLists, verifyJwtSignature, defaultLoadLoTE(), directTrust, pkix)

    /**
     * As [nonCached] `(trustLists:verifyJwtSignature:)`, but each LoTE is obtained from [loadLoTE] instead
     * of the built-in downloader.
     *
     * @param trustLists the LoTEs to use; leave any list `null` to skip it
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     * @param loadLoTE resolves a LoTE URL to its raw JWT; a missing list must be reported as
     *        [LoadLoTE.Outcome.NotFound] rather than thrown.
     */
    public fun nonCached(
        trustLists: TrustLists,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: IosLoadLoTE,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = buildNonCached(trustLists, verifyJwtSignature, loadLoTE.asLoadLoTE(), directTrust, pkix)

    /**
     * Builds a **cached** validator, fetching each LoTE with the built-in Darwin (NSURLSession)
     * downloader: trust anchors are resolved from the LoTEs once per context and
     * kept in memory for [ttlHours] hours, so repeated [CachedTrustValidator.trustAnchors] /
     * [CachedTrustValidator.validate] calls within that window are served without re-downloading.
     * Leave any context `null` in [trustLists] to skip it.
     *
     * Suited to higher-concurrency use (a wallet validating many credentials). The returned
     * [CachedTrustValidator] **owns** the in-memory cache: hold it for the session and call
     * [CachedTrustValidator.dispose] when finished (e.g. from a Swift `deinit`) to release it. Not
     * disposing leaks the cache for the process lifetime — fine for an app-lifetime singleton, not
     * for per-screen handles.
     *
     * @param trustLists the LoTEs to use; leave any list `null` to skip it
     * @param ttlHours cache time-to-live in hours (e.g. `24.0`); a plain `Double` to avoid Kotlin's
     *        `Duration` value class at the Swift boundary.
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     */
    public fun cached(
        trustLists: TrustLists,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): CachedTrustValidator = buildCached(trustLists, ttlHours, verifyJwtSignature, defaultLoadLoTE(), directTrust, pkix)

    /**
     * As [cached]`(trustLists:ttlHours:verifyJwtSignature:)` — same ownership contract — but each LoTE is
     * obtained from [loadLoTE] instead of the built-in downloader, and only on a cache miss.
     *
     * @param trustLists the LoTEs to use; leave any list `null` to skip it
     * @param ttlHours cache time-to-live in hours (e.g. `24.0`); a plain `Double` to avoid Kotlin's
     *        `Duration` value class at the Swift boundary.
     * @param verifyJwtSignature verifies each downloaded LoTE JWT — supply a real implementation in
     *        production; this is a required, explicit choice so trust is never silently bypassed.
     * @param loadLoTE resolves a LoTE URL to its raw JWT; a missing list must be reported as
     *        [LoadLoTE.Outcome.NotFound] rather than thrown.
     */
    public fun cached(
        trustLists: TrustLists,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: IosLoadLoTE,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): CachedTrustValidator = buildCached(trustLists, ttlHours, verifyJwtSignature, loadLoTE.asLoadLoTE(), directTrust, pkix)

    /**
     * Swift-facing overload of [nonCached]`(trustLists:verifyJwtSignature:)` that accepts a
     * [VerifyJwtSignatureCallback]
     */
    public fun nonCached(
        trustLists: TrustLists,
        verifyJwtSignature: VerifyJwtSignatureCallback,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = nonCached(trustLists, verifyJwtSignature.asVerifyJwtSignature(), directTrust, pkix)

    /**
     * Swift-facing overload of [nonCached]`(trustLists:verifyJwtSignature:loadLoTE:)` that accepts a
     * [VerifyJwtSignatureCallback].
     */
    public fun nonCached(
        trustLists: TrustLists,
        verifyJwtSignature: VerifyJwtSignatureCallback,
        loadLoTE: IosLoadLoTE,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = nonCached(trustLists, verifyJwtSignature.asVerifyJwtSignature(), loadLoTE, directTrust, pkix)

    /**
     * Swift-facing overload of [cached]`(trustLists:ttlHours:verifyJwtSignature:)` that accepts a
     * [VerifyJwtSignatureCallback].
     */
    public fun cached(
        trustLists: TrustLists,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignatureCallback,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): CachedTrustValidator = cached(trustLists, ttlHours, verifyJwtSignature.asVerifyJwtSignature(), directTrust, pkix)

    /**
     * Swift-facing overload of [cached]`(trustLists:ttlHours:verifyJwtSignature:loadLoTE:)` that accepts
     * a [VerifyJwtSignatureCallback].
     */
    public fun cached(
        trustLists: TrustLists,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignatureCallback,
        loadLoTE: IosLoadLoTE,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos.invoke(isRevocationEnabled = true),
    ): CachedTrustValidator = cached(trustLists, ttlHours, verifyJwtSignature.asVerifyJwtSignature(), loadLoTE, directTrust, pkix)

    /**
     * Builds a validator backed by **bundled / hardcoded** certificate anchors instead of a
     * downloaded LoTE — no network, JWT, or LoTE is involved. This is the iOS counterpart of the
     * JVM `IsChainTrustedForContext.usingKeyStore(...)`.
     *
     * Set only the contexts you need on [anchors]; leave the rest `null`.
     *
     * No ETSI end-entity profile is applied — these contexts validate purely by [method] — so bundled
     * CA anchors are not rejected by the strict EUDI end-entity profiles.
     *
     * Chains are validated with the library defaults: [ValidateCertificateChainUsingPKIXIos] with
     * revocation checking enabled via OCSP for [BundledAnchorMethod.PKIX], and
     * [ValidateCertificateChainUsingDirectTrustIos] for [BundledAnchorMethod.DIRECT_TRUST]. To check
     * CRLs instead, or to disable revocation checking, use the overload that takes the validators.
     *
     * @param method [BundledAnchorMethod.PKIX] for chain-to-anchor path validation (anchors are CA
     *        certificates) or [BundledAnchorMethod.DIRECT_TRUST] for leaf pinning (anchors are the
     *        exact end-entity certificates).
     */
    public fun usingBundledAnchors(
        anchors: BundledAnchors,
        method: BundledAnchorMethod,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = usingBundledAnchors(
        anchors = anchors,
        method = method,
        directTrust = ValidateCertificateChainUsingDirectTrustIos,
        pkix = ValidateCertificateChainUsingPKIXIos(),
    )

    /**
     * As [usingBundledAnchors]`(anchors:method:)`, but with the chain validators supplied by the
     * caller, mirroring [cached] and [nonCached].
     *
     * This is how revocation checking is configured on the bundled-anchor path. The default PKIX
     * validator checks revocation via OCSP and requires a positive response, which rejects chains
     * whose certificates only publish CRL distribution points:
     *
     * ```kotlin
     * // check CRLs instead of OCSP
     * EudiwIosTrust.usingBundledAnchors(
     *     anchors = anchors,
     *     method = BundledAnchorMethod.PKIX,
     *     pkix = ValidateCertificateChainUsingPKIXIos(IosRevocationMethod.CRL),
     * )
     * // no revocation checking at all (e.g. offline tests)
     * EudiwIosTrust.usingBundledAnchors(
     *     anchors = anchors,
     *     method = BundledAnchorMethod.PKIX,
     *     pkix = ValidateCertificateChainUsingPKIXIos(isRevocationEnabled = false),
     * )
     * ```
     *
     * This is a separate overload rather than default arguments on
     * [usingBundledAnchors]`(anchors:method:)` because Kotlin default arguments are not exported to
     * Objective-C: adding them would change the selector existing Swift consumers compile against.
     *
     * @param directTrust the validator used when [method] is [BundledAnchorMethod.DIRECT_TRUST]
     * @param pkix the validator used when [method] is [BundledAnchorMethod.PKIX]
     */
    public fun usingBundledAnchors(
        anchors: BundledAnchors,
        method: BundledAnchorMethod,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData> =
            ValidateCertificateChainUsingDirectTrustIos,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
            ValidateCertificateChainUsingPKIXIos(),
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> {
        val anchorsByContext: Map<VerificationContext, List<NSData>> = buildMap {
            anchors.pid?.let { put(VerificationContext.PID, it) }
            anchors.wallet?.let { put(VerificationContext.WalletProviderAttestation, it) }
            anchors.wrpac?.let { put(VerificationContext.WalletRelyingPartyAccessCertificate, it) }
            anchors.wrprc?.let { put(VerificationContext.WalletRelyingPartyRegistrationCertificate, it) }
            anchors.pubEaa?.let { put(VerificationContext.PubEAA, it) }
            anchors.qEaa?.let { put(VerificationContext.QEAA, it) }
            anchors.eaa.orEmpty().forEach {
                put(VerificationContext.EAA(it.key), it.value)
            }
        }

        val getTrustAnchors = GetTrustAnchors<VerificationContext, NSData> { ctx ->
            anchorsByContext[ctx]?.let { NonEmptyList.nelOrNull(it) }
        }

        val validateChain: ValidateCertificateChain<List<NSData>, NSData> = when (method) {
            BundledAnchorMethod.PKIX -> pkix
            BundledAnchorMethod.DIRECT_TRUST -> directTrust
        }

        return ComposeChainTrust(getTrustAnchors.validator(anchorsByContext.keys, validateChain))
    }

    private fun buildLocations(trustLists: TrustLists): SupportedLists<Uri> = SupportedLists(
        pidProviders = trustLists.pidProviders?.url?.let(::Uri),
        walletProviders = trustLists.walletProviders?.url?.let(::Uri),
        wrpacProviders = trustLists.wrpacProviders?.url?.let(::Uri),
        wrprcProviders = trustLists.wrprcProviders?.url?.let(::Uri),
        pubEaaProviders = trustLists.pubEaaProviders?.url?.let(::Uri),
        qeaProviders = trustLists.qEaaProviders?.url?.let(::Uri),
        eaaProviders = trustLists.eaaProviders.orEmpty().associate { it.useCase to Uri(it.trustList.url) },
    )

    private fun buildSvcTypePerCtx(trustLists: TrustLists): SupportedLists<LotEMeta<VerificationContext>> = SupportedLists(
        pidProviders = trustLists.pidProviders?.toLotEMeta(
            issuanceContext = VerificationContext.PID,
            revocationContext = VerificationContext.PIDStatus,
        ),
        walletProviders = trustLists.walletProviders?.toLotEMeta(
            issuanceContext = VerificationContext.WalletProviderAttestation,
            revocationContext = VerificationContext.WalletOrKeyStorageStatus,
        ),
        wrpacProviders = trustLists.wrpacProviders?.toLotEMeta(
            issuanceContext = VerificationContext.WalletRelyingPartyAccessCertificate,
            revocationContext = null,
        ),
        wrprcProviders = trustLists.wrprcProviders?.toLotEMeta(
            issuanceContext = VerificationContext.WalletRelyingPartyRegistrationCertificate,
            revocationContext = VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
        ),
        pubEaaProviders = trustLists.pubEaaProviders?.toLotEMeta(
            issuanceContext = VerificationContext.PubEAA,
            revocationContext = VerificationContext.PubEAAStatus,
        ),
        qeaProviders = trustLists.qEaaProviders?.toLotEMeta(
            issuanceContext = VerificationContext.QEAA,
            revocationContext = VerificationContext.QEAAStatus,
        ),
        eaaProviders = trustLists.eaaProviders.orEmpty().associate {
            it.useCase to it.toLotEMeta()
        },
    )

    private fun buildNonCached(
        trustLists: TrustLists,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData>,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData>,
    ): ComposeChainTrust<List<NSData>, VerificationContext, NSData> = ProvisionTrustAnchorsFromLoTEs
        .eudiwIos(
            loadLoTEAndPointers = buildLoadLoTEAndPointers(verifyJwtSignature, loadLoTE),
            svcTypePerCtx = buildSvcTypePerCtx(trustLists),
            directTrust = directTrust,
            pkix = pkix,
        )
        .nonCached(buildLocations(trustLists))

    private fun buildCached(
        trustLists: TrustLists,
        ttlHours: Double,
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
        directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData>,
        pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData>,
    ): CachedTrustValidator {
        val scope = DisposableContainer()
        val validator = ProvisionTrustAnchorsFromLoTEs
            .eudiwIos(
                loadLoTEAndPointers = buildLoadLoTEAndPointers(verifyJwtSignature, loadLoTE),
                svcTypePerCtx = buildSvcTypePerCtx(trustLists),
                directTrust = directTrust,
                pkix = pkix,
            )
            .cached(
                disposableScope = scope,
                loteLocationsSupported = buildLocations(trustLists),
                ttl = ttlHours.hours,
            )
        return CachedTrustValidator(scope, validator)
    }

    private fun defaultLoadLoTE(): LoadLoTE<String> = DownloadSingleLoTE(IosLoTEHttpClient.create())

    private fun IosLoadLoTE.asLoadLoTE(): LoadLoTE<String> = LoadLoTE { uri -> loadLoTE(uri.value) }

    private fun buildLoadLoTEAndPointers(
        verifyJwtSignature: VerifyJwtSignature,
        loadLoTE: LoadLoTE<String>,
    ): LoadLoTEAndPointers = LoadLoTEAndPointers(
        constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
        verifyJwtSignature = verifyJwtSignature,
        loadLoTE = loadLoTE,
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
    ): List<NSData> = validator.getTrustAnchors.invoke(context)?.list ?: emptyList()

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
    ): IosValidationResult = when (val outcome = validator.invoke(chain, context)) {
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
    public suspend fun trustAnchors(context: VerificationContext): List<NSData> = EudiwIosTrust.trustAnchors(validator, context)

    /**
     * Validates a leaf-first DER [chain] for [context]; the anchor lookup is served from cache
     * within the TTL. Returns an [IosValidationResult] (not-trusted if the context is unsupported).
     */
    @Throws(Throwable::class)
    public suspend fun validate(chain: List<NSData>, context: VerificationContext): IosValidationResult = EudiwIosTrust.validate(validator, chain, context)

    /** Releases the in-memory caches. Call once when the validator is no longer needed. */
    public fun dispose(): Unit = scope.dispose()
}

private fun TrustList.toLotEMeta(
    issuanceContext: VerificationContext,
    revocationContext: VerificationContext?,
): LotEMeta<VerificationContext> = LotEMeta(
    buildMap {
        put(issuanceContext, LotEMeta.SvcAndEEProfile(Uri(issuanceService), certificateProfile))
        val revocationService = revocationService
        if (null != revocationContext && null != revocationService) {
            put(revocationContext, LotEMeta.SvcAndEEProfile(Uri(revocationService), null))
        }
    },
    serviceDigitalIdentityCertificateType,
)

private fun EAATrustList.toLotEMeta(): LotEMeta<VerificationContext> = LotEMeta(
    buildMap {
        put(VerificationContext.EAA(useCase), LotEMeta.SvcAndEEProfile(Uri(trustList.issuanceService), trustList.certificateProfile))
        val revocationService = trustList.revocationService
        if (null != revocationService) {
            put(VerificationContext.EAAStatus(useCase), LotEMeta.SvcAndEEProfile(Uri(revocationService), null))
        }
    },
    trustList.serviceDigitalIdentityCertificateType,
)
