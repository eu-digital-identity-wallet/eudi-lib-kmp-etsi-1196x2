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
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource
import eu.europa.esig.dss.tsl.cache.CacheCleaner
import eu.europa.esig.dss.tsl.job.TLValidationJob
import eu.europa.esig.dss.tsl.source.LOTLSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.TrustAnchor

/**
 * An adapter class for [GetTrustAnchors] that uses a trusted list of trust anchors (LoTL) to retrieve trust anchors.
 * @param dispatcher The coroutine dispatcher to use for executing the validation job.
 *        Defaults to [Dispatchers.IO]
 * @param dssOptions The options for configuring the DSS library.
 */
public class GetTrustAnchorsFromLoTL(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dssOptions: DssOptions = DssOptions.Default,
) : GetTrustAnchors<LOTLSource, TrustAnchor> {

    override suspend fun invoke(query: LOTLSource): NonEmptyList<TrustAnchor>? =
        withContext(dispatcher + CoroutineName("DSS-LOTL-${query.url}")) {
            val trustAnchors = runValidationJobFor(query)
            NonEmptyList.nelOrNull(trustAnchors)
        }

    private fun runValidationJobFor(lotlSource: LOTLSource): List<TrustAnchor> =
        with(TrustedListsCertificateSource()) {
            createValidationJob(lotlSource).onlineRefresh()
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

    private fun CertificateToken.toTrustAnchor(): TrustAnchor =
        TrustAnchor(certificate, null)
}
