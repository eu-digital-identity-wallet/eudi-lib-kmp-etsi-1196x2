# EUDI ETSI 119 6x2 Consultation Library

:heavy_exclamation_mark: **Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Overview

The **EUDI ETSI 119 6x2 Consultation Library** is a Kotlin Multiplatform (KMP) project designed for the European Digital Identity (EUDI) Wallet ecosystem. It provides an extensible and secure framework for Certificate Chain Validation against dynamic Trust Anchors, enabling the verification of credentials and attestation objects by navigating trust trees within the European Union's identity framework.

## Problem Solved

In the EUDI ecosystem, verifying the trustworthiness of credentials (like PID or EAA) and attestations (like Wallet Unit Attestation) requires validating certificate chains against specific trust roots. These trust roots are often dynamic and published in various formats, such as ETSI TS 119 612 Trusted Lists.

This library simplifies this process by:
- Separating trust discovery from validation logic.
- Providing high-level abstractions for common EUDI use cases.
- Offering implementations for automated fetching and verification of the European List of Trusted Lists (LOTL).

## Target Audience

This library is designed for:
- **Wallets**: To verify the trustworthiness of credentials they receive and to handle attestation objects.
- **Verifiers (Relying Parties)**: To verify credentials presented by a Wallet against the required trust anchors.
- **Issuers**: To verify Wallet attestations during the credential issuance process.

## Project Structure

The library is divided into two main modules:

### 1. [Consultation](./consultation/README.md)
The core module providing the fundamental abstractions and common validation logic.
- **Features**: Functional architecture for trust discovery, attestation classification, and certificate chain validation.
- **Platform Support**: Common (KMP).

### 2. [Consultation-DSS](./consultation-dss/README.md)
An extension module that leverages the [Digital Signature Service (DSS)](https://github.com/esig/dss) to support ETSI Trusted Lists.
- **Features**: Automated LOTL/TL synchronization, multi-tier caching (In-Memory, File System), and DSS-based validation.
- **Platform Support**: JVM and Android.

## Key Abstractions

- `VerificationContext`: Represents specific EUDI use cases (e.g., PID issuance).
- `GetTrustAnchors`: A functional interface for retrieving anchors based on a query.
- `IsChainTrustedForEUDIW`: The orchestrator that resolves trust anchors and triggers validation.

## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

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
