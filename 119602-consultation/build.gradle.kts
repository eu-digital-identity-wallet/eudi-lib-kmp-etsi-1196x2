import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.atomicfu)
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    explicitApiWarning()
    jvmToolchain(libs.versions.java.get().toInt())

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        optIn =
            listOf(
                "kotlinx.serialization.ExperimentalSerializationApi",
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
    androidTarget {
        // Set JVM target to 17 to match Java compatibility
        // Using direct property access instead of deprecated kotlinOptions
        JvmTarget.fromTarget(libs.versions.java.get())
            .let { javaTarget ->
                compilations.all {
                    compileTaskProvider.configure {
                        compilerOptions.jvmTarget.set(javaTarget)
                    }
                }
            }
    }

    // iOS targets. This module links final iOS binaries (e.g. test executables) that transitively
    // use the consultation module's PKIXBridge cinterop, so it must repeat the framework + Swift
    // compatibility-shim linker options (cinterop linker options do not propagate transitively).
    val pkixBridgeXcframework = rootProject.file("PKIXBridge/build/PKIXBridge.xcframework")

    fun pkixBridgeSlice(targetName: String): String =
        when (targetName) {
            "iosArm64" -> "ios-arm64"
            "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
            else -> error("Unknown iOS target: $targetName")
        }

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

    // Single umbrella framework for iOS consumers (SwiftPM). It re-exports the consultation
    // and data-model APIs so Swift sees one module ("EudiEtsi1196x2") with the full surface,
    // and statically links the PKIXBridge cinterop so the framework is self-contained.
    val umbrella = XCFramework("EudiEtsi1196x2")

    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        val frameworkSearchPath = pkixBridgeXcframework.resolve(pkixBridgeSlice(target.name)).absolutePath
        target.binaries.framework {
            baseName = "EudiEtsi1196x2"
            isStatic = false
            export(projects.etsi1196x2Consultation)
            export(projects.etsi119602DataModel)
            umbrella.add(this)
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
                withAndroidTarget()
            }
        }
    }

    // Configure source sets
    sourceSets {
        commonMain {
            dependencies {
                // Common dependencies
                api(projects.etsi119602DataModel)
                api(projects.etsi1196x2Consultation)
                implementation(libs.atomicfu)
                implementation(libs.kotlinx.io.core)
                api(libs.ktor.client.core)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }

        @Suppress("UNUSED")
        val jvmAndAndroidMain by getting {
            dependencies {
                implementation(libs.bouncy.castle)
            }
        }

        @Suppress("UNUSED")
        val iosMain by getting {
            dependencies {
                // Darwin (NSURLSession) HTTP engine, linked into the umbrella framework so
                // HttpClient(Darwin) works at runtime on iOS.
                implementation(libs.ktor.client.darwin)
            }
        }

        @Suppress("UNUSED")
        val jvmAndAndroidTest by getting {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.eudi.lib.jvm.sdjwt)
            }
        }

        androidUnitTest {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

// SecTrust evaluation (reachable via the iOS PKIX validator) needs the trust daemon (trustd),
// which is only available on a fully-booted simulator. Run simulator tests against an
// already-booted device rather than Kotlin's default ephemeral standalone simulator.
// CI must boot a simulator first (`xcrun simctl boot <device>`).
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set("booted")
}

// Android configuration
android {
    namespace = "eu.europa.ec.eudi.etsi119602.consultation"
    group = properties["group"].toString()
    compileSdk = properties["android.targetSdk"].toString().toInt()

    defaultConfig {
        minSdk = properties["android.minSdk"].toString().toInt()
    }

    sourceSets {
        getByName("test") {
            resources.srcDirs("src/jvmAndAndroidTest/resources")
        }
    }

    compileOptions {
        JavaVersion.toVersion(libs.versions.java.get().toInt())
            .let { javaVersion ->
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
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
    moduleName = properties["POM_NAME"].toString()
    moduleVersion = project.version.toString()

    dokkaSourceSets.configureEach {
        documentedVisibilities = setOf(VisibilityModifier.Public, VisibilityModifier.Protected)

        val remoteSourceUrl =
            System.getenv()["GIT_REF_NAME"]?.let {
                URI.create("${properties["POM_SCM_URL"]}/tree/$it/${project.layout.projectDirectory.asFile.name}/src")
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
        artifactId = "etsi-119602-consultation",
        version = version.toString(),
    )

    pom {
        ciManagement {
            system = "github"
            url = "${project.properties["POM_SCM_URL"]}/actions"
        }
    }
}

dependencyCheck {
    skip = true
}
