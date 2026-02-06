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
 * @param cache whether to cache the trust anchors retrieved from the keystore.
 *        In this case, the keystore will be accessed only once.
 *        Defaults to `true`.
 * @param block a supplier function to provide the [KeyStore] instance to fetch trust anchors.
 *
 * @return an instance of [GetTrustAnchors] that reads trust anchors from the given keystore.
 */
public fun GetTrustAnchorsForSupportedQueries.Companion.usingKeyStore(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    trustAnchorCreator: TrustAnchorCreator<X509Certificate> = JvmSecurity.DefaultTrustAnchorCreator,
    cache: Boolean = true,
    supportedVerificationContexts: Set<VerificationContext>,
    regexPerVerificationContext: (VerificationContext) -> Regex,
    block: () -> KeyStore,
): GetTrustAnchorsForSupportedQueries<VerificationContext, TrustAnchor> {
    val getTrustAnchors = GetTrustAnchorsFromKeystore.fromBlocking(dispatcher, trustAnchorCreator, cache, block)
    return GetTrustAnchorsForSupportedQueries(
        getTrustAnchors.contraMap(regexPerVerificationContext),
        supportedVerificationContexts,
    )
}

public class GetTrustAnchorsFromKeystore(
    private val trustAnchorCreator: TrustAnchorCreator<X509Certificate>,
    private val getKeystore: suspend () -> KeyStore,
) : GetTrustAnchors<Regex, TrustAnchor> {

    override suspend fun invoke(query: Regex): List<TrustAnchor>? =
        getKeystore().getTrustAnchors(trustAnchorCreator, query).takeIf { it.isNotEmpty() }

    private fun KeyStore.getTrustAnchors(
        trustAnchorCreator: TrustAnchorCreator<X509Certificate>,
        query: Regex,
    ): List<TrustAnchor> {
        val filter = { alias: String -> query.matches(alias) }
        return aliases().toList().filter(filter).mapNotNull { alias ->
            val cert = (getCertificate(alias) as? X509Certificate)
            cert?.let(trustAnchorCreator::invoke)
        }
    }

    public companion object {
        public fun fromBlocking(
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            trustAnchorCreator: TrustAnchorCreator<X509Certificate> = JvmSecurity.DefaultTrustAnchorCreator,
            cache: Boolean = true,
            block: () -> KeyStore,
        ): GetTrustAnchorsFromKeystore {
            val getK = run {
                val suspendable = object : suspend () -> KeyStore {
                    override suspend fun invoke(): KeyStore {
                        return withContext(dispatcher) { block() }
                    }
                }
                if (cache) InvokeOnce(suspendable) else suspendable
            }
            return GetTrustAnchorsFromKeystore(trustAnchorCreator, getK)
        }
    }
}
