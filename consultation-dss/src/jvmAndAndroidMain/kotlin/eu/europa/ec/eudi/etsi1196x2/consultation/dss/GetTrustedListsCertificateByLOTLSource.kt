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

import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.TrustAnchorCreator
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.GetTrustedListsCertificateByLOTLSource.Companion.DEFAULT_DISPATCHER
import eu.europa.ec.eudi.etsi1196x2.consultation.dss.GetTrustedListsCertificateByLOTLSource.Companion.DEFAULT_SCOPE
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.*
import java.security.cert.TrustAnchor
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Defines a functional interface for retrieving a trusted lists certificate source
 * using a specified LOTLSource.
 *
 * The interface provides a method to asynchronously fetch a [TrustedListsCertificateSource]
 * based on the provided [LOTLSource]. It includes a companion object for creating an instance
 * using blocking logic wrapped in a coroutine-friendly structure.
 *
 * Note that in DSS:
 * - [LOTLSource] is a set of predicates to traverse a LOTL, using a [eu.europa.esig.dss.tsl.job.TLValidationJob]
 * - A [eu.europa.esig.dss.tsl.job.TLValidationJob] job aggregates matching certificates to a [TrustedListsCertificateSource]
 */
public fun interface GetTrustedListsCertificateByLOTLSource {

    /**
     * Retrieves a trusted lists certificate source based on the provided [LOTLSource].
     * @param source the LOTLSource to use for fetching the certificate source
     * @return the [TrustedListsCertificateSource]
     */
    public suspend operator fun invoke(source: LOTLSource): TrustedListsCertificateSource

    /**
     * Creates a [GetTrustAnchors] instance from this [GetTrustedListsCertificateByLOTLSource]
     * using the provided [trustAnchorCreator] and [source].
     *
     * @param trustAnchorCreator the [TrustAnchorCreator] to use for creating trust anchors from certificates
     *       Defaults to [DSSTrustAnchorCreator]
     * @param source the LOTLSource to use for fetching the certificate source
     */
    public fun asProviderFor(
        source: LOTLSource,
        trustAnchorCreator: TrustAnchorCreator<CertificateToken, TrustAnchor> = DSSTrustAnchorCreator,
    ): GetTrustAnchors<TrustAnchor> =
        GetTrustAnchors {
            invoke(source).trustAnchors(trustAnchorCreator)
        }

    public companion object {
        /**
         * The default scope for [GetTrustedListsCertificateByLOTLSourceBlocking] instances.
         * [Dispatchers.Default] + [SupervisorJob]
         */
        public val DEFAULT_SCOPE: CoroutineScope get() = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /**
         * The default coroutine dispatcher for [GetTrustedListsCertificateByLOTLSourceBlocking] instances.
         * [Dispatchers.IO]
         */
        public val DEFAULT_DISPATCHER: CoroutineDispatcher get() = Dispatchers.IO

        /**
         * Creates a [GetTrustedListsCertificateByLOTLSource] instance from blocking logic.
         *
         * @param coroutineScope the overall scope of the resulting [GetTrustedListsCertificateByLOTLSource]. By default, adds [SupervisorJob]
         *       Defaults to [DEFAULT_SCOPE]
         * @param coroutineDispatcher the coroutine dispatcher for executing the blocking logic
         *       Defaults to [DEFAULT_DISPATCHER]
         * @param clock the clock used to retrieve the current time. Defaults to [Clock.System]
         * @param expectedTrustSourceNo the expected number of trust sources
         * @param ttl the time-to-live duration for caching the certificate source. It should be set to a value higher than the average duration of executing the [block]
         * @param block the blocking function to retrieve the certificate source
         * @return the [GetTrustedListsCertificateByLOTLSource] instance
         */
        public fun fromBlocking(
            coroutineScope: CoroutineScope = DEFAULT_SCOPE,
            coroutineDispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
            clock: Clock = Clock.System,
            ttl: Duration,
            expectedTrustSourceNo: Int,
            block: (LOTLSource) -> TrustedListsCertificateSource,
        ): GetTrustedListsCertificateByLOTLSource =
            GetTrustedListsCertificateByLOTLSourceBlocking(
                coroutineScope,
                coroutineDispatcher,
                clock,
                ttl,
                expectedTrustSourceNo,
                block,
            )
    }
}

internal class GetTrustedListsCertificateByLOTLSourceBlocking(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    clock: Clock,
    ttl: Duration,
    expectedTrustSourceNo: Int,
    block: (LOTLSource) -> TrustedListsCertificateSource,
) : GetTrustedListsCertificateByLOTLSource {

    private val cached: AsyncCache<LOTLSource, TrustedListsCertificateSource> =
        AsyncCache(scope, dispatcher, clock, ttl, expectedTrustSourceNo) { trustSource ->
            block(trustSource)
        }

    override suspend fun invoke(source: LOTLSource): TrustedListsCertificateSource = cached(source)
}
