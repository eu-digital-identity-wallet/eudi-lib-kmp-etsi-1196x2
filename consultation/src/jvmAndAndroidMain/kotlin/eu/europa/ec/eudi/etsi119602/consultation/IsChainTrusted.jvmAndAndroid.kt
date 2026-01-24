/*
 * Copyright (c) 2023 European Commission
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
import eu.europa.ec.eudi.etsi119602.x509CertificateOf
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

//
// JVM Implementation
//
@Deprecated("Too complex")
public fun IsChainTrusted.Companion.jvmUsingLoTEs(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    getLatestListOfTrustedEntitiesByType: GetLatestListOfTrustedEntitiesByType,
): (TrustSource.LoTE) -> IsChainTrusted<List<X509Certificate>, TrustAnchor> = { trustSource ->
    IsChainTrusted(validateCertificateChain) {
        val list = getLatestListOfTrustedEntitiesByType(trustSource.loteType)
        val trustAnchorCreator =
            trustAnchorCreatorFromPkiObject(validateCertificateChain.certificateFactory)
        list?.let { lote ->
            trustAnchorCreator.trustAnchorsOfType(lote, trustSource.serviceType)
        } ?: emptyList()
    }
}

private fun trustAnchorCreatorFromPkiObject(certificateFactory: CertificateFactory) =
    TrustAnchorCreator.jvm().contraMap<X509Certificate, TrustAnchor, PKIObject> { pkiObj ->
        certificateFactory.x509CertificateOf(pkiObj)
    }

internal fun <TRUST_ANCHOR : Any> TrustAnchorCreator<PKIObject, TRUST_ANCHOR>.trustAnchorsOfType(
    lote: ListOfTrustedEntities,
    serviceType: String,
): List<TRUST_ANCHOR> =
    buildList {
        lote.entities?.forEach { entity ->
            entity.services.forEach { service ->
                val srvInformation = service.information
                if (srvInformation.typeIdentifier == serviceType) {
                    srvInformation.digitalIdentity.x509Certificates?.forEach { pkiObj ->
                        add(invoke(pkiObj))
                    }
                }
            }
        }
    }
