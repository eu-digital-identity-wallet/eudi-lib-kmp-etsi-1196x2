# Certificate Chain Validation Using EU Provider Lists (LoTE) for EUDI Wallet

**Document Version:** 4.6
**Date:** 2026-03-18
**Purpose:** Analysis of ETSI specifications for certificate chain validation against EU Provider Lists (LoTE) serving
as trust anchor sources for PID, Wallet, WRPAC, and WRPRC providers

---

## Executive Summary

This document analyzes ETSI specifications to understand how certificates found in EU Providers Lists (defined in ETSI
TS 119 602) can be used to validate certificate chains in the context of EUDI Wallet.

### Key Findings

| Provider List                     | ETSI TS 119 602 Annex | LoTE Certificate Type | What LoTE Validates                       | Certificate Chain Validation |
|-----------------------------------|-----------------------|-----------------------|-------------------------------------------|------------------------------|
| **PID Providers (Issuance)**      | Annex D               | End-entity OR CA      | PID signature                             | **Direct Trust OR PKIX**     |
| **PID Providers (Revocation)**    | Annex D               | End-entity OR CA      | PID TSL JWT/CWT signature                 | **Direct Trust OR PKIX**     |
| **Wallet Providers (Issuance)**   | Annex E               | End-entity OR CA      | WIA/WUA signature                         | **Direct Trust OR PKIX**     |
| **Wallet Providers (Revocation)** | Annex E               | End-entity OR CA      | WUA TSL JWT signature                     | **Direct Trust OR PKIX**     |
| **WRPAC Providers**               | Annex F               | CA                    | VCI metadata, VP request Object signature | **PKIX**                     |
| **WRPRC Providers (Issuance)**    | Annex G               | CA                    | WRPRC signature                           | **PKIX**                     |
| **WRPRC Providers (Revocation)**  | Annex G               | CA                    | WRPRC TSL JWT/CWT signature               | **PKIX**                     |

### Chain Verifications

The following chain verification cases have been identified.
Each chain verification case, is a step towards verifying the digital signature of a signed statement. 

#### PID

- **PID Provider** = Signs PID attestation
- **PID Attestation** = SD-JWT-VC or mDoc containing person identification data
- **PID `x5c`** = Certificate chain to verify PID signature
- **LoTE (Issuance)** = Contains PID Provider's certificate (end-entity or CA)
- **PID Provider signing certificate** = The certificate that corresponds to the private key used to sign the PID attestation
- **Certificate Chain Validation** = Direct Trust OR PKIX, including checking the PID Provider signing certificate matches ETSI TS 119 412-6 profile (section 4)
- **Attestation Signature Verification** = Uses validated certificate (separate step)

#### PID status

- **Token Status List (TSL)** = JWT containing revocation status bitstring for PID attestations
- **PID Attestation** = Contains `status.status_list` object with TSL URI and bit index
- **TSL `x5c`** = Certificate chain to verify TSL signature (end-entity → intermediate → LoTE CA)
- **LoTE (Revocation)** = Contains PID Provider's certificate (end-entity or CA) used to verify TSL signature
- **TSL Signature Validation** = Direct Trust OR PKIX

**Note:** For mDoc-encoded PID attestations, revocation may use Token Status List OR List Identifiers per ISO/IEC
18013-5 2nd edition (unpublished).


#### Wallet Attestations WIA/WUA

- **Wallet Provider** = Signs Wallet attestations (acts as end-entity or CA)
- **Wallet Instance Attestation (WIA)** = JWT attesting wallet instance properties
- **Wallet Unit Attestation (WUA)** = JWT attesting wallet unit properties (including attested keys)
- **WIA/WUA `x5c`** = Certificate chain to verify attestation signature
- **LoTE (Issuance)** = Contains Wallet Provider's certificate (end-entity or CA) to verify WIA/WUA signature
- **Certificate Chain Validation** = Contains PID Provider's certificate (end-entity or CA) used to verify TSL signature
- **Attestation Signature Verification** = Uses validated certificate (separate step)

#### WUA Status

- **Token Status List (TSL)** = JWT containing revocation status bitstring for WUA
- **WUA** = Contains `status.status_list` object with TSL URI and bit index
- **TSL `x5c`** = Certificate chain to verify TSL signature
- **LoTE (Revocation)** = Contains Wallet Provider's CA certificate (trust anchor for TSL)
- **TSL Validation** = PKIX + signature verification + bit check

#### Wallet Relying Party Access Certificate (WRAC)

- **WRPAC Provider** = CA that issues WRPAC certificates to Wallet Relying Parties
- **WRPAC** = End-entity certificate held by Wallet Relying Party
- **LoTE** = Contains WRPAC Provider's CA certificate (trust anchor)
- **Certificate Chain Validation** = PKIX (chain from WRPAC `x5c` end-entity to LoTE CA) to validate the `x5c` of
    - signed Credential Issuer metadata (when WRP acts as an EAA Provider), see OpenID4VCI.
    - signed Request Object metadata (when WRP acts as a Verifier), see OpenID4VP.

