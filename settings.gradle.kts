//
// Enables declaring module dependencies in a safer manner
// Instead using
//      implementation(project(":core:cache"))
// You can use
//      implementation(projects.core.cache)
//
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    //
    // Provides a repository for downloading JVMs
    //
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "eudi-lib-kmp-etsi-119602"
include(":eudi-lib-kmp-etsi-119602-data-model")
project(":eudi-lib-kmp-etsi-119602-data-model").projectDir = file("data-model")
