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
 * Creates an instance of [IsChainTrusted] using a keystore for trust anchor retrieval.
 *
 * @param validateCertificateChain the implementation of [ValidateCertificateChain] to validate a certificate chain.
 * @param trustAnchorCreator the function to create trust anchors from certificates.
 * @param filterAliases a predicate to filter the aliases in the keystore for trust anchor retrieval.
 * @param cache whether to cache the trust anchors retrieved from the keystore. In this case, keystore will be accessed only once.
 * @param getKeystore a supplier function to provide the [KeyStore] instance to fetch trust anchors.
 * @return an instance of [IsChainTrusted] configured with the provided certificate chain validator and trust anchor retrieval function.
 */
public fun IsChainTrusted.Companion.usingKeystore(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.TRUST_ANCHOR_WITH_NO_NAME_CONSTRAINTS,
    cache: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    filterAliases: (String) -> Boolean = { true },
    getKeystore: () -> KeyStore,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> {
    val getTrustAnchorsFromKeystore =
        getTrustAnchorsFromKeyStore(trustAnchorCreator, dispatcher, filterAliases, getKeystore)
    val actual =
        if (cache) {
            GetTrustAnchors.once(getTrustAnchorsFromKeystore::invoke)
        } else {
            getTrustAnchorsFromKeystore
        }
    return IsChainTrusted(validateCertificateChain, actual)
}

internal fun getTrustAnchorsFromKeyStore(
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
    dispatcher: CoroutineDispatcher,
    filterAliases: (String) -> Boolean,
    getKeystore: () -> KeyStore,
): GetTrustAnchors<TrustAnchor> =
    GetTrustAnchors {
        withContext(dispatcher) {
            getKeystore().trustAnchors(trustAnchorCreator, filterAliases)
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
