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
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * Creates an instance of [GetTrustAnchors] using a keystore for trust anchor retrieval.
 *
 * @param dispatcher the coroutine dispatcher to use for fetching trust anchors from the keystore.
 *        Defaults to [Dispatchers.IO]
 * @param trustAnchorCreator the function to create trust anchors from certificates.
 *        Defaults to [JvmSecurity.DefaultTrustAnchorCreator]
 * @param cache whether to cache the trust anchors retrieved from the keystore. If true keystore will be accessed only once.
 *        Defaults to `true`.
 * @param filterAliases a predicate to filter the aliases in the keystore for trust anchor retrieval.
 *        Defaults to accepting all aliases.
 * @param cache whether to cache the trust anchors retrieved from the keystore.
 *        In this case, the keystore will be accessed only once.
 *        Defaults to `true`.
 * @param getKeystore a supplier function to provide the [KeyStore] instance to fetch trust anchors.
 *
 * @return an instance of [GetTrustAnchors] that reads trust anchors from the given keystore.
 */
public fun GetTrustAnchors.Companion.usingKeystore(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.DefaultTrustAnchorCreator,
    cache: Boolean = true,
    filterAliases: (String) -> Boolean = { true },
    getKeystore: () -> KeyStore,
): GetTrustAnchors<TrustAnchor> {
    val getTrustAnchorsFromKeystore = GetTrustAnchors {
        withContext(dispatcher) {
            getKeystore().trustAnchors(trustAnchorCreator, filterAliases)
        }
    }
    return if (cache) {
        GetTrustAnchors.once(getTrustAnchorsFromKeystore::invoke)
    } else {
        getTrustAnchorsFromKeystore
    }
}

private fun KeyStore.trustAnchors(
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
    filterAliases: (String) -> Boolean,
): List<TrustAnchor> =
    aliases().toList().filter(filterAliases).mapNotNull { alias ->
        val cert = (getCertificate(alias) as? X509Certificate)
        cert?.let(trustAnchorCreator::invoke)
    }
