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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.security.cert.TrustAnchor
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Creates an instance of [GetTrustAnchorsForSupportedQueries] using a trusted list of trust anchors (LoTL) and a file cache loader.
 * The implementation has the following characteristics:
 * - Utilizes a file cache loader to load trusted lists from the file system
 * - Depending on the file cache loader configuration, the file downloader may use an online fetcher to
 *   refresh the file cache.
 * - Given that [FileCacheDataLoader] is blocking, it creates a suspendable and cachable wrapper around it using
 *   [GetTrustAnchorsFromLoTL].
 *
 * The example creates an [IsChainTrustedForContext] that:
 * - Supports PUB EAA
 * - Caches calls in memory for 10 minutes
 * - Every 10 minutes, the file cache is being used
 * - Every 24 hours, the trusted lists are fetched from the internet
 *
 * ```kotlin
 *
 *   GetTrustAnchorsForSupportedQueries.usingLoTL(
 *     dssOptions = DssOptions.usingFileCacheDataLoader(
 *         fileCacheExpiration = 24.hours,
 *         cacheDirectory = createTempDirectory("lotl-cache"),
 *     ),
 *     sourcePerVerification = buildMap {
 *         put(VerificationContext.PubEAA, lotlSource(PUB_EAA_SVC_TYPE))
 *     },
 *     coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
 *     coroutineDispatcher = Dispatchers.IO,
 *     ttl = 10.seconds,
 *  )
 *
 * ````
 *

 * @param sourcePerVerification a map of verification contexts to trusted list sources
 * @param validateCertificateChain the function used to validate a given certificate chain
 *        Defaults to [ValidateCertificateChainJvm.Default]
 * @param dssAdapter the DSS adapter to use for retrieving the trusted lists certificate source
 *        Defaults to [DSSAdapter.Default]
 * @param clock the clock used to retrieve the current time
 *        Defaults to [Clock.System]
 * @param coroutineScope the overall scope that controls [fileCacheLoader]
 *        Defaults to [GetTrustedListsCertificateByLOTLSource.DEFAULT_SCOPE]
 * @param coroutineDispatcher the coroutine dispatcher for executing the blocking logic of [fileCacheLoader]
 *        Defaults to [GetTrustedListsCertificateByLOTLSource.DEFAULT_DISPATCHER]
 * @param ttl the time-to-live duration for caching the certificate source.
 *
 */
public fun <CTX : Any> GetTrustAnchorsForSupportedQueries.Companion.usingLoTL(
    coroutineScope: CoroutineScope = GetTrustAnchors.DEFAULT_SCOPE,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: Clock = Clock.System,
    ttl: Duration,
    dssOptions: DssOptions = DssOptions.Default,
    queryPerVerificationContext: Map<CTX, LOTLSource>,
): GetTrustAnchorsForSupportedQueries<CTX, TrustAnchor> {
    require(queryPerVerificationContext.isNotEmpty()) {
        "At least one trusted list source must be provided"
    }

    val doubleQueries = queryPerVerificationContext.values.groupBy { it }.filterValues { it.size > 1 }.keys
    require(doubleQueries.isEmpty()) { "Queries must be unique: $doubleQueries" }

    val getTrustAnchorsFromLoTL: GetTrustAnchors<CTX, TrustAnchor> =
        GetTrustAnchorsFromLoTL(
            dispatcher = coroutineDispatcher,
            dssOptions = dssOptions,
        ).cached(
            coroutineScope = coroutineScope,
            expectedQueries = queryPerVerificationContext.size,
            ttl = ttl,
            clock = clock,
        ).contraMap { ctx -> checkNotNull(queryPerVerificationContext[ctx]) }

    return GetTrustAnchorsForSupportedQueries(queryPerVerificationContext.keys, getTrustAnchorsFromLoTL)
}
