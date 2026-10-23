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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.esig.dss.spi.client.http.DataLoader
import java.util.concurrent.atomic.AtomicInteger

interface GetCounter {
    val callCount: Int
    fun resetCallCount(): Int
}

class ObservableHttpLoader(val proxied: DataLoader) :
    DataLoader by proxied,
    GetCounter {
    private val _callCount = AtomicInteger(0)
    override val callCount: Int get() = _callCount.get()

    override fun resetCallCount(): Int = _callCount.getAndUpdate { 0 }

    override fun get(url: String?): ByteArray? {
        println("${Thread.currentThread().name}: Downloading $url")
        _callCount.incrementAndGet()
        return proxied.get(url)
    }
}
