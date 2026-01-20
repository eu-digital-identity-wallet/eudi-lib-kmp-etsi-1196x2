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
package eu.europa.ec.eudi.etsi119602

import java.security.cert.X509Certificate

public fun ListOfTrustedEntities.certificatesOf(serviceTypeIdentifier: URI): List<X509Certificate> =
    buildList {
        entities?.forEach { entity ->
            entity.services.forEach { service ->
                if (service.information.typeIdentifier == serviceTypeIdentifier) {
                    service.information.digitalIdentity.x509Certificates?.forEach { pkiObj ->
                        add(pkiObj.x509Certificate())
                    }
                }
            }
        }
    }
