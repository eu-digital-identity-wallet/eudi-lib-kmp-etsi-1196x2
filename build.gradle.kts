plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.dependency.check) apply true
}

dependencyCheck {
    formats = listOf("XML", "HTML")
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: properties["nvdApiKey"]?.toString() ?: ""
    nvd.delay = 10000
    nvd.maxRetryCount = 2
}