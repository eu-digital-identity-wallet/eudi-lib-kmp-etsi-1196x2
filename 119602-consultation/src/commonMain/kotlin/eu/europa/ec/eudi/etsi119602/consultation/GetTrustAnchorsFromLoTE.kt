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
import eu.europa.ec.eudi.etsi119602.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchors
import eu.europa.ec.eudi.etsi1196x2.consultation.NonEmptyList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * An implementation of [GetTrustAnchors] that retrieves trust anchors from a List of Trusted Entities (LoTE).
 *
 * This class handles:
 * 1. Loading a LoTE from a specified URL, including any other lists pointed to by the main list.
 * 2. Filtering services based on the requested service type URI.
 * 3. Converting service digital identities into the desired [TRUST_ANCHOR] type.
 * 4. Validating extracted certificates against optional constraints.
 *
 * It uses a [LoadLoTEAndPointers] service which typically implements caching. Access to the [invoke]
 * method is synchronized to prevent multiple concurrent downloads when the cache is empty.
 *
 * @param TRUST_ANCHOR the type of the trust anchor to produce
 * @param loTEDownloadUrl the URI where the LoTE is located
 * @param loadLoTEAndPointers the service used to load the LoTE and its pointers
 * @param continueOnProblem strategy for handling errors during the loading process
 * @param createTrustAnchors factory function to create trust anchors from digital identities
 */
public class GetTrustAnchorsFromLoTE<out TRUST_ANCHOR : Any>(
    private val loTEDownloadUrl: Uri,
    private val loadLoTEAndPointers: LoadLoTEAndPointers,
    private val continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    private val createTrustAnchors: (ServiceDigitalIdentity) -> List<TRUST_ANCHOR>,
) : GetTrustAnchors<Uri, TRUST_ANCHOR> {

    private val mutex = Mutex()

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
     * @throws IllegalStateException in case of a loading problem or if the certificate constraints are not met
     */
    override suspend fun invoke(query: Uri): NonEmptyList<TRUST_ANCHOR>? = mutex.withLock {
        val trustAnchors =
            loadLoTe().trustAnchorsFor(query)

        NonEmptyList.nelOrNull(trustAnchors)
    }

    private suspend fun loadLoTe(): LoadedLoTE {
        val events = loadLoTEAndPointers(loTEDownloadUrl)
        val result = LoTELoadResult.collect(events, continueOnProblem)
        val loaded = result.toLoadedLoTE()
        return loaded ?: raiseLoadingProblems(result)
    }

    private fun LoTELoadResult.toLoadedLoTE(): LoadedLoTE? =
        list?.let { mainList -> LoadedLoTE(list = mainList.lote, otherLists = otherLists.map { it.lote }) }

    private fun LoadedLoTE.trustAnchorsFor(svcType: Uri): List<TRUST_ANCHOR> =
        servicesOfType(svcType).flatMap { trustedService ->
            trustedService.information.digitalIdentity.trustAnchors()
        }

    private fun ListOfTrustedEntities.servicesOf(svcType: Uri): List<TrustedEntityService> =
        entities.orEmpty()
            .flatMap { it.services.filter { svc -> svc.information.typeIdentifier == svcType } }

    private fun LoadedLoTE.servicesOfType(svcType: Uri): List<TrustedEntityService> =
        (listOf(list) + otherLists).flatMap { it.servicesOf(svcType) }

    private fun ServiceDigitalIdentity.trustAnchors(): List<TRUST_ANCHOR> =
        createTrustAnchors(this)

    private fun raiseLoadingProblems(result: LoTELoadResult): Nothing {
        val causes = mutableListOf<Throwable?>()
        fun LoadLoTEAndPointers.Event.Problem.addCause() {
            val cause = cause()
            if (cause != null) {
                causes.add(cause)
                causes.addAll(cause.suppressedExceptions)
            }
        }

        val msg = buildString {
            appendLine("Failed to load LoTE from $loTEDownloadUrl\n")
            appendLine("Problems encountered during loading:")
            result.problems.forEachIndexed { index, problem ->
                problem.addCause()
                appendLine("${index + 1}. $problem")
            }
        }
        throw IllegalStateException(msg).apply {
            causes.forEach { cause -> cause?.let { addSuppressed(it) } }
        }
    }

    private fun LoadLoTEAndPointers.Event.Problem.cause(): Throwable? = when (this) {
        is LoadLoTEAndPointers.Event.CircularReferenceDetected -> null
        is LoadLoTEAndPointers.Event.Error -> cause
        is LoadLoTEAndPointers.Event.FailedToParseJwt -> cause
        is LoadLoTEAndPointers.Event.InvalidJWTSignature -> cause
        is LoadLoTEAndPointers.Event.MaxDepthReached -> null
        is LoadLoTEAndPointers.Event.MaxListsReached -> null
        is LoadLoTEAndPointers.Event.ResourceNotFound -> null
    }
}
