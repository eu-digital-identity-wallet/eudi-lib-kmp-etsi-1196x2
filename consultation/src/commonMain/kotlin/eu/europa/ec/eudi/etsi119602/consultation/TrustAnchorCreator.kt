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

import eu.europa.ec.eudi.etsi119602.PKIObject

/**
 * A way to create a trust anchor from a [PKIObject]
 *
 * @param TRUST_ANCHOR the type representing a trust anchor
 */
public fun interface TrustAnchorCreator<in CERT : Any, out TRUST_ANCHOR : Any> {
    public fun invoke(cert: CERT): TRUST_ANCHOR

    public companion object
}

public inline fun <CERT : Any, TRUST_ANCHOR : Any, CERT2 : Any> TrustAnchorCreator<CERT, TRUST_ANCHOR>.contraMap(
    crossinline f: (CERT2) -> CERT,
): TrustAnchorCreator<CERT2, TRUST_ANCHOR> = TrustAnchorCreator { cert2 -> invoke(f(cert2)) }
