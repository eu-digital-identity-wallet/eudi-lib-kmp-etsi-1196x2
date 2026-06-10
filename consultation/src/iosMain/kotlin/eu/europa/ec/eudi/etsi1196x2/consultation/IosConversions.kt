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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Copies the bytes of this [NSData] into a Kotlin [ByteArray].
 */
@OptIn(ExperimentalForeignApi::class)
public fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}

/**
 * Wraps this [ByteArray] in an [NSData] without sharing the backing buffer.
 */
@OptIn(ExperimentalForeignApi::class)
public fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

/**
 * Value-based equality for [NSData]. Kotlin/Native's default `==` on [NSData] is
 * reference identity, which is never what we want when matching DER-encoded
 * certificates, so always compare the underlying bytes.
 */
internal fun NSData.contentEqualsData(other: NSData): Boolean =
    this.toByteArray().contentEquals(other.toByteArray())
