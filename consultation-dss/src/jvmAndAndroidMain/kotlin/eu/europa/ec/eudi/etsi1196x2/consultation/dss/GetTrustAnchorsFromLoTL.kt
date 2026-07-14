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
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import eu.europa.esig.dss.validation.job.cache.CacheCleaner
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
        if (log.isInfoEnabled) {
            log.info(logMsg(summary))
        }
    }

    private fun CertificateToken.toTrustAnchor(): TrustAnchor =
        TrustAnchor(certificate, null)
}

private fun logMsg(summary: TLValidationJobSummary): String =
    buildString {
        appendLine("=== LOTL/TL Validation Summary ===")

        val lotlInfos = summary.lotlInfos
        if (lotlInfos.isEmpty()) {
            appendLine("No LOTLs were processed!")
        } else {
            appendLine("Processed ${lotlInfos.size} LOTL(s):")
            lotlInfos.forEach { appendLine(logMsg(it)) }
        }

        val otherTLInfos = summary.otherTLInfos
        if (otherTLInfos.isEmpty()) {
            appendLine("No standalone TLs (only LOTL-discovered TLs expected)")
        } else {
            appendLine("Processed ${otherTLInfos.size} standalone TL(s):")
            otherTLInfos.forEach { logMsg(it) }
        }

        appendLine("Total processed LOTLs: ${summary.numberOfProcessedLOTLs}")
        appendLine("Total processed TLs: ${summary.numberOfProcessedTLs}")
        appendLine("=== End Validation Summary ===")
    }

private fun logMsg(lotl: LOTLInfo): String =
    buildString {
        val parsing = lotl.parsingCacheInfo
        appendLine("  LOTL: ${lotl.url}")
        appendLine("    Download: ${lotl.downloadCacheInfo}")
        appendLine("    Parsing: ${lotl.parsingCacheInfo}")
        appendLine("    Validation: ${lotl.validationCacheInfo}")
        appendLine("    Territory: ${parsing?.territory}")
        appendLine("    Sequence: ${parsing?.sequenceNumber}")
        appendLine("    Version: ${parsing?.version}")
        appendLine("    TL pointers: ${parsing?.tlOtherPointers?.size}")
        appendLine("    LOTL pointers: ${parsing?.lotlOtherPointers?.size}")
        appendLine("    Certificates: ${parsing?.certNumber}")
    }

private fun logMsg(tl: TLInfo): String =
    buildString {
        val parsing = tl.parsingCacheInfo
        appendLine("  TL: ${tl.url}")
        appendLine("    Download: ${tl.downloadCacheInfo}")
        appendLine("    Parsing: ${tl.parsingCacheInfo}")
        appendLine("    Validation: ${tl.validationCacheInfo}")
        appendLine("    Synchronized: ${parsing?.isSynchronized}")
        appendLine("    Territory: ${parsing?.territory}")
        appendLine("    Sequence: ${parsing?.sequenceNumber}")
        appendLine("    Version: ${parsing?.version}")
        appendLine("    TSPs: ${parsing?.tspNumber}")
        appendLine("    Services: ${parsing?.tsNumber}")
        appendLine("    Certificates: ${parsing?.certNumber}")
    }
