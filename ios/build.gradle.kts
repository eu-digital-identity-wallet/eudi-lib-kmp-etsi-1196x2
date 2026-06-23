import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kmmbridge.github)
}

kotlin {
    // PKIXBridge.xcframework produced by buildPKIXBridge below.
    val pkixBridgeXcframework = projectDir.resolve("cinterop/build/PKIXBridge.xcframework")

    fun pkixBridgeSlice(targetName: String): String =
        when (targetName) {
            "iosArm64" -> "ios-arm64"
            "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
            else -> error("Unknown iOS target: $targetName")
        }

    // Resolve the Swift toolchain's static-library directory so the Kotlin/Native linker can find
    // the Swift ABI compatibility shims that PKIXBridge's objects force-load.
    val swiftLibBase: String? =
        if (OperatingSystem.current().isMacOsX) {
            providers.exec { commandLine("xcode-select", "-p") }
                .standardOutput.asText.get().trim() +
                    "/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift"
        } else {
            null
        }

    fun swiftLibPlatform(targetName: String): String =
        when (targetName) {
            "iosArm64" -> "iphoneos"
            "iosX64", "iosSimulatorArm64" -> "iphonesimulator"
            else -> error("Unknown iOS target: $targetName")
        }

    val frameworkName = "EudiEtsi1196x2"

    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        val frameworkSearchPath = pkixBridgeXcframework.resolve(pkixBridgeSlice(target.name)).absolutePath

        target.compilations.getByName("main") {
            cinterops {
                create("PKIXBridge") {
                    definitionFile.set(project.file("cinterop/PKIXBridge.def"))
                    // -fmodules: PKIXBridge.framework exposes its @objc surface via module.modulemap.
                    compilerOpts("-F$frameworkSearchPath", "-fmodules")
                }
            }
        }

        target.binaries.framework {
            baseName = frameworkName
            isStatic = false
            export(projects.etsi1196x2Consultation)
            export(projects.etsi119602Consultation)
            export(projects.etsi119602DataModel)
        }
        target.binaries.all {
            linkerOpts("-framework", "PKIXBridge", "-F$frameworkSearchPath")
            if (swiftLibBase != null) {
                linkerOpts("-L$swiftLibBase/${swiftLibPlatform(target.name)}")
            }
        }
    }

    sourceSets {
        iosMain {
            dependencies {
                api(projects.etsi1196x2Consultation)
                api(projects.etsi119602Consultation)
                api(projects.etsi119602DataModel)
                // Darwin (NSURLSession) HTTP engine, linked into the umbrella framework so
                // HttpClient(Darwin) works at runtime on iOS.
                implementation(libs.ktor.client.darwin)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

kmmbridge {
    gitHubReleaseArtifacts()
}

// Build PKIXBridge.xcframework before cinterop runs. The script invokes xcrun/swiftc/lipo
// directly (no .xcodeproj wrapping) and produces ios-arm64 + ios-arm64_x86_64-simulator slices.
// Gated on macOS — non-Darwin CI hosts skip iOS targets entirely.
val buildPKIXBridge by tasks.registering(Exec::class) {
    val pkixBridgeDir = projectDir.resolve("cinterop")
    workingDir = pkixBridgeDir
    commandLine("./scripts/build-xcframework.sh")

    inputs.dir(pkixBridgeDir.resolve("Sources"))
    inputs.file(pkixBridgeDir.resolve("scripts/build-xcframework.sh"))
    inputs.file(pkixBridgeDir.resolve("Package.swift"))
    outputs.dir(pkixBridgeDir.resolve("build/PKIXBridge.xcframework"))

    onlyIf { OperatingSystem.current().isMacOsX }
}

tasks.withType<CInteropProcess>().configureEach {
    if (interopName == "PKIXBridge") {
        dependsOn(buildPKIXBridge)
        // Track the xcframework contents so a Swift-side change also invalidates the generated
        // bindings. Without this, cinterop stays UP-TO-DATE off its .def hash alone.
        inputs.dir(projectDir.resolve("cinterop/build/PKIXBridge.xcframework"))
            .withPropertyName("pkixBridgeXcframework")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
