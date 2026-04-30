# Consultation-DSS on Android

## Problem

The `consultation-dss` module wraps [DSS (Digital Signature Services)](https://github.com/esig/dss) 6.4, a Java library for XML signature validation and trusted list parsing. DSS was designed for the JVM and uses several Java APIs that are either missing or behave differently on Android, causing **four distinct runtime crashes**:

### 0. `java.awt.Image` — `NoClassDefFoundError`

JAXB RI (Glassfish) has a hard static reference to `java.awt.Image` in its internal classes. This class does not exist on Android. When the JVM classloader encounters this reference during class initialization, it throws `NoClassDefFoundError` before any code runs.

DSS 6.4 pulls in `org.glassfish.jaxb:jaxb-runtime:3.0.2` as a transitive dependency, which has this hard reference.

```
java.lang.NoClassDefFoundError: Failed resolution of: Ljava/awt/Image;
  at org.glassfish.jaxb.core.v2.model.nav.Navigator.isSubClassOf(...)
  at org.glassfish.jaxb.runtime.v2.runtime.XMLSerializer.<clinit>(...)
```

JAXB RI 4.0.7 (eclipse-ee4j/jaxb-ri#1869) fixed this by replacing the hard static reference with a runtime availability check for `java.awt.*`, making it safe to load on Android.

### 1. `XMLInputFactory.newFactory()` — `NoSuchMethodError`

DSS's `AbstractJaxbFacade.avoidXXE()` calls `XMLInputFactory.newFactory()` to create a StAX parser for JAXB unmarshalling. This method was added in **Java 9** as a replacement for the deprecated `newInstance()`. Android's `javax.xml.stream.XMLInputFactory` only has `newInstance()`.

```
java.lang.NoSuchMethodError: No static method newFactory()Ljavax/xml/stream/XMLInputFactory;
  at eu.europa.esig.dss.jaxb.common.AbstractJaxbFacade.avoidXXE(AbstractJaxbFacade.java:382)
  at eu.europa.esig.dss.jaxb.common.AbstractJaxbFacade.unmarshall(...)
  at eu.europa.esig.dss.tsl.parsing.AbstractParsingTask.getJAXBObject(...)
```

This crashes the **entire LOTL/TL parsing pipeline** — every call to `GetTrustAnchorsFromLoTL` fails before any XML is read.

### 2. JAXB RI `FEATURE_SECURE_PROCESSING` — `SAXNotRecognizedException`

JAXB RI (Glassfish) enables the `http://javax.xml.XMLConstants/feature/secure-processing` feature on its SAX parser by default. Android's built-in parser (`org.apache.harmony.xml`) does not recognize this feature.

```
org.xml.sax.SAXNotRecognizedException: http://javax.xml.XMLConstants/feature/secure-processing
  at org.apache.harmony.xml.parsers.SAXParserFactoryImpl.setFeature(...)
  at org.glassfish.jaxb.core.v2.util.XmlFactory.createParserFactory(XmlFactory.java:107)
```

Fix 3 (Xerces) resolves this by replacing Android's SAX parser with Xerces' `SAXParserFactoryImpl` via JAXP service discovery.

### 3. `SchemaFactoryBuilder.setFeature()` — `SecurityConfigurationException`

DSS's `SchemaFactoryBuilder` enables secure-processing on `SchemaFactory` for XSD validation (used during TL structure conformity checks). Unlike `DocumentBuilderFactoryBuilder` which catches exceptions and logs warnings, `SchemaFactoryBuilder` **throws** on any failure.

```
eu.europa.esig.dss.xml.common.exception.SecurityConfigurationException
  at eu.europa.esig.dss.xml.common.SchemaFactoryBuilder.setSecurityFeature(...)
```

Android's built-in `SchemaFactory` throws when setting `FEATURE_SECURE_PROCESSING`.

## Solution

The module applies three independent fixes, each targeting one failure path:

> **Publishing Status**: The Android AAR (containing the patched JAR) is not yet published to Maven Central. Only the JVM variant is published. Android consumers must manually apply Fix 2 and Fix 3 in their own builds until Android publishing is enabled.

### Fix 1: JAXB RI 4.0.7 Upgrade

DSS 6.4 pulls in `org.glassfish.jaxb:jaxb-runtime:3.0.2` which has a hard static reference to `java.awt.Image`, causing `NoClassDefFoundError` on Android. The Android dependency block forces `jaxb-runtime` and `jaxb-core` to version `4.0.7`, which replaced the hard reference with a runtime availability check for `java.awt.*` classes ([eclipse-ee4j/jaxb-ri#1869](https://github.com/eclipse-ee4j/jaxb-ri/issues/1869)).

```kotlin
androidMain {
    dependencies {
        implementation(libs.jaxb.runtime)  // 4.0.7
        implementation(libs.jaxb.core)     // 4.0.7
    }
}
```

This fix must be applied first — without it, the app crashes during class loading before any of the other fixes can help.

### Fix 2: ASM Bytecode Patching (`patchDssJaxbCommon` task)

A Gradle task patches the `dss-jaxb-common` JAR at build time using the OW2 ASM library. It replaces the `XMLInputFactory.newFactory()` call in `AbstractJaxbFacade.avoidXXE()` with `XMLInputFactory.newInstance()`, which exists on all Android API levels.

The original `dss-jaxb-common` is excluded from Android configurations and the patched JAR is substituted via a file dependency. The JVM target continues to use the original, unmodified JAR from Maven Central.

> **Note for consumers**: The Android AAR (which bundles the patched JAR) is not yet published to Maven Central. When publishing is enabled, the patched JAR will be included transitively in the Android AAR. Until then, consuming apps that need Android support must apply the `patchDssJaxbCommon` task themselves (copy from `consultation-dss/build.gradle.kts` lines 262-300).

### Fix 3: Xerces SAX/DOM Parser

The `xerces:xercesImpl:2.12.2` library is added as an Android-only dependency. Xerces registers standard JAXP service providers via `META-INF/services/`, replacing Android's built-in XML parsers:

1. **`javax.xml.parsers.SAXParserFactory`** → `org.apache.xerces.jaxp.SAXParserFactoryImpl` — Supports `FEATURE_SECURE_PROCESSING`, preventing JAXB RI's `SAXNotRecognizedException`.
2. **`javax.xml.validation.SchemaFactory`** → `org.apache.xerces.jaxp.validation.XMLSchemaFactory` — Supports `FEATURE_SECURE_PROCESSING`, preventing DSS `SchemaFactoryBuilder`'s `SecurityConfigurationException` during TL structure validation.
3. **`javax.xml.parsers.DocumentBuilderFactory`** → `org.apache.xerces.jaxp.DocumentBuilderFactoryImpl` — Provides a compliant DOM parser.

> **Known warnings**: Xerces doesn't implement every security feature DSS attempts to set. You will see `WARN` logs like `SECURITY : unable to set feature 'http://xml.org/sax/features/external-general-entities'` and `unable to set attribute 'accessExternalDTD'`. These are **non-critical** — DSS catches the exceptions internally and continues parsing. The critical crashes (`NoSuchMethodError`, `NoClassDefFoundError`, uncaught `SecurityConfigurationException`) are eliminated.

## Architecture Decision: Why Not Desugaring?

`coreLibraryDesugaring` was evaluated but rejected because:
- It **cannot rewrite static method calls** in pre-compiled library bytecode
- It only applies to project source code, not transitive dependencies
- The `enableCoreLibraryDesugaringForLibs` flag was attempted but proved insufficient for this case

---

## Integration Guide for Android Developers

### Requirements

- **minSdk 26** (Android 8.0) or higher
- The module is distributed as a KMP artifact with `androidTarget`

### Step 1: Add the Dependency

> **⚠️ Important**: The Android AAR with the patched JAR is not yet published to Maven Central. Currently only the `jvm` variant is published, which uses the unpatched `dss-jaxb-common`. Android consumers must apply the bytecode patch manually (see below).

Add the `consultation-dss` module to your app's dependencies:

```kotlin
dependencies {
    implementation("eu.europa.ec.eudi:etsi-1196x2-consultation-dss:<version>")
}
```

### Step 2: Force JAXB RI 4.0.7 (Required for Android)

DSS 6.4 pulls in `jaxb-runtime:3.0.2` which has a hard reference to `java.awt.Image`, causing `NoClassDefFoundError` on Android. Override the version to `4.0.7` in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.7")
    implementation("org.glassfish.jaxb:jaxb-core:4.0.7")
}
```

Or use a version catalog entry:

```kotlin
// gradle/libs.versions.toml
[versions]
jaxb = "4.0.7"

[libraries]
jaxb-runtime = { module = "org.glassfish.jaxb:jaxb-runtime", version.ref = "jaxb" }
jaxb-core = { module = "org.glassfish.jaxb:jaxb-core", version.ref = "jaxb" }
```

This fix must be applied first — without it, the app crashes during class loading before any other fix can help.

### Step 3: Apply the Bytecode Patch (Required for Android)

Since the Android AAR isn't published yet, you need to patch `dss-jaxb-common` in your own build. Add this to your app's `build.gradle.kts`:

```kotlin
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm:9.8") }
}

val patchDssJaxbCommon by tasks.registering {
    val dssVersion = "6.4" // Match the DSS version used by consultation-dss
    val inputJarProvider =
        provider {
            val cfg = configurations.detachedConfiguration(
                dependencies.create("eu.europa.ec.joinup.sd-dss:dss-jaxb-common:$dssVersion")
            )
            cfg.resolve().single { it.name.startsWith("dss-jaxb-common") }
        }
    val outputJar = layout.buildDirectory.file("libs/dss-jaxb-common-$dssVersion-android.jar")
    outputs.file(outputJar)
    inputs.file(inputJarProvider)

    doLast {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()
        val inputJar = inputJarProvider.get()
        val classToPatch = "eu/europa/esig/dss/jaxb/common/AbstractJaxbFacade.class"
        JarInputStream(inputJar.inputStream()).use { jis ->
            JarOutputStream(out.outputStream()).use { jos ->
                var entry = jis.nextJarEntry
                while (entry != null) {
                    jos.putNextEntry(JarEntry(entry.name))
                    if (entry.name == classToPatch) {
                        val bytes: ByteArray = jis.readAllBytes()
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(0)
                        reader.accept(
                            object : ClassVisitor(Opcodes.ASM9, writer) {
                                override fun visitMethod(
                                    access: Int, name: String?, desc: String?,
                                    signature: String?, exceptions: Array<out String>?,
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, desc, signature, exceptions)
                                    return if (name == "avoidXXE") {
                                        object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitMethodInsn(
                                                opcode: Int, owner: String?, methodName: String?,
                                                methodDesc: String?, isInterface: Boolean,
                                            ) {
                                                val newName = if (owner == "javax/xml/stream/XMLInputFactory" &&
                                                    methodName == "newFactory" &&
                                                    methodDesc == "()Ljavax/xml/stream/XMLInputFactory;"
                                                ) "newInstance" else methodName
                                                super.visitMethodInsn(opcode, owner, newName, methodDesc, isInterface)
                                            }
                                        }
                                    } else mv
                                }
                            },
                            0,
                        )
                        jos.write(writer.toByteArray())
                    } else {
                        jis.copyTo(jos)
                    }
                    jos.closeEntry()
                    entry = jis.nextJarEntry
                }
            }
        }
    }
}

