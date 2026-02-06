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
 * Creates an instance of [GetTrustAnchorsFromSource] using a keystore for trust anchor retrieval.
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
 * @param block a supplier function to provide the [KeyStore] instance to fetch trust anchors.
 *
 * @return an instance of [GetTrustAnchorsFromSource] that reads trust anchors from the given keystore.
 */
public fun GetTrustAnchorsForSupportedQueries.Companion.usingKeyStore(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.DefaultTrustAnchorCreator,
    cache: Boolean = true,
    queryPerVerificationContext: Map<VerificationContext, Regex>,
    block: () -> KeyStore,
): GetTrustAnchorsForSupportedQueries<VerificationContext, TrustAnchor> {
    require(queryPerVerificationContext.isNotEmpty()) { "At least one query must be provided" }
    val doubleQueries = queryPerVerificationContext.values.groupBy { it }.filterValues { it.size > 1 }.keys
    require(doubleQueries.isEmpty()) { "Queries must be unique: $doubleQueries" }

    return GetTrustAnchorsForSupportedQueries(
        GetTrustAnchorsFromSource.usingKeyStore(
            dispatcher = dispatcher,
            trustAnchorCreator = trustAnchorCreator,
            cache = cache,
            { checkNotNull(queryPerVerificationContext[it]) },
            block,
        ),
        queryPerVerificationContext.keys,
    )
}

public fun GetTrustAnchorsFromSource.Companion.usingKeyStore(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.DefaultTrustAnchorCreator,
    cache: Boolean = true,
    regexForContext: (VerificationContext) -> Regex,
    block: () -> KeyStore,
): GetTrustAnchorsFromSource<VerificationContext, TrustAnchor> =
    GetTrustAnchorsFromKeystore.fromBlocking(dispatcher, trustAnchorCreator, cache, block)
        .contraMap(regexForContext)

private class GetTrustAnchorsFromKeystore(
    private val trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
    private val getKeystore: suspend () -> KeyStore,
) : GetTrustAnchorsFromSource<Regex, TrustAnchor> {

    override suspend fun invoke(query: Regex): List<TrustAnchor>? =
        getKeystore().getTrustAnchors(trustAnchorCreator, query).takeIf { it.isNotEmpty() }

    private fun KeyStore.getTrustAnchors(
        trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
        query: Regex,
    ): List<TrustAnchor> {
        val filter = { alias: String -> query.matches(alias) }
        return aliases().toList().filter(filter).mapNotNull { alias ->
            val cert = (getCertificate(alias) as? X509Certificate)
            cert?.let(trustAnchorCreator::invoke)
        }
    }

    companion object {
        fun fromBlocking(
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.DefaultTrustAnchorCreator,
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