#### Wallet Relying Party Registration Certificate (WRPRC)

- **WRPRC Provider** = Signs WRP Registration Certificates JWT/CWT attestations
- **WRPRC** = JWT/CWT attestation declaring WRP entitlements and intended use
- **WRPRC `x5c`** = Certificate chain to verify WRPRC signature (end-entity → intermediate → LoTE CA)
- **LoTE (Issuance)** = Contains WRPRC Provider's CA certificate (trust anchor)
- **Certificate Chain Validation** = PKIX (chain from WRPRC `x5c` end-entity to LoTE CA) to validate the `x5c` of
  the Registration Certificate.

#### Wallet Relying Party Registration Certificate Status

- **Token Status List (TSL)** = JWT/CWT containing revocation status bitstring
- **WRP Registration Certificate** = Contains `status.status_list` object with TSL URI and bit index
- **TSL `x5c`** = Certificate chain to verify TSL signature (end-entity → intermediate → LoTE CA)
- **LoTE (Revocation)** = Contains WRPRC Provider's CA certificate (trust anchor for TSL)
- **Certificate Chain Validation** = PKIX (chain from WRPRC `x5c` end-entity to LoTE CA) for TSL `x5c`

---

## References

### ETSI Specifications

- **ETSI TS 119 412-6 V1.1.1**: "Certificate profile requirements for PID, Wallet, EAA, QEAA, and PSBEAA providers"
- **ETSI TS 119 472-3**: "PID attestation issuance" (profiles HAIP v1 for PID)
- **ETSI TS 119 475 V1.2.1**: "Relying party attributes supporting EUDI Wallet user's authorization decisions"
- **ETSI TS 119 602 V1.1.1**: "Lists of trusted entities; Data model"
- **ETSI TS 119 612 V2.4.1**: "Trusted Lists" (August 2025)¹
- **ETSI EN 319 412-2 V2.4.1**: "Certificate profile for certificates issued to natural persons"
- **ETSI EN 319 412-3 V1.3.1**: "Certificate profile for certificates issued to legal persons"
- **ETSI EN 319 412-5 V2.5.1**: "QCStatements"

**Footnotes:**
¹ **ETSI TS 119 612 Version Note:** V2.4.1 (August 2025) is the latest version. V2.3.1 (November 2024) updated the eIDAS
reference to acknowledge Regulation (EU) 2024/1183 and Directive (EU) 2022/2555, but contains no substantive changes
affecting the certificate validation analysis. V2.4.1 is a maintenance release with no technical changes. The EUDI
Wallet provider lists are defined in ETSI TS 119 602, not TS 119 612. See `TS119612-Version-Comparison.md` for detailed
analysis.

### OIDF Specifications

- **OpenID4VC High Assurance Interoperability Profile 1.0 (HAIP v1)**: "OpenID4VC High Assurance Interoperability
  Profile 1.0" (openid4vc-high-assurance-interoperability-profile-1_0)
    - Profiles OpenID4VCI, OpenID4VP, SD-JWT-VC, and ISO mdoc for high assurance use cases
    - Requires Token Status List for SD-JWT-VC revocation
    - References ISO/IEC 18013-5 2nd edition for mdoc revocation
- **OpenID for Verifiable Credential Issuance 1.0 (OID4VCI)**: "OpenID for Verifiable Credential Issuance 1.0"
- **OpenID for Verifiable Presentations 1.0 (OID4VP)**: "OpenID for Verifiable Presentations 1.0"

### IETF RFCs

- **RFC 5280**: "Internet X.509 Public Key Infrastructure Certificate and CRL Profile"
- **RFC 7515**: "JSON Web Signature (JWS)"
- **RFC 9901**: "SD-JWT (Selective Disclosure for JWT)"

### IETF Internet-Drafts

- **draft-ietf-oauth-status-list**: Looker, T., Bastian, P., and C. Bormann, "Token Status List (TSL)", Work in
  Progress, Internet-Draft, draft-ietf-oauth-status-list-18, 18 February
  2026, <https://datatracker.ietf.org/doc/html/draft-ietf-oauth-status-list-18>

### ISO/IEC Standards

- **ISO/IEC 18013-5 (2nd edition)**: "Personal identification — ISO-compliant driving licence — Part 5: Mobile driving
  licence (mDL) applications" (unpublished, in development)
    - Defines mDoc format for mobile driving licences
    - Specifies revocation mechanisms including Token Status List and List Identifiers
    - Referenced for PID attestation in mDoc format

---

**Disclaimer:** This analysis is based on publicly available ETSI specifications and should be validated against actual
implementations and national requirements.
