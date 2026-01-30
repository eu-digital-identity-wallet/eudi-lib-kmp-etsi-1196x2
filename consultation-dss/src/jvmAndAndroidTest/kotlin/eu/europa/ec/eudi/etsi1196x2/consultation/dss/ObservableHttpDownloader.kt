package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import eu.europa.esig.dss.spi.client.http.AdvancedDataLoader
import java.util.concurrent.atomic.AtomicInteger

class ObservableHttpLoader(val proxied: AdvancedDataLoader) : AdvancedDataLoader by proxied {
    private val _callCount = AtomicInteger(0)
    val callCount: Int get() = _callCount.get()

    fun reset(): Int = _callCount.getAndUpdate { 0 }

    override fun get(url: String?): ByteArray? {
        println("Downloading $url")
        _callCount.incrementAndGet()
        return proxied.get(url)
    }

}