# iOS: linking `PKIXBridge` from Maven/Gradle

This document explains why iOS **Kotlin Multiplatform (Maven/Gradle)** consumers must
supply the internal `PKIXBridge` framework themselves, and gives the exact setup.
Swift-app consumers of the Swift Package Manager (SPM) artifact do **not** need any
of this — see the [root README](../README.md#ios) for the channel comparison.

---

## TL;DR

On iOS, the PKIX validation path of this library calls into a small Swift framework
named `PKIXBridge` (a wrapper around Apple's `Security.framework` / SecTrust). The
Swift API reaches Kotlin through Kotlin/Native **cinterop**.

- The **SPM** artifact (`EudiEtsi1196x2.xcframework`) already contains `PKIXBridge`,
  statically linked in. Nothing to do.
- The **Maven klibs** declare the `PKIXBridge` API but do **not** ship its
  implementation. You must build `PKIXBridge` once, on macOS, and link it into your
  iOS targets with `linkerOpts`.

If you don't, you eventually hit:

```
ld: warning: Could not find or use auto-linked framework 'PKIXBridge': framework 'PKIXBridge' not found
Undefined symbols for architecture arm64:
  "_OBJC_CLASS_$__TtC10PKIXBridge13PKIXValidator", referenced from: …
  "_OBJC_CLASS_$__TtC10PKIXBridge17PKIXConfiguration", referenced from: …
  "_OBJC_CLASS_$__TtC10PKIXBridge24PKIXCertificateInspector", referenced from: …
ld: symbol(s) not found for architecture arm64
```

---

## Why this exists

### The cinterop contract

Cinterop works like JNI with the native library not bundled:

- At build time, Kotlin/Native reads `PKIXBridge`'s Objective-C header and generates
  **Kotlin bindings** — the API surface (`PKIXValidator`, `PKIXConfiguration`,
  `PKIXCertificateInspector`, …) referencing the underlying Swift symbols.
- These bindings are compiled into the published klib. **The klib is a declaration +
  stub layer.** It does not contain the `libPKIXBridge` archive, and it does not carry
  the linker options that would locate it.
- At *final link time* (when your app or test executable is produced), the Kotlin/Native
  linker must resolve those symbols against the real `PKIXBridge` framework. If it is not
  provided, you get the undefined-symbol error above.

This is the normal, intended shape of cinterop — the same contract as `androidx.sqlite`
expecting consumers to link `-lsqlite3`. The library's own build wires PKIXBridge in
(`consultation/build.gradle.kts`); **that wiring is never propagated to consumers.**

### Why it is easy to be surprised

- **It's transitive.** The README entry point is `eu.europa.ec.eudi:etsi-119602-consultation`.
  `PKIXBridge` is never named in your build files — the cinterop klib arrives through the
  transitive dependency `etsi-1196x2-consultation`. The failure therefore reads like a
  packaging bug rather than a missing step.
- **It's invisible until exercised.** Kotlin/Native dead-strips unreferenced code. Merely
  *adding* the dependency links fine; the first test that reaches a trust decision is what
  surfaces the missing framework.

---

## The SPM/Maven asymmetry

| Channel | Artifact | `PKIXBridge` inside? |
|---|---|---|
| Swift Package Manager | `EudiEtsi1196x2.xcframework` (release asset / `v<version>-SPM` tag) | ✅ statically linked in (self-contained) |
| Maven Central | `...-iosarm64/*.klib`, `...-iossimulatorarm64/*.klib`, `...-iosx64/*.klib` | ❌ declarations/stubs only |

You can verify this yourself against the release artifact:

```bash
# SPM artifact — PKIXBridge symbols are defined (T)
$ nm -gU ios-arm64/EudiEtsi1196x2.framework/EudiEtsi1196x2 | grep -c PKIXBridge
611
$ nm -gU ios-arm64/EudiEtsi1196x2.framework/EudiEtsi1196x2 | grep PKIXValidator
… T _$s10PKIXBridge13PKIXValidatorC…

# Maven klib — declarations only; the archive holds no implementation
$ unzip -l etsi-1196x2-consultation-iosArm64Cinterop-PKIXBridgeMain-<version>.klib
```

The umbrella framework is a static archive (`file` reports "current ar archive"), which
is why its binary carries the merged `PKIXBridge` objects.

---

## Obtaining `PKIXBridge.xcframework`

All of the following require macOS with Xcode. Pick one:

1. **From this repository (recommended):** the Gradle task builds the framework locally.

   ```bash
   ./gradlew :etsi-1196x2-ios:buildPKIXBridge
   # Output: ios/cinterop/build/PKIXBridge.xcframework
   ```

2. **The build script directly:**

   ```bash
   ios/cinterop/scripts/build-xcframework.sh
   ```

3. **The standalone SwiftPM package** in this repo (`ios/cinterop/Package.swift`)
   builds the same sources; copy `ios/cinterop/Sources/PKIXBridge` into your project and
   add it as a local Swift package, or build with `swift build`.

---

## Linking `PKIXBridge`

### Kotlin/Native test binaries (Gradle)

The most common failure point is a Kotlin/Native test executable (`iosSimulatorArm64Test`,
`iosX64Test`, `iosArm64Test`). A test binary has **no Xcode target** to borrow a framework
from — Gradle must pass the linker options explicitly.

```kotlin
kotlin {
    val pkix = "<path-to>/PKIXBridge.xcframework"
    // Output of `xcode-select -p`, followed by the toolchain's Swift shim directory:
    val swiftShims = "<xcode-select -p>/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift"

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
```

The `-L${swiftShims}/…` lines carry the Swift ABI-compatibility shims that `PKIXBridge`'s
Swift objects force-load; they mirror the library's own build
(`consultation/build.gradle.kts`).

### App targets (Xcode)

An app whose Kotlin framework is embedded from these klibs must resolve the `PKIXBridge`
symbols in the final app link. Two routes:

- **Link the framework:** add `-framework PKIXBridge -F<path-to-slice>` to the app target's
  *Other Linker Flags* (or the `<path>` to the whole xcframework and use the matching slice
  from the table below). The Swift runtime is handled by Xcode automatically for any target
  containing Swift; a pure Objective-C target additionally needs *Always Embed Swift
  Standard Libraries*.
- **Add the sources as a local Swift package:** copy `ios/cinterop/Sources/PKIXBridge`
  into your project (or build it from this repo's `ios/cinterop/Package.swift`) and add it as
  a local package dependency of the app target.

---

## XCFramework slice table

| Kotlin target | Slice inside `PKIXBridge.xcframework` | `-L` Swift shim platform |
|---|---|---|
| `iosArm64` (device) | `ios-arm64` | `iphoneos` |
| `iosX64` (Intel simulator) | `ios-arm64_x86_64-simulator` (fat: arm64 + x86_64) | `iphonesimulator` |
| `iosSimulatorArm64` (Apple Silicon simulator) | `ios-arm64_x86_64-simulator` (fat: arm64 + x86_64) | `iphonesimulator` |

`-F` points at the **directory containing** `PKIXBridge.framework` — i.e. the slice path,
not the framework path itself.

---

## Troubleshooting

- **`Undefined symbols for architecture arm64: "_OBJC_CLASS_$__TtC10PKIXBridge…"`** —
  `PKIXBridge` is not on the link line. Apply the recipe above for the target you are
  building (device vs simulator slice).
- **`This declaration needs opt-in ... ExperimentalForeignApi`** — the cinterop
  (`PKIXValidator`, `PKIXConfiguration`, …) API is experimental. Mark the using file or
  class with `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` (or add
  `kotlinx.cinterop.ExperimentalForeignApi` to the target's `compilerOptions.optIn`).
- **The error appears only in one task** (e.g. `iosSimulatorArm64Test` but not the debug
  framework) — expected. Configure `linkerOpts` per binary type/target as shown.
- **The build succeeded until I added a test that calls the trust API** — expected.
  Kotlin/Native drops unreferenced code, so the missing framework only surfaces once the
  PKIX path is actually referenced.
- **You only declared `eu.europa.ec.eudi:etsi-119602-consultation`** — expected. The
  cinterop klib is a transitive dependency; nothing named `PKIXBridge` appears in your
  build files.

---

## What NOT to do

- **Do not "fix" this by adding paths into `ios/cinterop/PKIXBridge.def`** (e.g.
  `linkerOpts` with an absolute `-F`). Options declared in the `.def` are baked into the
  published klib manifest and applied to every consumer's link — build-machine paths would
  break the library for everyone.
- **Do not expect Maven to link automatically.** Kotlin/Native currently has no supported
  mechanism for a published klib to bundle a native binary and link it transparently; the
  documented `linkerOpts` recipe is the supported integration.

---

## Related

- [#176](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/issues/176) —
  the tracking issue for making the `PKIXBridge` requirement documented and consumable.
- [Root README, iOS section](../README.md#ios) — channel comparison for consumers.
- [`verification/ios-consumer`](../verification/ios-consumer) — the CI harness that
  verifies this documented recipe actually links (<code>verify-docs-ios-consumer</code>
  job in `.github/workflows/verify-ios-consumer.yml`); keep the `linkerOpts` blocks in
  sync with it.
- `ios/cinterop/Package.swift`, `ios/cinterop/scripts/build-xcframework.sh`,
  `consultation/build.gradle.kts` — the library's own build wiring this document mirrors.