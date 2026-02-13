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
import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.TrustedEntityService
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.*

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

public class GetTrustAnchorsFromLoTE(
    private val loadedLote: LoadedLoTE,
) : GetTrustAnchors<URI, PKIObject> {

    override suspend fun invoke(query: URI): NonEmptyList<PKIObject>? {
        val certs =
            loadedLote.servicesOfType(query).flatMap { trustedService ->
                trustedService.information.digitalIdentity.x509Certificates.orEmpty()
            }
        return NonEmptyList.nelOrNull(certs)
    }

    private fun LoadedLoTE.servicesOfType(svcType: URI): List<TrustedEntityService> {
        return (listOf(list) + otherLists).flatMap { it.servicesOf(svcType) }
    }

    private fun ListOfTrustedEntities.servicesOf(svcType: URI): List<TrustedEntityService> =
        entities.orEmpty()
            .flatMap { it.services.filter { svc -> svc.information.typeIdentifier == svcType } }
}
