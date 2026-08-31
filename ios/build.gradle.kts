import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kmmbridge.github)
}

// Slice paths match the xcframework layout: device = ios-arm64; both simulators share
// the lipo'd ios-arm64_x86_64-simulator slice.
fun pkixBridgeSlice(targetName: String): String =
    when (targetName) {
        "iosArm64" -> "ios-arm64"
        "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
        else -> error("Unknown iOS target: $targetName")
    }

// Architecture to pull out of the (possibly fat) PKIXBridge slice when merging it into a
// static framework archive.
fun pkixBridgeArch(targetName: String): String =
    when (targetName) {
        "iosArm64", "iosSimulatorArm64" -> "arm64"
        "iosX64" -> "x86_64"
        else -> error("Unknown iOS target: $targetName")
    }

kotlin {
    // PKIXBridge.xcframework produced by buildPKIXBridge below.
    val pkixBridgeXcframework = projectDir.resolve("cinterop/build/PKIXBridge.xcframework")

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
        target.binaries.framework {
            baseName = frameworkName
            // Static framework: the Kotlin/Native runtime and every exported module are archived
            // into the framework binary, so consumers link it at build time instead of embedding
            // and signing a dynamic framework. This also avoids the duplicate-runtime and
            // dyld-load-order problems that arise when several Kotlin frameworks ship in one app.
            isStatic = true
            export(projects.etsi1196x2Consultation)
            export(projects.etsi119602Consultation)
            export(projects.etsi119602DataModel)
        }
        target.binaries.all {
            linkerOpts("-framework", "PKIXBridge", "-F$frameworkSearchPath")
            // Carry the iOS system Swift runtime location as an rpath so dyld can resolve
            linkerOpts("-rpath", "/usr/lib/swift")
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
    spm(swiftToolVersion = "5.9")
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

// A static framework is an `ar` archive, not a link product: the Kotlin/Native compiler leaves
// PKIXBridge's Objective-C classes as undefined symbols rather than pulling its objects in the way
// the linker does when it produces a dynamic framework. Merge the PKIXBridge slice into the archive
// so the framework we publish stays self-contained and consumers do not have to link PKIXBridge —
// an implementation detail of this library — themselves.
tasks.withType<KotlinNativeLink>().configureEach {
    val framework = binary as? Framework ?: return@configureEach
    if (!framework.isStatic) return@configureEach

    val targetName = framework.target.name
    val pkixBridgeBinary = projectDir.resolve(
        "cinterop/build/PKIXBridge.xcframework/${pkixBridgeSlice(targetName)}/PKIXBridge.framework/PKIXBridge",
    )
    val frameworkBinary = framework.outputFile.resolve(framework.baseName)
    val arch = pkixBridgeArch(targetName)

    inputs.file(pkixBridgeBinary)
        .withPropertyName("pkixBridgeSliceBinary")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        val merged = frameworkBinary.resolveSibling("${frameworkBinary.name}-merged.a")
        val exit = ProcessBuilder(
            "xcrun", "libtool", "-static",
            // The simulator slice is fat (arm64 + x86_64); keep only this target's architecture.
            "-arch_only", arch,
            "-o", merged.absolutePath,
            frameworkBinary.absolutePath,
            pkixBridgeBinary.absolutePath,
        ).inheritIO().start().waitFor()
        check(exit == 0) { "libtool failed with exit code $exit while merging PKIXBridge into $frameworkBinary" }
        // Atomic rename over the existing archive: `File.renameTo` reports failure only through its
        // return value and its overwrite behaviour varies by filesystem. Source and target are
        // siblings, so the move stays within one filesystem and ATOMIC_MOVE is always supported.
        Files.move(
            merged.toPath(),
            frameworkBinary.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
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
