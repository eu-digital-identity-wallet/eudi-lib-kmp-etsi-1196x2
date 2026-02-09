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

rootProject.name = "eudi-lib-kmp-etsi-1196x2"
include(":etsi-1196x2-consultation")
include(":etsi-1196x2-consultation-dss")
include(":etsi-119602")
include(":etsi-119602:data-model")
include(":etsi-119602:eu-profiles")
project(":etsi-1196x2-consultation").projectDir = file("consultation")
project(":etsi-1196x2-consultation-dss").projectDir = file("consultation-dss")
project(":etsi-119602").projectDir = file("119602")