// Replace original dss-jaxb-common with patched version for Android
configurations.matching { it.name.contains("Android") && it.name.contains("Classpath") }.configureEach {
    exclude(group = "eu.europa.ec.joinup.sd-dss", module = "dss-jaxb-common")
}

dependencies {
    "implementation"(files(patchDssJaxbCommon.map { it.outputs.files.singleFile }))
}
```

### Step 4: Add Xerces Dependency

```kotlin
dependencies {
    implementation("xerces:xercesImpl:2.12.2") {
        exclude(group = "xml-apis")
        exclude(group = "org.apache.xmlbeans")
        exclude(group = "net.sf.saxon")
    }
}
```

### Step 5: No Further Configuration Needed

No system properties or custom code are required at runtime. `GetTrustAnchorsFromLoTL` and other high-level APIs work out of the box. You will see `WARN` logs from DSS about unsupported security features — these are expected and non-critical (see Fix 3).

### Usage Example

```kotlin
val dssOptions = DssOptions.usingFileCacheDataLoader(
    fileCacheExpiration = 24.hours,
    cacheDirectory = cacheDir,
    httpLoader = NativeHTTPDataLoader(),
)

val lotlSource = LOTLSource().apply {
    url = "https://trustedlist.serviceproviders.eudiw.dev/LOTL/01.xml"
    trustAnchorValidityPredicate = GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate()
    tlVersions = listOf(6)
    trustServicePredicate = Predicate { tsp ->
        tsp.serviceInformation.serviceTypeIdentifier == "http://uri.etsi.org/Svc/Svctype/Provider/PID"
    }
    isPivotSupport = true
}

