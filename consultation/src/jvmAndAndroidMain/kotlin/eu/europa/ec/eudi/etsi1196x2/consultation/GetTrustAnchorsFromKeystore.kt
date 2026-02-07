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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * Creates an instance of [GetTrustAnchors] using a keystore for trust anchor retrieval.
 *
 * @param dispatcher the coroutine dispatcher to use for fetching trust anchors from the keystore.
 *        Defaults to [Dispatchers.IO]
 * @param keystore the keystore to use.
 * @param supportedVerificationContexts the set of supported verification contexts.
 * @param regexPerVerificationContext a function that maps a verification context to a regular expression
 *
 * @return an instance of [GetTrustAnchorsForSupportedQueries] that reads trust anchors from the given keystore.
 */
public fun <CTX : Any> GetTrustAnchorsForSupportedQueries.Companion.usingKeyStore(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    keystore: KeyStore,
    supportedVerificationContexts: Set<CTX>,
    regexPerVerificationContext: (CTX) -> Regex,
): GetTrustAnchorsForSupportedQueries<CTX, TrustAnchor> {
    val getTrustAnchors = GetTrustAnchorsFromKeystore(dispatcher, keystore)
    return GetTrustAnchorsForSupportedQueries(
        supportedVerificationContexts,
        getTrustAnchors.contraMap(regexPerVerificationContext),
    )
}

public class GetTrustAnchorsFromKeystore(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val keystore: KeyStore,
) : GetTrustAnchors<Regex, TrustAnchor> {

    override suspend fun invoke(query: Regex): NonEmptyList<TrustAnchor>? =
        withContext(dispatcher + CoroutineName("GetTrustAnchorsFromKeystore")) {
            keystore.getTrustAnchors(query).let { NonEmptyList.nelOrNull(it) }
        }

    private fun KeyStore.getTrustAnchors(query: Regex): List<TrustAnchor> =
        buildList {
            for (alias in aliases()) {
                if (query.matches(alias)) {
                    getCertificate(alias)?.trustAnchor()?.let { add(it) }
                }
            }
        }

    private fun Certificate.trustAnchor(): TrustAnchor? =
        (this as? X509Certificate)?.let { TrustAnchor(it, null) }
}
