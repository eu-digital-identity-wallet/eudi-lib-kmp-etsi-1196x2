import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    kotlin("multiplatform") version "2.2.21"
}

// Version of the library under test. CI passes the version it published to Maven Local;
// the default mirrors gradle.properties and is only a convenience for local runs.
val eudiVersion: String = providers.gradleProperty("eudiVersion").getOrElse("0.4.0-alpha.2-SNAPSHOT")

kotlin {
    // Stand-in for the "<path-to>/PKIXBridge.xcframework" placeholder in the docs.
    // Must be an absolute path: the linker resolves `-F` relative to this project directory,
    // not the repository root.
    val pkix = providers.gradleProperty("pkixXcframework").get()

    // Output of `xcode-select -p`, followed by the toolchain's Swift ABI-shim directory.
    val swiftShims = providers.exec { commandLine("xcode-select", "-p") }
        .standardOutput.asText.get().trim() +
        "/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift"

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // The README entry point, consumed like any other external library.
            // Pulls in etsi-1196x2-consultation and etsi-119602-data-model transitively.
            implementation("eu.europa.ec.eudi:etsi-119602-consultation:$eudiVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    // ==== The documented snippet, verbatim — README.md#ios / docs/iOS-PKIXBridge.md ====
    // If either side changes without the other, this build fails: that is the point.

    // Device (iosArm64)
    iosArm64().binaries.withType<TestExecutable>().configureEach {
        linkerOpts("-framework", "PKIXBridge", "-F${pkix}/ios-arm64")
        linkerOpts("-L${swiftShims}/iphoneos")
    }

    // Simulators (iosX64, iosSimulatorArm64) — shared fat slice (arm64 + x86_64)
    listOf(iosX64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.withType<TestExecutable>().configureEach {
            linkerOpts("-framework", "PKIXBridge", "-F${pkix}/ios-arm64_x86_64-simulator")
            linkerOpts("-L${swiftShims}/iphonesimulator")
        }
    }
}