val getTrustAnchors = GetTrustAnchorsFromLoTL(dssOptions)

// Fetch trust anchors from LOTL
val trustAnchors = getTrustAnchors(lotlSource)

// Inspect validation summary for debugging
val summary = getTrustAnchors.lastValidationSummary
```

### Debugging Empty Trust Anchors

If trust anchors come back empty, check `GetTrustAnchorsFromLoTL.lastValidationSummary`:

```kotlin
val summary = getTrustAnchors.lastValidationSummary
summary?.let {
    // Check LOTL parsing results
    it.lotlInfos.forEach { lotl ->
        log.d("LOTL: ${lotl.url}, Territory: ${lotl.parsingCacheInfo?.territory}")
        log.d("  TL pointers: ${lotl.parsingCacheInfo?.tlOtherPointers?.size}")
        log.d("  Certificates: ${lotl.parsingCacheInfo?.certNumber}")
    }
    // Check TL parsing results
    it.otherTLInfos.forEach { tl ->
        log.d("TL: ${tl.url}, Territory: ${tl.parsingCacheInfo?.territory}")
        log.d("  TSPs: ${tl.parsingCacheInfo?.tspNumber}")
    }
}
```

Common causes:
- **Territory filtering**: EUDI LOTL uses territory code `UT` (not standard EU country codes). Ensure your `GrantedOrRecognizedAtNationalLevelTrustAnchorPeriodPredicate` or custom predicate matches.
- **Network**: The LOTL URL must be reachable from the device.
- **XSD validation failures**: Check logs for `SecurityConfigurationException` or parsing errors.
