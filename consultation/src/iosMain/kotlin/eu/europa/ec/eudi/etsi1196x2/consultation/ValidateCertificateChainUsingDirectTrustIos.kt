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

import platform.Foundation.NSData

/**
 * Direct-trust validator for iOS.
 *
 * Matches the chain's head (leaf) certificate against the trust anchors by comparing
 * DER-encoded bytes. Identity is by content, not [NSData] reference, since Kotlin/Native's
 * default `==` on [NSData] is reference identity.
 */
public val ValidateCertificateChainUsingDirectTrustIos:
    ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData, ByteArrayKey> =
    ValidateCertificateChainUsingDirectTrust(
        headCertificateId = { chain ->
            ByteArrayKey(requireNotNull(chain.firstOrNull()) { "Chain cannot be empty" }.toByteArray())
        },
        trustToCertificateId = { ByteArrayKey(it.toByteArray()) },
    )

/**
 * A content-addressable wrapper around DER bytes, used as the comparison key for
 * direct-trust matching. [NSData] cannot be used directly because its `equals`/`hashCode`
 * are reference-based in Kotlin/Native.
 */
public class ByteArrayKey(public val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
