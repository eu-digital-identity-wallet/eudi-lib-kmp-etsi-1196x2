# DSS Consultation Module

This module provides abstractions and implementations for validating certificate chains against trust anchors that are
published in [ETSI TS 119 612 Trusted Lists](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/02.04.01_60/ts_119612v020401p.pdf).

Trusted Lists are fetched, parsed, and validated using [Digital Signature Service (DSS)](https://github.com/esig/dss).

## Key Components

### GetTrustedListsCertificateByLOTLSource

`GetTrustedListsCertificateByLOTLSource` is a low level entry point used to fetch certificates published in Trusted Lists.
The details of the Trusted List and those of the certificates to fetch are defined using [LOTLSource](https://github.com/esig/dss/blob/master/dss-tsl-validation/src/main/java/eu/europa/esig/dss/tsl/source/LOTLSource.java).
The fetched certificates are represented using [TrustedListsCertificateSource](https://github.com/esig/dss/blob/master/dss-spi/src/main/java/eu/europa/esig/dss/spi/tsl/TrustedListsCertificateSource.java).

Each `GetTrustedListsCertificateByLOTLSource` can act as a `GetTrustAnchors` instace for a specific `LOTLSource`, allowing bridging DSS with components of [Consultation](../consultation).

`GetTrustedListsCertificateByLOTLSource` provides the factory method `fromBlocking()` which can be used to wrap blocking code from DSS with Kotlin coroutines.

### DSSAdapter

`DSSAdapter` bridges DSS with `GetTrustedListsCertificateByLOTLSource`.

### usingLoTL

Factory methods for creating `IsChainTrusted`, and `IsChainTrustedForContext` instances using either `DSSAdapter`, or custom implementations.

## Usage

To use this library, you have to add the following dependency to your project:

```kotlin
dependencies {
    implementation("eu.europa.ec.eudi:etsi-1196x2-consultation-dss:$version")
}
```

> [!IMPORTANT]
> 
> DSS abstracts certain utility APIs, and provides two implementations:
> 
> * `dss-utils-apache-commons`: Implementation of dss-utils with Apache Commons libraries
> * `dss-utils-google-guava`: Implementation of dss-utils with Google Guava
> 
> Users of this library, must also include the DSS implementation of their choice.
> ```kotlin
> dependencies {
>     implementation("eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons:$dssVersion")
>     // OR
>     implementation("eu.europa.ec.joinup.sd-dss:dss-utils-google-guava:$dssVersion")
> }
> ```

## Examples

Usage examples can be found in:

* [IsChainTrustedUsingLoTLTest.kt](src/jvmAndAndroidTest/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/dss/IsChainTrustedUsingLoTLTest.kt)

## Platform Support

The library targets JVM and Android.
