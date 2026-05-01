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
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.esig.dss.model.tsl.LOTLInfo
import eu.europa.esig.dss.model.tsl.TLInfo
import eu.europa.esig.dss.model.tsl.TLValidationJobSummary
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.cert.TrustAnchor

/**
 * An adapter class for [GetTrustAnchors] that uses a trusted list of trust anchors (LoTL) to retrieve trust anchors.
 * @param dssOptions The options for configuring the DSS library.
 */
public class GetTrustAnchorsFromLoTL(
    private val dssOptions: DssOptions = DssOptions.Default,
) : GetTrustAnchors<LOTLSource, TrustAnchor> {

    private val log = LoggerFactory.getLogger(GetTrustAnchorsFromLoTL::class.java)

    /**
     * The summary of the last LOTL/TL validation job execution.
     * Contains download, parsing, and validation results for each processed LOTL and TL.
     * Useful for debugging why trust anchors might be empty.
     */
    public var lastValidationSummary: TLValidationJobSummary? = null
        private set

    override suspend fun invoke(query: LOTLSource): NonEmptyList<TrustAnchor>? =
        withContext(dssOptions.validateJobDispatcher + CoroutineName("DSS-LOTL-${query.url}")) {
            val trustAnchors = runValidationJobFor(query)
            NonEmptyList.nelOrNull(trustAnchors)
        }

    /**
     * Runs the DSS validation job for the given LOTL source.
     *
     * Note: This method performs blocking I/O operations via the DSS library's
     * [TLValidationJob.onlineRefresh]. It is called from a coroutine context
     * with the [DssOptions.validateJobDispatcher] (defaults to [kotlinx.coroutines.Dispatchers.IO]),
     * ensuring blocking operations don't block coroutine threads.
     *
     * @param lotlSource the LOTL source to validate
     * @return list of trust anchors extracted from the validated trusted lists
     */
    private fun runValidationJobFor(lotlSource: LOTLSource): List<TrustAnchor> =
        with(TrustedListsCertificateSource()) {
            val job = createValidationJob(lotlSource)
            job.onlineRefresh()
            lastValidationSummary = job.summary
            logValidationSummary(lastValidationSummary!!)
            certificates.map { it.toTrustAnchor() }
        }

    private fun TrustedListsCertificateSource.createValidationJob(
        lotlSource: LOTLSource,
    ): TLValidationJob =
        TLValidationJob().apply {
            setListOfTrustedListSources(lotlSource)
            setOnlineDataLoader(dssOptions.loader)
            setTrustedListCertificateSource(this@createValidationJob)
            setSynchronizationStrategy(dssOptions.synchronizationStrategy)
            setCacheCleaner(
                CacheCleaner().apply {
                    setCleanMemory(dssOptions.cleanMemory)
                    setCleanFileSystem(dssOptions.cleanFileSystem)
                    setDSSFileLoader(dssOptions.loader)
                },
            )
            if (dssOptions.executorService != null) {
                setExecutorService(dssOptions.executorService)
            }
        }

    private fun logValidationSummary(summary: TLValidationJobSummary) {
        log.info("=== LOTL/TL Validation Summary ===")

        val lotlInfos = summary.lotlInfos
        if (lotlInfos.isEmpty()) {
            log.warn("No LOTLs were processed!")
        } else {
            log.info("Processed ${lotlInfos.size} LOTL(s):")
            lotlInfos.forEach { logLOTLInfo(it) }
        }

        val otherTLInfos = summary.otherTLInfos
        if (otherTLInfos.isEmpty()) {
            log.info("No standalone TLs (only LOTL-discovered TLs expected)")
        } else {
            log.info("Processed ${otherTLInfos.size} standalone TL(s):")
            otherTLInfos.forEach { logTLInfo(it) }
        }

        log.info("Total processed LOTLs: ${summary.numberOfProcessedLOTLs}")
        log.info("Total processed TLs: ${summary.numberOfProcessedTLs}")
        log.info("=== End Validation Summary ===")
    }

    private fun logLOTLInfo(info: LOTLInfo) {
        val parsing = info.parsingCacheInfo
        log.info("  LOTL: ${info.url}")
        log.info("    Download: ${info.downloadCacheInfo}")
        log.info("    Parsing: ${info.parsingCacheInfo}")
        log.info("    Validation: ${info.validationCacheInfo}")
        log.info("    Territory: ${parsing?.territory}")
        log.info("    Sequence: ${parsing?.sequenceNumber}")
        log.info("    Version: ${parsing?.version}")
        log.info("    TL pointers: ${parsing?.tlOtherPointers?.size}")
        log.info("    LOTL pointers: ${parsing?.lotlOtherPointers?.size}")
        log.info("    Certificates: ${parsing?.certNumber}")
    }

    private fun logTLInfo(info: TLInfo) {
        val parsing = info.parsingCacheInfo
        log.info("  TL: ${info.url}")
        log.info("    Download: ${info.downloadCacheInfo}")
        log.info("    Parsing: ${info.parsingCacheInfo}")
        log.info("    Validation: ${info.validationCacheInfo}")
        log.info("    Synchronized: ${parsing?.isSynchronized}")
        log.info("    Territory: ${parsing?.territory}")
        log.info("    Sequence: ${parsing?.sequenceNumber}")
        log.info("    Version: ${parsing?.version}")
        log.info("    TSPs: ${parsing?.tspNumber}")
        log.info("    Services: ${parsing?.tsNumber}")
        log.info("    Certificates: ${parsing?.certNumber}")
    }

    private fun CertificateToken.toTrustAnchor(): TrustAnchor =
        TrustAnchor(certificate, null)
}
