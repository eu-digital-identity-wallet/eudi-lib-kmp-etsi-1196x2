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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Functional interface for retrieving a list of trust anchors.
 *
 * @param TRUST_ANCHORS the type representing trust anchors.
 *
 * @see IsChainTrusted
 */
public fun interface GetTrustAnchors<out TRUST_ANCHORS : Any> :
    suspend () -> List<TRUST_ANCHORS> {

    public companion object {
        /**
         * Creates an instance of [GetTrustAnchors] that reads trust anchors from the given source only once.
         * @param source the source of trust anchors.
         * @param TRUST_ANCHORS the type representing trust anchors.
         */
        public fun <TRUST_ANCHOR : Any> once(source: suspend () -> List<TRUST_ANCHOR>): GetTrustAnchors<TRUST_ANCHOR> {
            val once = InvokeOnce(source)
            return GetTrustAnchors { once() }
        }
    }
}

private class InvokeOnce<T : Any>(
    private val source: suspend () -> T,
) : suspend () -> T {

    private val mutex = Mutex()

    @Volatile
    private var cache: T? = null

    private suspend fun invokeOnce(): T = source.invoke()

    override suspend fun invoke(): T =
        cache ?: mutex.withLock {
            // check again in case another thread read the keystore before us
            cache ?: invokeOnce().also { cache = it }
        }
}
