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
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.*

public class GetTrustAnchorsFromLoTE(
    private val lote: ListOfTrustedEntities,
) : GetTrustAnchors<URI, PKIObject> {

    override suspend fun invoke(query: URI): NonEmptyList<PKIObject>? {
        val certs = lote.entities.orEmpty().flatMap { trustedEntity ->
            trustedEntity.services
                .filter { it.information.typeIdentifier == query }
                .flatMap { it.information.digitalIdentity.x509Certificates.orEmpty() }
        }
        return NonEmptyList.nelOrNull(certs)
    }
}

public fun GetTrustAnchorsForSupportedQueries.Companion.usingLoTE(
    lotePerProfile: Map<Map<VerificationContext, URI>, ListOfTrustedEntities>,
): GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> {
    var result = GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject>()
    lotePerProfile.forEach { (svcTypesPerVerificationContext, lote) ->
        result += GetTrustAnchorsFromLoTE(lote).transform(svcTypesPerVerificationContext)
    }
    return result
}
