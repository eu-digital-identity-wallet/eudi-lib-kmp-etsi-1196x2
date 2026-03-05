# 119602 Consultation Module

The EUDI ETSI TS 119 602 Consultation module provides abstractions and implementations for validating certificate chains against trust anchors published in **ETSI TS 119 602 Lists of Trusted Entities (LoTE)**.

This module enables Wallets, Issuers, and Verifiers to verify the trustworthiness of credentials (PIDs, EAAs) and attestation objects by navigating trust trees defined in LoTE format, as specified by ETSI TS 119 602.

> [!WARNING]
> **Status: Under Development** - This module is currently being developed. API and features may change.

---

## Purpose

The module automates the process of:
- Fetching and parsing ETSI TS 119 602 LoTE documents
- Extracting trust anchors from LoTE entries
- Validating certificate chains against LoTE-derived trust anchors
- Enforcing certificate constraints per ETSI specifications

---

## Quick Start

> [!NOTE]
> API and usage examples will be added once the module implementation is complete.

```kotlin
// TBD - API under development
```

---

## Core Abstractions

> [!NOTE]
> Detailed documentation of core abstractions will be added once the module implementation is complete.

Planned abstractions include:

🔍 **LoTE Discovery**
- `LoadLoTE`: Fetching and parsing LoTE documents
- `GetTrustAnchorsFromLoTE`: Extracting trust anchors from LoTE entries

🛡️ **Validation**
- Integration with `ValidateCertificateChainUsingPKIX` and `ValidateCertificateChainUsingDirectTrust` from the consultation module

📋 **Certificate Constraints**
- Profile-specific constraint evaluators for PID, Wallet, WRPAC, and WRPRC providers

---

## Platform Support

The 119602-consultation module is a **Kotlin Multiplatform (KMP)** module.

- **commonMain**: Core logic and abstractions.
- **jvmAndAndroidMain**: Specific implementations for JVM and Android.

---

## Dependencies

> [!NOTE]
> Dependencies will be documented once the module implementation is complete.

Expected dependencies:
- `eu.europa.ec.eudi:etsi-1196x2-consultation` (core consultation module)
- `eu.europa.ec.eudi:etsi-119602-data-model` (LoTE data model)
- kotlinx.serialization (JSON parsing)

---

## References

- [ETSI TS 119 602 - Lists of Trusted Entities (LoTE)](https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/)
- [ETSI TS 119 612 - Trusted Lists](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/)
- [EUDI Wallet Reference Implementation](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

---

## See Also

- **[Root README](../README.md)** - Project overview and installation
- **[Consultation Module](../consultation/README.md)** - Core abstractions for certificate chain validation
- **[Consultation-DSS Module](../consultation-dss/README.md)** - ETSI Trusted Lists support via DSS
- **[119602-data-model Module](../119602-data-model/README.md)** - ETSI TS 119 602 LoTE JSON data model

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
