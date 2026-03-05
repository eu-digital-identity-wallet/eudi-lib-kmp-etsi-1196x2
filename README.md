# EUDI ETSI 119 6x2 Consultation Library

:heavy_exclamation_mark: **Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

---

## Quick Links

- **[119602-consultation Module](./119602-consultation/README.md)** - ETSI TS 119 602 LoTE-based validation
- **[119602-data-model Module](./119602-data-model/README.md)** - ETSI TS 119 602 LoTE JSON data model
- **[Consultation Module](./consultation/README.md)** - Core abstractions for certificate chain validation
- **[Consultation-DSS Module](./consultation-dss/README.md)** - ETSI TS 119 612 Trusted Lists support via DSS

---

## Overview

The **EUDI ETSI 119 6x2 Consultation Library** is a Kotlin Multiplatform (KMP) project designed for the European Digital Identity (EUDI) Wallet ecosystem. It provides an extensible and secure framework for Certificate Chain Validation against Trusted Lists, enabling the verification of credentials and attestation objects within the European Union's identity framework.

## Problem Solved

In the EUDI ecosystem, verifying the trustworthiness of credentials (like PID or EAA)
and attestations (like Wallet Instance Attestation) requires validating certificate chains
against trust anchors from **Trusted Lists**.

The EUDI Wallet ecosystem uses two complementary Trusted List specifications:
- **ETSI TS 119 602** (Lists of Trusted Entities - LoTE): JSON/XML data model for PID Providers, Wallet Providers, WRPAC/WRPRC providers, and public sector EAA providers
- **ETSI TS 119 612** (Trusted Lists): XML format for trust service providers, with LOTL (List Of Trusted Lists) aggregation

This library provides **unified abstractions for both Trusted List specifications**, enabling consistent certificate chain validation regardless of the trust source format.

This library simplifies this process by:
- **Unified abstractions**: Common interfaces (`GetTrustAnchors`, `IsChainTrustedForContext`) work with both ETSI TS 119 602 LoTE and ETSI TS 119 612 Trusted Lists
- **Separation of concerns**: Trust discovery (fetching anchors from Trusted Lists) separated from validation logic (PKIX or direct trust)
- **Composable architecture**: Combine validators for different verification contexts using `ComposeChainTrust`
- **Platform flexibility**: KMP support for LoTE-based validation, JVM/Android for DSS-based Trusted Lists

## Target Audience

This library is designed for:
- **Wallets**: To verify the trustworthiness of credentials they receive and to handle attestation objects.
- **Verifiers (Relying Parties)**: To verify credentials presented by a Wallet against the required trust anchors.
- **Issuers**: To verify Wallet attestations during the credential issuance process.

## Installation

### Gradle Setup

The typical setup for EUDI Wallet certificate validation against LoTE (Lists of Trusted Entities):

```kotlin
// ETSI TS 119 602 LoTE-based validation (KMP)
// Includes transitive dependencies:
//   - etsi-119602-data-model (LoTE JSON data types)
//   - etsi-1196x2-consultation (core validation abstractions)
implementation("eu.europa.ec.eudi:etsi-119602-consultation:$version")
```

For ETSI TS 119 612 Trusted Lists support (JVM/Android only):

```kotlin
// ETSI TS 119 612 Trusted Lists via DSS (JVM/Android)
// Includes transitive dependency:
//   - etsi-1196x2-consultation (core validation abstractions)
implementation("eu.europa.ec.eudi:etsi-1196x2-consultation-dss:$version")

// Additional DSS dependencies required
implementation("eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons:$dssVersion")
// OR
implementation("eu.europa.ec.joinup.sd-dss:dss-utils-google-guava:$dssVersion")
implementation("eu.europa.ec.joinup.sd-dss:dss-policy-jaxb:$dssVersion")
```

> [!NOTE]
> Replace `$version` with the latest release version from the [releases page](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases).
> All modules share the same version number.

---

## Project Structure

The library is divided into four modules:

### 1. [119602-consultation](./119602-consultation/README.md)

Implements certificate chain validation against ETSI TS 119 602 Lists of Trusted Entities (LoTE).
- **Features**: LoTE document fetching, trust anchor extraction, profile-specific certificate constraints for PID/Wallet/WRPAC/WRPRC providers.
- **Platform Support**: KMP (common + JVM/Android).

### 2. [119602-data-model](./119602-data-model/README.md)

Data model implementation for ETSI TS 119 602 Lists of Trusted Entities (LoTE).
- **Features**: Kotlinx serialization, JSON schema compliance, validation for LoTE documents.
- **Platform Support**: KMP (common + JVM/Android).

#### Core Data Types

- `ListOfTrustedEntities`: Root LoTE document structure.
- `ListAndSchemeInformation`: LoTE metadata and scheme information.
- `TrustedEntity`: Trusted entity (e.g., PID Provider, Wallet Provider).
- `TrustedEntityService`: Service provided by a trusted entity.
- `ServiceDigitalIdentity`: Digital identity including X.509 certificates.


### 3. [Consultation](./consultation/README.md)

The core module providing the **unified abstractions** for Trusted List-based certificate validation.
- **Features**: Functional architecture for trust discovery, attestation classification, and certificate chain validation.
- **Platform Support**: KMP (common + JVM/Android).

#### Key Abstractions

- `VerificationContext`: Represents specific EUDI use cases (e.g., PID issuance).
- `GetTrustAnchors`: A functional interface for retrieving anchors from Trusted Lists.
- `IsChainTrustedForContext`: Combines trust anchors and validation logic for a set of supported contexts.
- `ComposeChainTrust`: Combines validators for **different** verification contexts (e.g., PID + PubEAA).
- `IsChainTrustedForEUDIW`: The orchestrator that resolves trust anchors and triggers validation.


### 4. [Consultation-DSS](./consultation-dss/README.md)

An extension module that leverages the [Digital Signature Service (DSS)](https://github.com/esig/dss) to support ETSI TS 119 612 Trusted Lists.
- **Features**: Automated LOTL/TL synchronization, multi-tier caching (In-Memory, File System), and DSS-based validation.
- **Platform Support**: JVM and Android only.


## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

---

## See Also

- **[EUDI Wallet Reference Implementation](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)** - Project description and overview
- **[ETSI TS 119 612 - Trusted Lists](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/)** - ETSI specification for Trusted Lists
- **[ETSI TS 119 602 - Lists of Trusted Entities (LoTE)](https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/)** - ETSI specification for LoTE
- **[Digital Signature Service (DSS)](https://github.com/esig/dss)** - DSS library for certificate validation

---

## License

Copyright (c) 2026 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.