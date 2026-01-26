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
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

public fun IsChainTrusted.Companion.usingKeystore(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.trustAnchorCreator(),
    filterAliases: (String) -> Boolean = { true },
    keystore: KeyStore,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> =
    usingKeystore(validateCertificateChain, trustAnchorCreator, filterAliases) { keystore }

public fun IsChainTrusted.Companion.usingKeystore(
    validateCertificateChain: ValidateCertificateChainJvm = ValidateCertificateChainJvm(),
    trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> = JvmSecurity.trustAnchorCreator(),
    filterAliases: (String) -> Boolean = { true },
    getKeystore: suspend () -> KeyStore,
): IsChainTrusted<List<X509Certificate>, TrustAnchor> {
    val getTrustAnchorsFromKeystore = GetTrustAnchorsFromKeystore(trustAnchorCreator, filterAliases, getKeystore)
    return IsChainTrusted.Companion(validateCertificateChain, getTrustAnchorsFromKeystore)
}

internal class GetTrustAnchorsFromKeystore(
    private val trustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor>,
    private val filterAliases: (String) -> Boolean,
    private val getKeystore: suspend () -> KeyStore,
) : suspend () -> List<TrustAnchor> {

    private val readKeystore = Mutex()
    private var trustAnchors: List<TrustAnchor>? = null

    private suspend fun readTrustAnchors(): List<TrustAnchor> =
        withContext(Dispatchers.IO) {
            val keystore = getKeystore()
            keystore.aliases().toList().filter(filterAliases).mapNotNull { alias ->
                val cert = (keystore.getCertificate(alias) as? X509Certificate)
                cert?.let(trustAnchorCreator::invoke)
            }
        }

    override suspend fun invoke(): List<TrustAnchor> =
        trustAnchors ?: readKeystore.withLock {
            // check again in case another thread read the keystore before us
            trustAnchors ?: readTrustAnchors().also { trustAnchors = it }
        }
}
