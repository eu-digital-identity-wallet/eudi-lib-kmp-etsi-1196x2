import com.android.build.api.withAndroid
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
    google()
}

group = findProperty("group")!!.toString()

kotlin {
    explicitApiWarning()
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        optIn =
            listOf(
                "kotlin.io.encoding.ExperimentalEncodingApi",
                "kotlin.ExperimentalStdlibApi",
                "kotlin.time.ExperimentalTime",
                "kotlin.contracts.ExperimentalContracts",
                "kotlinx.cinterop.ExperimentalForeignApi",
            )
    }

    // JVM target
    jvm()

    // Android target
    android {
        namespace = "eu.europa.ec.eudi.etsi1196x2.consultation"
        compileSdk = findProperty("android.targetSdk")!!.toString().toInt()
        minSdk = findProperty("android.minSdk")!!.toString().toInt()

        // Set JVM target to 17 to match Java compatibility
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
        }
    }

    // iOS targets — cinterop into PKIXBridge.xcframework (produced by buildPKIXBridge below).
    // Slice paths match the xcframework layout: device = ios-arm64; both simulators share
    // the lipo'd ios-arm64_x86_64-simulator slice.
    val pkixBridgeXcframework = rootProject.file("ios/cinterop/build/PKIXBridge.xcframework")

    fun pkixBridgeSlice(targetName: String): String =
        when (targetName) {
            "iosArm64" -> "ios-arm64"
            "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
            else -> error("Unknown iOS target: $targetName")
        }

    // Resolve the Swift toolchain's static-library directory so the Kotlin/Native linker can find
    // the Swift ABI compatibility shims that PKIXBridge's objects force-load.
    val swiftLibBase: String? =
        if (org.gradle.internal.os.OperatingSystem
                .current()
                .isMacOsX
        ) {
            providers
                .exec { commandLine("xcode-select", "-p") }
                .standardOutput.asText
                .get()
                .trim() +
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

    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        val frameworkSearchPath = pkixBridgeXcframework.resolve(pkixBridgeSlice(target.name)).absolutePath
        target.compilations.getByName("main") {
            cinterops {
                create("PKIXBridge") {
                    definitionFile.set(rootProject.file("ios/cinterop/PKIXBridge.def"))
                    // -fmodules: PKIXBridge.framework exposes its @objc surface via module.modulemap,
                    // which requires clang module support.
                    compilerOpts("-F$frameworkSearchPath", "-fmodules")
                }
            }
        }
        target.binaries.all {
            linkerOpts("-framework", "PKIXBridge", "-F$frameworkSearchPath")
            if (swiftLibBase != null) {
                linkerOpts("-L$swiftLibBase/${swiftLibPlatform(target.name)}")
            }
        }
    }

    // Set up targets
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {

        // create a new group that depends on `common`
        common {
            // Define group name without `Main` as suffix
            group("jvmAndAndroid") {
                // Provide which targets would be part of this group
                withJvm()
                withAndroid()
            }
        }
    }

    // Configure source sets
    sourceSets {
        commonMain {
            dependencies {
                // Common dependencies
                api(libs.kotlinx.coroutines.core)
                implementation(libs.atomicfu)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        @Suppress("UNUSED")
        val jvmAndAndroidMain =
            getByName("jvmAndAndroidMain") {
                dependencies {
                    implementation(libs.bouncy.castle)
                    implementation(libs.slf4j.api)
                }
            }

        @Suppress("UNUSED")
        val jvmAndAndroidTest =
            getByName("jvmAndAndroidTest") {
                resources.srcDirs("src/commonTest/resources")
            }

        @Suppress("UNUSED")
        val iosMain =
            getByName("iosMain") {
            }
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                ),
            )
        trimTrailingWhitespace()
        licenseHeaderFile("../FileHeader.txt")
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

//
// Configuration of Dokka engine
//
dokka {
    // used as project name in the header
    moduleName = findProperty("POM_NAME")!!.toString()
    moduleVersion = project.version.toString()

    dokkaSourceSets.configureEach {
        documentedVisibilities = setOf(VisibilityModifier.Public, VisibilityModifier.Protected)

        val remoteSourceUrl =
            System.getenv()["GIT_REF_NAME"]?.let {
                URI.create("${findProperty("POM_SCM_URL")!!}/tree/$it/${project.layout.projectDirectory.asFile.name}/src")
            }
        remoteSourceUrl
            ?.let {
                sourceLink {
                    localDirectory = projectDir.resolve("src")
                    remoteUrl = it
                    remoteLineSuffix = "#L"
                }
            }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka(tasks.dokkaGeneratePublicationHtml),
            sourcesJar = true,
        ),
    )

    coordinates(
        groupId = group.toString(),
        artifactId = "etsi-1196x2-consultation",
        version = version.toString(),
    )

    pom {
        ciManagement {
            system = "github"
            url = "${findProperty("POM_SCM_URL")!!}/actions"
        }
    }
}

// SecTrust evaluation requires the trust daemon (trustd), which is only reliably available on a
// fully-booted simulator. Run simulator tests against an already-booted device instead.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set("booted")
}

tasks.withType<CInteropProcess>().configureEach {
    if (interopName == "PKIXBridge") {
        dependsOn(":etsi-1196x2-ios:buildPKIXBridge")
    }
}
