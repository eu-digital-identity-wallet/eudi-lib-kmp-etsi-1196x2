# iOS consumer verification harness

This standalone Gradle project verifies that the **documented** recipe for consuming
this library on iOS actually works: it depends on `eu.europa.ec.eudi:etsi-119602-consultation`
exactly like a Maven consumer, and it links using **only** the `linkerOpts` snippet
published in [README.md#ios](../README.md#ios) and [docs/iOS-PKIXBridge.md](../docs/iOS-PKIXBridge.md).

It is intentionally **not** part of the main Gradle build (root `settings.gradle.kts`
does not include it).

## What CI does

The `verify-docs-ios-consumer` job in [`.github/workflows/verify-ios-consumer.yml`](../.github/workflows/verify-ios-consumer.yml)
runs on macOS and:

1. builds `PKIXBridge.xcframework` (`./gradlew :etsi-1196x2-ios:buildPKIXBridge`),
2. publishes the three consumed modules to **Maven Local** (as if they were a release),
3. links the consumer test binaries for `iosArm64`, `iosX64` and `iosSimulatorArm64`
   using the documented snippet.

If the docs stop matching reality — slice layout, PKIXBridge symbol set, Swift ABI-shim
paths, cinterop/publication naming — the link step fails.

## Running locally (macOS)

```bash
./gradlew \
  :etsi-1196x2-ios:buildPKIXBridge \
  :etsi-119602-data-model:publishToMavenLocal \
  :etsi-1196x2-consultation:publishToMavenLocal \
  :etsi-119602-consultation:publishToMavenLocal

./gradlew -p verification/ios-consumer \
  linkDebugTestIosSimulatorArm64 linkDebugTestIosX64 linkDebugTestIosArm64 \
  -PeudiVersion=$(grep '^version=' gradle.properties | cut -d= -f2) \
  -PpkixXcframework=$(pwd)/ios/cinterop/build/PKIXBridge.xcframework
```

## Notes

- The `linkerOpts` blocks in `ios-consumer/build.gradle.kts` must stay **byte-identical**
  to the documented snippet; that is exactly what this harness verifies.
- `pkixXcframework` must be an **absolute** path: the linker resolves `-F` relative to
  the consumer project directory, not the repository root.
- This checks **linking**. Running the tests on a booted simulator
  (`iosSimulatorArm64Test`) is a planned, stronger follow-up.