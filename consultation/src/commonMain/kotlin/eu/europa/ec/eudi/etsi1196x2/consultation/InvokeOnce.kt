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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InvokeOnce<T : Any>(
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
