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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.TrustedEntityService
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateCertificateConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ensureAllMet

/**
 * A loaded list of trusted entities, including
 * other (pointer) lists
 *
 * @param list the main list of trusted entities
 * @param otherLists other lists of trusted entities pointed by [list]
 */
public data class LoadedLoTE(
    val list: ListOfTrustedEntities,
    val otherLists: List<ListOfTrustedEntities>,
)

public class GetTrustAnchorsFromLoTE<out TRUST_ANCHOR : Any, CERT : Any>(
    private val loTEDownloadUrl: String,
    private val certificateConstraints: EvaluateCertificateConstraint<CERT>?,
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
    private val extractCertificate: (TRUST_ANCHOR) -> CERT,
    private val getCertInfo: suspend (CERT) -> String = { it.toString() },
) : GetTrustAnchors<URI, TRUST_ANCHOR> {

    /**
     * Retrieves trust anchors for the specified service type.
     *
     * This method:
     * 1. Loads the LoTE (and its pointers) from cache or downloads if needed
     * 2. Extracts services of the requested type from all loaded lists
     * 3. Converts service digital identities to trust anchors
     *
     * @param query the service type URI to search for
     * @return a non-empty list of trust anchors, or null if no services of the requested type are found
     * @throws IllegalStateException if this instance has been closed
     */
    @Throws(IllegalStateException::class)
    override suspend fun invoke(query: URI): NonEmptyList<TRUST_ANCHOR>? {
        // Load LoTE and pointers (cached in memory)
        val loadedLoTE = run {
            val events = loadLoTEAndPointers(loTEDownloadUrl)
            val result = LoTELoadResult.collect(events, continueOnProblem)
            result.toLoadedLoTE()
        }
        checkNotNull(loadedLoTE) { "Failed to load LoTE from $loTEDownloadUrl" }

        // Extract trust anchors from services of the requested type
        val trustAnchors = loadedLoTE.servicesOfType(query).flatMap { trustedService ->
            createTrustAnchors(trustedService.information.digitalIdentity)
        }
        ensureCertificateConstraintsAreMet(trustAnchors)
        return NonEmptyList.nelOrNull(trustAnchors)
    }

    private fun LoTELoadResult.toLoadedLoTE(): LoadedLoTE? =
        list?.let { mainList -> LoadedLoTE(list = mainList.lote, otherLists = otherLists.map { it.lote }) }

    private suspend fun ensureCertificateConstraintsAreMet(
        anchors: List<TRUST_ANCHOR>,
    ) {
        val evaluator = certificateConstraints ?: return
        val certs = anchors.map { extractCertificate(it) }
        try {
            evaluator.ensureAllMet(certs, getCertInfo)
        } catch (e: IllegalStateException) {
            val msg = "Found invalid trust anchors to the LoTE loaded from $loTEDownloadUrl"
            throw IllegalStateException(msg, e)
        }
    }

    //
    // Helper extensions
    //

    private fun LoadedLoTE.servicesOfType(svcType: URI): List<TrustedEntityService> =
        (listOf(list) + otherLists).flatMap { it.servicesOf(svcType) }

    private fun ListOfTrustedEntities.servicesOf(svcType: URI): List<TrustedEntityService> =
        entities.orEmpty()
            .flatMap { it.services.filter { svc -> svc.information.typeIdentifier == svcType } }
}
