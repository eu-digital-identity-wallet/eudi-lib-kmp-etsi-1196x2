# Certificate Chain Validation Using EU Provider Lists (LoTE) for EUDI Wallet Attestations

**Document Version:** 4.6
**Date:** 2026-03-18
**Purpose:** Analysis of ETSI specifications for certificate chain validation against EU Provider Lists (LoTE) serving
as trust anchor sources for PID, Wallet, WRPAC, and WRPRC providers

**Version 4.6 Changes:**

- Updated to ETSI TS 119 475 v1.2.1 (March 2026)
- Added Token Status List validation for WRPRC revocation checking
- Restructured WRPRC validation sections to distinguish Issuance and Revocation services
- Updated Executive Summary with WRPRC dual-service architecture
- Updated Open Questions to reflect Token Status List mechanism

**Version 4.5 Changes:**

- Corrected specification ambiguity analysis for PID/Wallet Providers (Annexes D/E)
- Acknowledged that ETSI TS 119 602 Annexes D/E do not explicitly exclude CA certificates
- Added implementation guidance for handling specification ambiguity
- Changed validation method conclusions from definitive to qualified language

**Version 4.4 Changes:**

- Updated improvement status: AIA validation for PID/Wallet providers is now implemented and integrated.
- Standardized OIDs and fixed Wallet Provider policy OID inconsistency.

---

## Executive Summary

This document analyzes ETSI specifications to understand how certificates found in EU Providers Lists (defined in ETSI
TS 119 602) can be used to validate certificate chains for attestations issued in SD-JWT-VC or JWT format.

### Key Findings

| Provider List        | ETSI TS 119 602 Annex | LoTE Certificate Type                              | What LoTE Validates        | Certificate Chain Validation        |
|----------------------|-----------------------|----------------------------------------------------|----------------------------|-------------------------------------|
| **PID Providers**    | Annex D               | End-entity OR CA¹                                  | PID signing certificate    | **Direct Trust OR PKIX**¹           |
| **Wallet Providers** | Annex E               | End-entity OR CA¹                                  | Wallet signing certificate | **Direct Trust OR PKIX**¹           |
| **WRPAC Providers**  | Annex F               | CA (WRPAC Provider)                                | WRPAC X.509 certificate    | **PKIX**                            |
| **WRPRC Providers (Issuance)**  | Annex G               | CA (WRPRC Provider)²                               | WRPRC JWT `x5c` chain      | **PKIX**                            |
| **WRPRC Providers (Revocation)** | Annex G          | CA (WRPRC Provider)³                               | TSL JWT/CWT `x5c` chain    | **PKIX**                            |

**Footnotes:**

¹ **PID/Wallet Specification Ambiguity:** ETSI TS 119 602 Annexes D and E describe the *purpose* of certificates ("verify the signature... on the person identification data" / "authenticate and validate the components of the wallet unit") but do not explicitly specify whether the certificate must be end-entity or CA. The functional description is compatible with **both** Direct Trust (end-entity in LoTE) and PKIX (CA in LoTE) validation methods. Implementations should support both validation methods. See Section 1.7 for detailed analysis.

² **WRPRC Issuance Service:** Table G.3 wording ("verify the signature... on the registration certificate it provides") is identical to Table F.3 (WRPAC), indicating the LoTE contains a **CA certificate** (WRPRC Provider), not an end-entity signing certificate. The WRPRC JWT `x5c` header contains "the whole certificate chain" (ETSI TS 119 475 Table 5), which builds via PKIX to the LoTE CA. See Section 4.2.2 for detailed analysis.

³ **WRPRC Revocation Service (NEW in v1.2.1):** ETSI TS 119 475 v1.2.1 introduces Token Status List for WRPRC revocation. The Token Status List is a JWT/CWT with its own `x5c` chain. The WRPRC Providers LoTE (service type: Revocation) contains the CA certificate for validating the TSL signature. See Section 4.5 for detailed analysis.

**Critical Distinction:**

- **PID/Wallet Providers**: Sign data directly → End-entity OR CA certificate in LoTE → Direct Trust OR PKIX validation - *Specification allows both options*
- **WRPAC Providers**: Issue X.509 certificates to Relying Parties → CA certificate in LoTE → PKIX validation (chain from WRPAC to LoTE CA)
- **WRPRC Providers (Issuance)**: Sign JWT attestations → CA certificate in LoTE → PKIX validation (chain from WRPRC `x5c` to LoTE CA), then JWT signature verification
- **WRPRC Providers (Revocation)**: Sign Token Status Lists → CA certificate in LoTE → PKIX validation (chain from TSL `x5c` to LoTE CA), then TSL signature verification

**Note on WRPRC:** The WRPRC itself is a JWT attestation (NOT an X.509 certificate). The certificate chain validation (
PKIX) verifies the `x5c` certificates in the WRPRC JWT header against the LoTE. JWT signature verification is a separate
step that uses the validated certificate.

**WRPAC Certificate Architecture:**

- **WRPAC Provider** = CA that issues WRPAC certificates to Wallet Relying Parties
- **WRPAC** = End-entity certificate held by Wallet Relying Party, included in JWT `x5c`
- **LoTE** = Contains WRPAC Provider's CA certificate (trust anchor)
- **PKIX validation** = Builds chain from WRPAC (x5c) to LoTE trust anchor

**WRPRC Attestation Architecture:**

- **WRPRC Provider** = Signs WRPRC JWT attestations (acts as CA)
- **WRPRC** = JWT attestation declaring WRP entitlements and intended use
- **WRPRC `x5c`** = Certificate chain to verify WRPRC signature (end-entity → intermediate → LoTE CA)
- **LoTE (Issuance)** = Contains WRPRC Provider's CA certificate (trust anchor)
- **Certificate Chain Validation** = PKIX (chain from WRPRC `x5c` end-entity to LoTE CA)
- **JWT Signature Verification** = Uses validated end-entity certificate from `x5c` (separate step)

**WRPRC Token Status List Architecture (NEW in v1.2.1):**

- **Token Status List (TSL)** = JWT/CWT containing revocation status bitstring
- **WRPRC** = Contains `status.status_list` object with TSL URI and bit index
- **TSL `x5c`** = Certificate chain to verify TSL signature (end-entity → intermediate → LoTE CA)
- **LoTE (Revocation)** = Contains WRPRC Provider's CA certificate (trust anchor for TSL)
- **TSL Validation** = PKIX (chain from TSL `x5c` to LoTE CA) + signature verification + bit check

**Dual-Layer Trust Framework:**

- **WRPAC** = Authentication ("Who are you?") - X.509 certificate
- **WRPRC** = Authorization ("What can you do?") - JWT attestation
- WRP must have valid WRPAC before obtaining WRPRC

### HAIP v1 / OpenID4VCI v1 Context

- `x5c` in JWT header MUST NOT include trust anchor (per HAIP v1)
- For PID/Wallet: `x5c` contains end-entity, LoTE contains end-entity (Direct Trust) OR CA (PKIX) → **Support both validation methods**
- For WRPAC: `x5c` contains end-entity, LoTE contains CA → PKIX path validation required
- For WRPRC: `x5c` in WRPRC JWT header contains cert chain (end-entity → CA), LoTE (Issuance) contains CA → PKIX path validation required (then JWT signature verification)
- For TSL: `x5c` in TSL JWT/CWT header contains cert chain (end-entity → CA), LoTE (Revocation) contains CA → PKIX path validation required (then TSL signature verification + bit check)

---

### Document Structure

For each provider list type, this document provides two sub-sections:

1. **Certificate Profile Requirements**: What certificate types are applicable (ETSI TS 119 412-6 or other)
2. **LoTE ServiceDigitalIdentity Requirements (ETSI TS 119 602 Annex)**: Which certificates can be published in the List
   of Trusted Entities

---

# PID Providers

## 1.1 Certificate Profile Requirements (ETSI TS 119 412-6)

#### 1.1.1 Scope (Clause 1)

> "The present document specifies requirements on the content **end entity certificates** used by Person Identification
> Data (PID) providers, Public Sector Body's Electronic Attestation of Attributes (PSBEAA) providers, Electronic
> Attestation of Attributes (EAA) providers, Qualified Electronic Attestation of Attributes (QEAA) providers and Wallet
> providers."

**Finding 1.1.1:** PID Provider certificates are explicitly **end-entity certificates**, NOT CA certificates.

#### 1.1.2 Issuer Requirements (Clause 4.2 - PID-4.2-01)

```
PID-4.2-01: The issuer shall be:
a) as specified in ETSI EN 319 412-2 [5], clause 4.2.3;  OR
b) as defined for the subject if the PID certificate is self certified.
```

**Two Allowed Issuance Models:**

| Model                        | Issuer          | Certificate Type          | AIA Required |
|------------------------------|-----------------|---------------------------|--------------|
| **Option A: CA-Issued**      | Intermediate CA | End-entity (issued by CA) | YES          |
| **Option B: Self-Certified** | Same as Subject | Self-signed end-entity    | NO           |

**Finding 1.1.2:** ETSI TS 119 412-6 allows **both** self-signed and CA-issued end-entity certificates for PID
Providers.

#### 1.1.3 Authority Information Access (Clause 4.4.3)

**PID-4.4.3-01 [CONDITIONAL]:**
> "If the PID attribute sign/seal certificate is **not self signed** [...] the Authority Information Access extension *
*shall be present**."

**PID-4.4.3-02:**
> "The Authority Information Access extension shall include an accessMethod OID, **id-ad-caIssuers**, with an
> accessLocation value specifying at least one access location of a valid **CA certificate of intermediate CA**."

**Finding 1.1.3:**

- CA-issued model → AIA extension REQUIRED, pointing to intermediate CA certificate
- Self-signed model → AIA extension NOT required

#### 1.1.4 QCStatement Requirement (Clause 4.5 - PID-4.5-01)

> "The certificate shall contain the **QcType qcStatement** as defined in ETSI EN 319 412-5 [4], with the value *
*id-etsi-qct-pid** defined in Annex A of the present document."

**Finding 1.1.4:** PID Provider certificates MUST contain QCStatement with `id-etsi-qct-pid` to identify them as
specifically for PID issuance.

#### 1.1.5 Summary: PID Certificate Profile (ETSI TS 119 412-6)

| Aspect                 | Requirement                                                          |
|------------------------|----------------------------------------------------------------------|
| **Certificate Type**   | End-entity ONLY (NOT CA)                                             |
| **Issuer Options**     | Self-signed OR CA-issued                                             |
| **AIA Extension**      | Required if CA-issued, Not required if self-signed                   |
| **QCStatement**        | Mandatory (`id-etsi-qct-pid`)                                        |
| **Key Usage**          | digitalSignature (for signing PID attestations)                      |
| **Certificate Policy** | **Present** (TSP-defined OID per EN 319 412-2 §4.3.3, not validated) |

---

## 1.2 LoTE ServiceDigitalIdentity Requirements (ETSI TS 119 602 Annex D)

#### 1.2.1 Annex D Scope (EU PID Providers List Profile)

> "The present Annex specifies a LoTE profile aimed at supporting the publication by the European Commission of a list
> of providers of person identity data according to CIR 2024/2980 [i.2], Article 5(2)."

**Finding 1.2.1:** Annex D defines the **EU PID Providers List** profile specifically.

#### 1.2.2 Table D.3: Service Digital Identity for PID Providers

> "The ServiceDigitalIdentity component **shall contain one or more X.509 certificates** that can be used to verify the
> signature or seal created by the provider of person identification data **on the person identification data it
> provides
**, and for which the certified identity data include the name, and where applicable, the registration number of the
> person identification data provider, as specified in the TEName and TETradeName components respectively."

**Finding 1.2.2:** The wording describes the **purpose** ("verify the signature... on the person identification data it provides") but does not explicitly specify certificate type. Both end-entity (Direct Trust) and CA certificates (PKIX) can serve this purpose. The specification allows both interpretations. See Section 1.7 for detailed ambiguity analysis.

#### 1.2.3 Absence of CA Certificate Option NOTE

**Observation:** Unlike Annex H (Pub-EAA Providers), Annex D **does NOT contain** the following NOTE:

> ~~"NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic attestation of attributes, or it can be the X.509 certificate corresponding to a CA issuing such X.509 certificates provided the other requirements applying to the present component are met."~~

**Finding 1.2.3:** The absence of this NOTE in Annex D (while present in Annex H) is **not a normative exclusion**. The functional description in Table D.3 is compatible with both end-entity and CA certificates. The NOTE in Annex H is clarifying, not restrictive. See Section 1.7 for detailed analysis.

#### 1.2.4 Summary: EU PID Providers LoTE Requirements (ETSI TS 119 602 Annex D)

| Aspect                       | Requirement                                            |
|------------------------------|--------------------------------------------------------|
| **Certificate Type in LoTE** | **End-entity OR CA** - specification allows both       |
| **CA Certificates Allowed?** | **YES** - specification does not exclude CA            |
| **Purpose**                  | Verify signatures on PID data directly                 |
| **Validation Method**        | **Direct Trust OR PKIX** - support both                |

---

## 1.3 Reconciliation: Certificate Profile vs LoTE Profile

#### 1.3.1 Specification Independence

| Specification                               | Allows                                                 |
|---------------------------------------------|--------------------------------------------------------|
| **ETSI TS 119 412-6 (Certificate Profile)** | Self-signed OR CA-issued end-entity certificates       |
| **ETSI TS 119 602 Annex D (LoTE Profile)**  | End-entity OR CA in ServiceDigitalIdentity             |

**Resolution:**

1. **ETSI TS 119 412-6 and ETSI TS 119 602 are independent** - The certificate profile requirements do not constrain LoTE ServiceDigitalIdentity requirements
2. **Annex D wording is ambiguous** - Describes purpose ("verify the signature"), not certificate type
3. **Both interpretations are valid**:
   - **Option A (Direct Trust)**: LoTE contains end-entity certificate (same as `x5c`)
   - **Option B (PKIX)**: LoTE contains CA certificate (issues the `x5c` end-entity)
4. **Implementations should support both** validation methods for maximum interoperability

#### 1.3.2 Certificate Chain Structures for EU PID Providers

**Option A: Direct Trust (LoTE contains end-entity)**
```
┌─────────────────────────────────┐
│  EU PID Providers LoTE          │
│  (PID Provider End-Entity Cert) │
│  - basicConstraints: cA=FALSE   │
│  - QCStatement: id-etsi-qct-pid │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Matches
         ▼
┌─────────────────────────────────┐
│  PID Attestation (SD-JWT-VC)    │
│  - x5c: [PID Provider Cert]     │
│  - (same as LoTE certificate)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: PID Provider end-entity certificate
Validation Method: Direct Trust (certificate subject + serial match)
```

**Option B: PKIX (LoTE contains CA)**
```
┌─────────────────────────────────┐
│  EU PID Providers LoTE          │
│  (PID Provider CA Certificate)  │
│  - basicConstraints: cA=TRUE    │
│  - Trust Anchor                 │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Issues
         ▼
┌─────────────────────────────────┐
│  PID Provider Certificate       │
│  (End-Entity)                   │
│  - basicConstraints: cA=FALSE   │
│  - QCStatement: id-etsi-qct-pid │
│  - AIA: id-ad-caIssuers         │
│  - Key Usage: digitalSignature  │
│  ★ In JWT x5c ★                 │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  PID Attestation (SD-JWT-VC)    │
│  - x5c: [PID Provider Cert]     │
│  - (end-entity, issued by CA)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: PID Provider CA certificate
Validation Method: PKIX (chain from x5c end-entity to LoTE CA)
```

**Key Point:** The specification allows both options. Implementations should support both Direct Trust and PKIX validation methods.

---

## 1.4 Validation Method for EU PID Providers List

#### 1.4.1 Direct Trust Validation

When the LoTE contains the end-entity signing certificate (same as the one in the JWT `x5c`), validation is performed by direct certificate matching:

1. Extract the first certificate from JWT `x5c` header
2. Load trust anchors from EU PID Providers LoTE ServiceDigitalIdentity
3. Match certificate by subject name and serial number
4. Verify validity period (notBefore, notAfter)
5. Verify revocation status (if CRL/OCSP available)
6. Verify key usage (digitalSignature bit)

#### 1.4.2 PKIX Validation

When the LoTE contains a CA certificate, validation is performed via PKIX path validation:

1. Extract the certificate chain from JWT `x5c` header
2. Load trust anchors from EU PID Providers LoTE ServiceDigitalIdentity
3. Build certification path from end-entity certificate to LoTE trust anchor
4. Verify signature on each certificate in the path
5. Verify basicConstraints (cA=TRUE for intermediates)
6. Verify pathLenConstraint
7. Verify validity periods
8. Check revocation (CRL/OCSP)
9. Verify key usage (digitalSignature for end-entity)

**Note:** The specification allows both validation methods. Implementations should support both.

---

## 1.5 Compliance Requirements Summary for PID Providers

| Requirement                      | Specification       | Clause                    | Requirement Level            |
|----------------------------------|---------------------|---------------------------|------------------------------|
| PID certificates are end-entity  | ETSI TS 119 412-6   | Scope                     | MUST                          |
| Self-signed OR CA-issued allowed | ETSI TS 119 412-6   | PID-4.2-01                | MAY (issuer choice)          |
| AIA if CA-issued                 | ETSI TS 119 412-6   | PID-4.4.3-01              | MUST (conditional)           |
| QCStatement with id-etsi-qct-pid | ETSI TS 119 412-6   | PID-4.5-01                | MUST                          |
| Certificate Policy present       | EN 319 412-2        | §4.3.3                    | MUST                          |
| LoTE certificate type            | ETSI TS 119 602     | Annex D, Table D.3        | **End-entity OR CA**          |
| CA certificates allowed in LoTE  | ETSI TS 119 602     | Annex D                   | **YES** - not excluded        |
| Validation method                | Inferred from above | N/A                       | **Direct Trust AND PKIX**     |

---

## 1.6 Open Questions for Further Investigation

1. **Implementation Practice:** Do actual EU PID Provider implementations use self-signed or CA-issued certificates?

2. **Revocation for Self-Signed:** If a PID Provider uses self-signed certificates, what are the revocation mechanisms?

3. **Multi-Certificate LoTE:** Can a single PID Provider have multiple end-entity certificates in the LoTE (e.g., for
   key rotation)?

4. **National Variations:** Are there national implementations that deviate from the EU PID Providers List profile?

**Implementation Note:** Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present. The specific
policy OIDs are TSP-defined (not mandated by ETSI TS 119 412-6). The validator checks for the presence of the
certificatePolicies extension but does not validate specific OID values.

---

## 1.7 Specification Ambiguity Analysis

### 1.7.1 The Ambiguity in ETSI TS 119 602 Annex D

ETSI TS 119 602 Annex D Table D.3 states:

> "The ServiceDigitalIdentity component **shall contain one or more X.509 certificates** that can be used to verify the signature or seal created by the provider of person identification data **on the person identification data it provides**, and for which the certified identity data include the name, and where applicable, the registration number of the person identification data provider, as specified in the TEName and TETradeName components respectively."

**Critical Analysis:** This wording describes the **purpose** of the certificates ("verify the signature... on the person identification data") but does **not** explicitly specify:

1. Whether the certificate must be the **end-entity signing certificate** (Direct Trust)
2. Whether the certificate can be a **CA certificate** that issued the signing certificate (PKIX)

**Both interpretations are technically valid:**
- An end-entity certificate can verify signatures directly
- A CA certificate can verify signatures via PKIX path validation

### 1.7.2 Comparison with Annex H (Pub-EAA Providers)

Annex H (Pub-EAA Providers) **explicitly clarifies** this ambiguity with the following NOTE:

> "NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic attestation of attributes, **or it can be the X.509 certificate corresponding to a CA issuing such X.509 certificates** provided the other requirements applying to the present component are met."

**This NOTE proves that:**
1. The drafters of ETSI TS 119 602 were **aware** of the ambiguity
2. They **explicitly clarified** it for Annex H (Pub-EAA) to allow both options
3. The **absence of this NOTE** in Annexes D and E is significant, but not definitive

### 1.7.3 Interpretation vs. Specification Requirement

The document's previous conclusion that "CA certificates are NOT allowed" in Annex D was based on:
- The absence of the clarifying NOTE present in Annex H
- An interpretation of the phrase "verify the signature... on the person identification data"

**This is an interpretation, NOT a specification requirement.** The specification text does not explicitly exclude CA certificates.

### 1.7.4 Independence of ETSI TS 119 412-6 and ETSI TS 119 602

**Critical Finding:** ETSI TS 119 412-6 (Certificate Profiles for PID/Wallet providers) makes **no normative reference** to ETSI TS 119 602 (LoTE specification). These are independent specifications:

- **ETSI TS 119 412-6**: Defines certificate profiles for PID/Wallet provider certificates (end-entity, with QCStatement)
- **ETSI TS 119 602**: Defines LoTE data model and what certificates can be published in ServiceDigitalIdentity

The certificate profile requirements (412-6) do not constrain the LoTE ServiceDigitalIdentity requirements (119 602), and vice versa.

### 1.7.5 Recommended Implementation Approach

Given the specification ambiguity, implementations should:

1. **Support Both Validation Methods**
   - Direct Trust: For deployments where LoTE contains end-entity certificates
   - PKIX: For deployments where LoTE contains CA certificates
   - Both options are valid per the specification

2. **Monitor for Clarification**
   - ETSI may issue clarification or corrigendum
   - National authorities may provide implementation guidance

3. **Log Validation Method**
   - Log which validation method was used (Direct Trust vs PKIX)
   - Include validation method in audit trails

### 1.7.6 Conclusion on Specification Ambiguity

| Aspect                               | Conclusion                                                                 |
|--------------------------------------|----------------------------------------------------------------------------|
| Specification text                   | Ambiguous - describes purpose, not certificate type                        |
| Absence of Annex H NOTE in D/E       | Not a normative exclusion - both options are valid                         |
| Valid interpretations                | **Both** end-entity (Direct Trust) AND CA (PKIX)                           |
| Recommended implementation           | **Support both** Direct Trust AND PKIX                                     |
| Need for ETSI clarification          | **Yes** - Should explicitly clarify Annexes D/E in future revision         |

---

# Wallet Providers

## 2.1 Certificate Profile Requirements (ETSI TS 119 412-6)

#### 2.1.1 Scope (Clause 5)

> "**5 Wallet Provider sign/seal certificate profile requirements**"

**WAL-5.1-01:**
> "The requirements of clauses 4.1 to 4.4 on certificate profiles shall apply."

**Finding 2.1.1:** Wallet Provider certificates follow the **same requirements** as PID Provider certificates (clauses
4.1 to 4.4).

#### 2.1.2 Issuer Requirements (via WAL-5.1-01 → PID-4.2-01)

By reference to PID-4.2-01:

```
The issuer shall be:
a) as specified in ETSI EN 319 412-2 [5], clause 4.2.3;  OR
b) as defined for the subject if the certificate is self certified.
```

**Finding 2.1.2:** ETSI TS 119 412-6 allows **both** self-signed and CA-issued end-entity certificates for Wallet
Providers.

#### 2.1.3 Authority Information Access (via WAL-5.1-01 → PID-4.4.3)

By reference to PID-4.4.3-01/02:

**PID-4.4.3-01 [CONDITIONAL]:**
> "If the Wallet Provider sign/seal certificate is **not self signed** [...] the Authority Information Access extension
**shall be present**."

**PID-4.4.3-02:**
> "The Authority Information Access extension shall include an accessMethod OID, **id-ad-caIssuers**, with an
> accessLocation value specifying at least one access location of a valid **CA certificate of intermediate CA**."

**Finding 2.1.3:**

- CA-issued model → AIA extension REQUIRED, pointing to intermediate CA certificate
- Self-signed model → AIA extension NOT required

#### 2.1.4 QCStatement Requirement (Clause 5.2 - WAL-5.2-01)

> "The certificate shall contain the **QcType qcStatement** as defined in ETSI EN 319 412-5 [4], and the values shall be
> set to **id-etsi-qct-wal** defined in Annex A of the present document."

**Finding 2.1.4:** Wallet Provider certificates MUST contain QCStatement with `id-etsi-qct-wal` to identify them as
specifically for Wallet issuance.

#### 2.1.5 Summary: Wallet Provider Certificate Profile (ETSI TS 119 412-6)

| Aspect                 | Requirement                                                          |
|------------------------|----------------------------------------------------------------------|
| **Certificate Type**   | End-entity ONLY (NOT CA)                                             |
| **Issuer Options**     | Self-signed OR CA-issued                                             |
| **AIA Extension**      | Required if CA-issued, Not required if self-signed                   |
| **QCStatement**        | Mandatory (`id-etsi-qct-wal`)                                        |
| **Key Usage**          | digitalSignature (for signing wallet attestations)                   |
| **Certificate Policy** | **Present** (TSP-defined OID per EN 319 412-2 §4.3.3, not validated) |

---

## 2.2 LoTE ServiceDigitalIdentity Requirements (ETSI TS 119 602 Annex E)

#### 2.2.1 Annex E Scope (EU Wallet Providers List Profile)

> "The present Annex specifies a LoTE profile aimed at supporting the publication by the European Commission of a list
> of wallet providers..."

**Finding 2.2.1:** Annex E defines the **EU Wallet Providers List** profile specifically.

#### 2.2.2 Table E.3: Service Digital Identity for Wallet Providers

> "The ServiceDigitalIdentity component **shall contain one or more X.509 certificates** that can be used to *
*authenticate and validate the components of the wallet unit** the wallet provider provides under the wallet solution
> identified through the ServiceName component, and for which the certified identity data includes the name, and where
> applicable, the registration number of the wallet provider, as specified in the TEName and TETradeName components
> respectively."

**Finding 2.2.2:** The wording describes the **purpose** ("authenticate and validate the components of the wallet unit") but does not explicitly specify certificate type. Both end-entity (Direct Trust) and CA certificates (PKIX) can serve this purpose. The specification allows both interpretations. See Section 1.7 for detailed ambiguity analysis.

#### 2.2.3 Absence of CA Certificate Option NOTE

**Observation:** Unlike Annex H (Pub-EAA Providers), Annex E **does NOT contain** the following NOTE:

> ~~"NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic attestation of attributes, or it can be the X.509 certificate corresponding to a CA issuing such X.509 certificates provided the other requirements applying to the present component are met."~~

**Finding 2.2.3:** The absence of this NOTE in Annex E (while present in Annex H) is **not a normative exclusion**. The functional description in Table E.3 is compatible with both end-entity and CA certificates. The NOTE in Annex H is clarifying, not restrictive. See Section 1.7 for detailed analysis.

#### 2.2.4 Summary: EU Wallet Providers LoTE Requirements (ETSI TS 119 602 Annex E)

| Aspect                       | Requirement                                      |
|------------------------------|--------------------------------------------------|
| **Certificate Type in LoTE** | **End-entity OR CA** - specification allows both |
| **CA Certificates Allowed?** | **YES** - specification does not exclude CA      |
| **Purpose**                  | Authenticate and validate wallet unit components |
| **Validation Method**        | **Direct Trust OR PKIX** - support both          |

---

## 2.3 Reconciliation: Certificate Profile vs LoTE Profile

#### 2.3.1 Specification Independence

| Specification                               | Allows                                                 |
|---------------------------------------------|--------------------------------------------------------|
| **ETSI TS 119 412-6 (Certificate Profile)** | Self-signed OR CA-issued end-entity certificates       |
| **ETSI TS 119 602 Annex E (LoTE Profile)**  | End-entity OR CA in ServiceDigitalIdentity             |

**Resolution:**

1. **ETSI TS 119 412-6 and ETSI TS 119 602 are independent** - The certificate profile requirements do not constrain LoTE ServiceDigitalIdentity requirements
2. **Annex E wording is ambiguous** - Describes purpose ("authenticate and validate"), not certificate type
3. **Both interpretations are valid**:
   - **Option A (Direct Trust)**: LoTE contains end-entity certificate (same as `x5c`)
   - **Option B (PKIX)**: LoTE contains CA certificate (issues the `x5c` end-entity)
4. **Implementations should support both** validation methods for maximum interoperability

#### 2.3.2 Certificate Chain Structures for EU Wallet Providers

**Option A: Direct Trust (LoTE contains end-entity)**
```
┌─────────────────────────────────┐
│  EU Wallet Providers LoTE       │
│  (Wallet Provider End-Entity)   │
│  - basicConstraints: cA=FALSE   │
│  - QCStatement: id-etsi-qct-wal │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Matches
         ▼
┌─────────────────────────────────┐
│  Wallet Attestation (JWT)       │
│  - x5c: [Wallet Provider Cert]  │
│  - (same as LoTE certificate)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: Wallet Provider end-entity certificate
Validation Method: Direct Trust (certificate subject + serial match)
```

**Option B: PKIX (LoTE contains CA)**
```
┌─────────────────────────────────┐
│  EU Wallet Providers LoTE       │
│  (Wallet Provider CA Cert)      │
│  - basicConstraints: cA=TRUE    │
│  - Trust Anchor                 │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Issues
         ▼
┌─────────────────────────────────┐
│  Wallet Provider Certificate    │
│  (End-Entity)                   │
│  - basicConstraints: cA=FALSE   │
│  - QCStatement: id-etsi-qct-wal │
│  - AIA: id-ad-caIssuers         │
│  - Key Usage: digitalSignature  │
│  ★ In JWT x5c ★                 │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  Wallet Attestation (JWT)       │
│  - x5c: [Wallet Provider Cert]  │
│  - (end-entity, issued by CA)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: Wallet Provider CA certificate
Validation Method: PKIX (chain from x5c end-entity to LoTE CA)
```

**Key Point:** The specification allows both options. Implementations should support both Direct Trust and PKIX validation methods.

---

## 2.4 Validation Method for EU Wallet Providers List

#### 2.4.1 Direct Trust Validation

When the LoTE contains the end-entity signing certificate (same as the one in the JWT `x5c`), validation is performed by direct certificate matching:

1. Extract the first certificate from JWT `x5c` header
2. Load trust anchors from EU Wallet Providers LoTE ServiceDigitalIdentity
3. Match certificate by subject name and serial number
4. Verify validity period (notBefore, notAfter)
5. Verify QCStatement contains `id-etsi-qct-wal`
6. Verify revocation status (if CRL/OCSP available)
7. Verify key usage (digitalSignature bit)

#### 2.4.2 PKIX Validation

When the LoTE contains a CA certificate, validation is performed via PKIX path validation:

1. Extract the certificate chain from JWT `x5c` header
2. Load trust anchors from EU Wallet Providers LoTE ServiceDigitalIdentity
3. Build certification path from end-entity certificate to LoTE trust anchor
4. Verify signature on each certificate in the path
5. Verify basicConstraints (cA=TRUE for intermediates)
6. Verify pathLenConstraint
7. Verify validity periods
8. Check revocation (CRL/OCSP)
9. Verify key usage (digitalSignature for end-entity)

**Note:** The specification allows both validation methods. Implementations should support both.

---

## 2.5 Compliance Requirements Summary for Wallet Providers

| Requirement                        | Specification       | Clause                        | Requirement Level    |
|------------------------------------|---------------------|-------------------------------|----------------------|
| Wallet certificates are end-entity | ETSI TS 119 412-6   | Scope (via WAL-5.1-01)        | MUST                  |
| Self-signed OR CA-issued allowed   | ETSI TS 119 412-6   | PID-4.2-01 (via WAL-5.1-01)   | MAY (issuer choice)  |
| AIA if CA-issued                   | ETSI TS 119 412-6   | PID-4.4.3-01 (via WAL-5.1-01) | MUST (conditional)   |
| QCStatement with id-etsi-qct-wal   | ETSI TS 119 412-6   | WAL-5.2-01                    | MUST                  |
| Certificate Policy present         | EN 319 412-2        | §4.3.3                        | MUST                  |
| LoTE certificate type              | ETSI TS 119 602     | Annex E, Table E.3            | **End-entity OR CA**  |
| CA certificates allowed in LoTE    | ETSI TS 119 602     | Annex E                       | **YES** - not excluded |
| Validation method                  | Inferred from above | N/A                           | **Direct Trust AND PKIX** |

---

## 2.6 Open Questions for Further Investigation

1. **Implementation Practice:** Do actual EU Wallet Provider implementations use self-signed or CA-issued certificates?

2. **Revocation for Self-Signed:** If a Wallet Provider uses self-signed certificates, what are the revocation
   mechanisms?

3. **Multi-Certificate LoTE:** Can a single Wallet Provider have multiple end-entity certificates in the LoTE (e.g., for
   different wallet solutions or key rotation)?

4. **National Variations:** Are there national implementations that deviate from the EU Wallet Providers List profile?

**Implementation Note:** Per EN 319 412-2 §4.3.3, the certificatePolicies extension shall be present. The specific
policy OIDs are TSP-defined (not mandated by ETSI TS 119 412-6). The validator checks for the presence of the
certificatePolicies extension but does not validate specific OID values.

---

# WRPAC Providers

## 3.1 Certificate Profile Requirements

#### 3.1.1 Applicable Certificate Profiles

Since ETSI TS 119 412-6 does not cover WRPAC certificates, the applicable profiles are:

- **ETSI TS 119 411-8**: "Policy and security requirements for Trust Service Providers issuing certificates; Part 8:
  Access Certificate Policy for EUDI Wallet Relying Parties"
- **ETSI TS 119 475**: "Relying party attributes supporting EUDI Wallet user's authorization decisions"
- **ETSI EN 319 412-2/3**: Certificate profiles for end-entity certificates (natural or legal persons)
- **ETSI TS 119 602 Annex F**: LoTE profile for WRPAC Providers List

**Finding 3.1.1:** WRPAC Providers follow **CA certificate policies** (ETSI TS 119 411-8), while the WRPAC certificates
they issue are **end-entity certificates** following ETSI EN 319 412-2/3 profiles.

#### 3.1.2 WRPAC Certificate Purpose

Per ETSI TS 119 602 Annex F and ETSI TS 119 475:

- **WRPAC is issued BY** WRPAC Providers **TO** Wallet Relying Parties
- **WRPAC is an end-entity certificate** held and controlled by the Wallet Relying Party
- WRPAC is used by Wallet Relying Parties to authenticate themselves to Wallets
- **WRPAC Provider acts as a CA** - issuing access certificates to relying parties

**Finding 3.1.2:** WRPAC Providers are **Certificate Authorities** that issue end-entity certificates (WRPAC) to Wallet
Relying Parties. The WRPAC itself is an **end-entity certificate** (NOT a CA certificate).

---

## 3.2 LoTE ServiceDigitalIdentity Requirements (ETSI TS 119 602 Annex F)

#### 3.2.1 Annex F Scope (EU WRPAC Providers List Profile)

> "The present Annex specifies a LoTE profile aimed at supporting the publication by the European Commission of a list
> of providers of wallet relying party access certificates..."

**Finding 3.2.1:** Annex F defines the **EU WRPAC Providers List** profile specifically.

#### 3.2.2 Table F.3: Service Digital Identity for WRPAC Providers

> "The ServiceDigitalIdentity component **shall contain one or more X.509 certificates** that can be used to verify the
> signature or seal created by the provider of wallet-relying party access certificates **on the access certificate it
provides to wallet-relying parties**, with, where applicable, the information required to distinguish wallet-relying
> party access certificates from other certificates."

**Critical Finding 3.2.2:** The wording explicitly states certificates verify signatures **"on the access certificate it
provides"** - this indicates the LoTE contains the **WRPAC Provider's CA certificate** (trust anchor), which is used to
verify WRPAC end-entity certificates issued to Wallet Relying Parties.

#### 3.2.3 Absence of CA Certificate Option NOTE

**Observation:** Unlike Annex H (Pub-EAA Providers), Annex F **does NOT contain** the NOTE about CA certificates:

> ~~"NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic
attestation of attributes, or it can be the X.509 certificate corresponding to a CA issuing such X.509
certificates..."~~

**However:** The main specification wording ("on the access certificate it provides") already indicates the LoTE
certificate is used to verify other certificates, making the NOTE unnecessary.

**Finding 3.2.3:** The functional description in Table F.3 indicates the LoTE contains a **CA certificate** (WRPAC
Provider's trust anchor) used to verify WRPAC end-entity certificates.

#### 3.2.4 Summary: EU WRPAC Providers LoTE Requirements (ETSI TS 119 602 Annex F)

| Aspect                       | Requirement                                                              |
|------------------------------|--------------------------------------------------------------------------|
| **Certificate Type in LoTE** | **CA certificate** (WRPAC Provider's trust anchor)                       |
| **Purpose**                  | Verify signatures on WRPAC certificates issued to wallet-relying parties |
| **WRPAC Certificate Type**   | End-entity certificate (held by Wallet Relying Party)                    |
| **Validation Method**        | **PKIX** (certification path from WRPAC to LoTE trust anchor)            |

---

## 3.3 Reconciliation: WRPAC Certificate Architecture

#### 3.3.1 Certificate Chain Structure for WRPAC

```
┌─────────────────────────────────┐
│  EU WRPAC Providers LoTE        │
│  (WRPAC Provider CA Certificate)│
│  - basicConstraints: cA=TRUE    │
│  - pathLenConstraint: N         │
│  - Trust Anchor                 │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Issues
         ▼
┌─────────────────────────────────┐
│  WRPAC (Access Certificate)     │
│  (End-Entity, issued TO WRP)    │
│  - basicConstraints: cA=FALSE   │
│  - Key Usage: digitalSignature  │
│  - Held by Wallet Relying Party │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  JWT (signed by WRPAC Cert)     │
│  - x5c: [WRPAC Cert]            │
│  - (Trust anchor NOT included)  │
│                                 │
│  Two use cases:                 │
│  1. Credential Issuer Metadata  │
│     (OpenID4VCI)                │
│  2. Authorization Request       │
│     (OpenID4VP)                 │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: WRPAC Provider CA certificate (trust anchor)
WRPAC (x5c): End-entity certificate (issued to Wallet Relying Party)
Validation Method: PKIX (chain from WRPAC in x5c to LoTE trust anchor)
```

**Key Points:**

1. **WRPAC Provider** is a CA that issues WRPAC certificates to Wallet Relying Parties
2. **LoTE contains** the WRPAC Provider's CA certificate (trust anchor)
3. **WRPAC** is an **end-entity certificate** held by the Wallet Relying Party
4. **JWT x5c** contains the WRPAC end-entity certificate (NOT the trust anchor, per HAIP v1)
5. **PKIX validation** builds the chain from WRPAC (x5c) to LoTE trust anchor

---

## 3.4 Validation Method for EU WRPAC Providers List

#### 3.4.1 OpenID4VCI / OpenID4VP / HAIP v1 Context

**Two Types of Wallet Relying Parties:**

Per the EUDI Wallet ecosystem, there are two types of Wallet Relying Parties that use WRPAC Access Certificates:

1. **Credential Issuers (OpenID4VCI)**: Issue verifiable credentials to wallet users
2. **Verifiers (OpenID4VP)**: Request and verify credentials from wallet users

**WRPAC Access Certificate Usage:**

| Wallet Relying Party Type | What is Signed             | JWT Purpose                        | Endpoint                        |
|---------------------------|----------------------------|------------------------------------|---------------------------------|
| **Credential Issuer**     | Credential Issuer Metadata | OpenID4VCI discovery/configuration | Wallet's credential endpoint    |
| **Verifier**              | Authorization Request      | OpenID4VP request to Wallet        | Wallet's authorization_endpoint |

**HAIP v1 Requirement:**

- For **both** JWT types, the `x5c` header MUST NOT include the trust anchor
- The `x5c` contains only the end-entity certificate (WRPAC Access Certificate)
- Verifier (Wallet) MUST validate `x5c` against the EU WRPAC Providers LoTE

**Implication:** The same PKIX validation logic applies to both OpenID4VCI and OpenID4VP use cases. The EU WRPAC
Providers LoTE serves as a unified trust anchor source for all Wallet Relying Party operations.

#### 3.4.2 PKIX Validation

Since the LoTE contains a CA certificate (WRPAC Provider's trust anchor) and `x5c` contains an end-entity certificate (
WRPAC), PKIX path validation is required:

1. Extract the certificate chain from JWT `x5c` header
2. Load trust anchors from EU WRPAC Providers LoTE ServiceDigitalIdentity
3. Verify LoTE certificates are CA certificates (basicConstraints >= 0)
4. Verify `x5c` contains end-entity certificate (basicConstraints < 0)
5. Build certification path from WRPAC (x5c) to LoTE trust anchor
6. Verify signature on each certificate in the path
7. Verify basicConstraints (cA=TRUE for intermediates)
8. Verify pathLenConstraint
9. Verify validity periods
10. Check revocation (CRL/OCSP)

#### 3.4.3 Why Direct Trust is NOT Applicable

Direct Trust validation requires:

1. The LoTE certificate to match the `x5c[0]` certificate exactly
2. Both certificates to be the same type (end-entity)

For EU WRPAC Providers List:

- The LoTE contains a **CA certificate** (WRPAC Provider's trust anchor)
- The `x5c` contains an **end-entity certificate** (WRPAC issued to Wallet Relying Party)
- These are **different certificates in the chain** - direct matching is impossible

**Conclusion:** PKIX is the ONLY appropriate validation method for EU WRPAC Providers List.

---

## 3.5 Compliance Requirements Summary for WRPAC Providers

| Requirement                     | Specification          | Clause                          | Requirement Level             |
|---------------------------------|------------------------|---------------------------------|-------------------------------|
| WRPAC Provider is a CA          | ETSI TS 119 411-8      | Access Certificate Policy       | MUST                          |
| WRPAC is end-entity certificate | ETSI TS 119 475        | Clause 4.3, Annex E             | MUST                          |
| LoTE contains WRPAC Provider CA | ETSI TS 119 602        | Annex F, Table F.3              | MUST                          |
| x5c excludes trust anchor       | HAIP v1                | OpenID4VCI & OpenID4VP profiles | MUST                          |
| Validation method               | Inferred from above    | N/A                             | **PKIX ONLY**                 |
| Key Usage for WRPAC             | RFC 5280               | 4.2.1.3                         | digitalSignature              |
| JWT use cases                   | OpenID4VCI & OpenID4VP | N/A                             | Both require WRPAC validation |

---

## 3.6 Comparison: PID/Wallet vs WRPAC

| Aspect                        | PID/Wallet Providers                                    | WRPAC Providers                                                                    |
|-------------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------|
| **What they sign**            | PID data / Wallet attestation                           | Access certificates (to Relying Parties)                                           |
| **Provider certificate type** | End-entity                                              | CA certificate                                                                     |
| **LoTE contains**             | End-entity certificate (signing cert)                   | CA certificate (WRPAC Provider trust anchor)                                       |
| **x5c contains**              | End-entity certificate (same as LoTE)                   | End-entity certificate (WRPAC, different from LoTE)                                |
| **Trust anchor in x5c?**      | NO (per HAIP v1)                                        | NO (per HAIP v1)                                                                   |
| **Validation method**         | **Direct Trust** (certificate match)                    | **PKIX** (path validation)                                                         |
| **Chain length**              | 1 (direct match)                                        | 2+ (path from WRPAC to LoTE CA)                                                    |
| **JWT use cases**             | 1. PID attestation (SD-JWT-VC)<br>2. Wallet attestation | 1. Credential Issuer Metadata (OpenID4VCI)<br>2. Authorization Request (OpenID4VP) |

---

## 3.7 Open Questions for Further Investigation

1. **Certificate Profile:** What specific certificate profile applies to WRPAC Provider certificates (since ETSI TS 119
   412-6 does not cover them)?

2. **Intermediate CAs:** Can there be intermediate CAs between the LoTE trust anchor and the WRPAC Provider certificate?

3. **x5c Structure:** Should `x5c` contain only the WRPAC Provider certificate, or should it include intermediate
   certificates up to (but not including) the LoTE trust anchor?

4. **National Variations:** Are there national implementations that deviate from the EU WRPAC Providers List profile?

---

# WRPRC Providers

## 4.1 WRPRC Nature and Format (ETSI TS 119 475)

#### 4.1.1 Critical Distinction: WRPRC is NOT an X.509 Certificate

**Finding 4.1.1:** Unlike WRPAC (which is an X.509 end-entity certificate), **WRPRC is a JWT (JSON Web Token) or CWT (
CBOR Web Token) attestation**.

Per ETSI TS 119 475, Clause 5.2.1:

> "**GEN-5.2.1-01:** The WRPRC shall be formatted as signed JSON Web Token (JWT) [6] or CBOR Web Token (CWT) [7]."

> "**GEN-5.2.1-03:** The WRPRC shall be signed with the digital signature of provider of the wallet-relying party
> registration certificates."

#### 4.1.2 WRPRC Purpose

Per ETSI TS 119 475, Clause 4.4:

> "WRPRCs are structured data objects that describe the **intended use and attribute access scope** of a WRP registered
> in a national register. They serve as a **transparency mechanism**, enabling wallet users to understand what
> information
> a WRP is allowed to request and under which legal or functional entitlement."

**Finding 4.1.2:** WRPRC answers "**What can you do?**" (authorization/entitlements), while WRPAC answers "**Who are
you?**" (authentication).

#### 4.1.3 Dual-Layer Trust Framework

Per ETSI TS 119 475, Clause 4.5:

> "WRPAC and WRPRC serve distinct but complementary purposes within the EUDIW ecosystem. While the **WRPAC ensures WRP
authentication**, the **WRPRC conveys the WRP's declared use cases and data access policies** to both the EUDIW and the
> end user."

> "Together, they form a **dual-layer trust framework**, where the WRPAC guarantees the WRP's identity and
> authentication, and the WRPRC ensures entitlements, transparency and data minimization."

| Certificate | Purpose                    | Answers            | Format           |
|-------------|----------------------------|--------------------|------------------|
| **WRPAC**   | Authentication             | "Who are you?"     | X.509 end-entity |
| **WRPRC**   | Authorization/Entitlements | "What can you do?" | JWT attestation  |

#### 4.1.4 WRPRC JWT Header Requirements

Per ETSI TS 119 475, Table 5:

| Header Field | Value               | Description                                                |
|--------------|---------------------|------------------------------------------------------------|
| **typ**      | `rc-wrp+jwt`        | Specifies the type of the Web Token                        |
| **alg**      | [algorithm]         | Algorithm used to sign the JWT (per ETSI TS 119 182-1)     |
| **x5c**      | [certificate chain] | Contains the certificate chain to verify the JWT signature |
| **b64**      | `"true"`            | WRPRC is serialized in compact form                        |
| **cty**      | `"b64"`             | Content type                                               |

**Finding 4.1.4:** The WRPRC JWT header contains an `x5c` field with the certificate chain needed to verify the WRPRC
Provider's signature.

---

## 4.2 LoTE ServiceDigitalIdentity Requirements (ETSI TS 119 602 Annex G)

#### 4.2.1 Annex G Scope (EU WRPRC Providers List Profile)

> "The present Annex specifies a LoTE profile aimed at supporting the publication by the European Commission of a list
> of providers of wallet relying party registration certificates..."

**Finding 4.2.1:** Annex G defines the **EU WRPRC Providers List** profile specifically.

#### 4.2.2 Table G.3: Service Digital Identity for WRPRC Providers

> "The ServiceDigitalIdentity component shall contain one or more X.509 certificates that can be used to verify the
> signature or seal created by the provider of wallet-relying party registration certificates **on the registration
certificate it provides to wallet-relying parties**..."

**Critical Finding 4.2.2:** The LoTE contains the **WRPRC Provider's CA certificate(s)** (X.509), NOT an end-entity
signing certificate.

**Analysis: Table G.3 vs Table F.3 Wording**

| Aspect             | Table F.3 (WRPAC)                                               | Table G.3 (WRPRC)                                                     |
|--------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| **Wording**        | "verify the signature... on the access certificate it provides" | "verify the signature... on the registration certificate it provides" |
| **Pattern**        | Identical structure                                             | Identical structure                                                   |
| **Interpretation** | LoTE contains CA cert (WRPAC Provider)                          | LoTE contains CA cert (WRPRC Provider)                                |

**Key Evidence for CA Certificate in LoTE:**

1. **Identical Wording Pattern:** Table G.3 uses the same "verify the signature... on the [certificate type] it
   provides" pattern as Table F.3 (WRPAC), which explicitly contains a CA certificate.

2. **"Whole Certificate Chain" in x5c:** ETSI TS 119 475 Table 5 states the `x5c` header field "contains the **whole**
   certificate chain to verify the JWT." The word "whole" implies multiple certificates (end-entity → intermediate →
   CA), not just a single end-entity certificate.

3. **PKIX Validation Required:** If the LoTE contained only the end-entity signing certificate (Direct Trust), the `x5c`
   would only need to contain that single certificate. The requirement for "the whole certificate chain" indicates PKIX
   path validation is expected.

4. **WRPRC Provider as CA:** The WRPRC Provider signs WRPRC JWT attestations. If the Provider uses an intermediate CA
   internally (common practice), the LoTE would contain the CA certificate, and the `x5c` would contain the chain from
   the end-entity signing certificate to that CA.

**Conclusion:** The WRPRC Providers LoTE contains a **CA certificate** (WRPRC Provider), and validation requires **PKIX
** (building the chain from the `x5c` end-entity certificate to the LoTE CA). This is the same pattern as WRPAC
Providers (Annex F).

**Certificate Chain Structure for WRPRC:**

```
┌─────────────────────────────────┐
│  WRPRC Provider CA Certificate  │
│  (PUBLISHED IN LoTE)            │
│  - basicConstraints: cA=TRUE    │
│  - Trust Anchor                 │
└─────────────────────────────────┘
         ▲
         │ Verified by PKIX
         │
┌─────────────────────────────────┐
│  Intermediate CA (optional)     │
│  - basicConstraints: cA=TRUE    │
│  - Included in x5c              │
└─────────────────────────────────┘
         ▲
         │ Issues
         │
┌─────────────────────────────────┐
│  WRPRC Signing Certificate      │
│  (End-Entity)                   │
│  - basicConstraints: cA=FALSE   │
│  - Key Usage: digitalSignature  │
│  - Included in x5c (first cert) │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  WRPRC JWT                      │
│  - Header: { x5c: [...] }       │
│  - Payload: { intended_use,     │
│               entitlements }    │
│  - Signature                    │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: WRPRC Provider CA certificate
Validation Method: PKIX (chain from x5c end-entity to LoTE CA), then JWT signature verification
```

#### 4.2.3 Comparison: WRPAC vs WRPRC LoTE

| Aspect                  | WRPAC Providers (Annex F)      | WRPRC Providers (Annex G)             |
|-------------------------|--------------------------------|---------------------------------------|
| **What LoTE contains**  | WRPAC Provider CA certificate  | WRPRC Provider CA certificate         |
| **What is verified**    | WRPAC X.509 certificate        | WRPRC JWT `x5c` chain                 |
| **Verification target** | End-entity certificate (WRPAC) | Certificate chain in JWT header       |
| **Validation method**   | PKIX chain validation          | PKIX chain validation + JWT signature |
| **LoTE cert type**      | CA certificate                 | CA certificate                        |
| **x5c content**         | End-entity cert (WRPAC)        | End-entity → Intermediate → CA chain  |

**Finding 4.2.3:** Both LoTEs contain **CA certificates** and use **PKIX validation**:

- **WRPAC LoTE** → Validates X.509 certificate chain (WRPAC end-entity → LoTE CA)
- **WRPRC LoTE** → Validates JWT `x5c` chain (WRPRC signing cert → LoTE CA), then verifies JWT signature

---

## 4.3 WRPRC Use Cases

#### 4.3.1 Credential Issuer (OpenID4VCI)

**Context:** A Wallet Relying Party acting as a Credential Issuer includes WRPRC in its Credential Issuer Metadata.

**Per ETSI TS 119 472-3 (to be verified):**

- WRPRC JWTs included in `issuer_info` claim
- Wallet verifies WRPRC signature against WRPRC Providers LoTE
- WRPRC expresses: **What credentials the issuer can issue**

```json
{
  "issuer_info": {
    "wrprc": [
      "eyJhbGciOiJFUzI1NiIsInR5cCI6InJjLXdycCtqdHQiLCJ4NWMiOltdfQ...",
      "eyJhbGciOiJFUzI1NiIsInR5cCI6InJjLXdycCtqdHQiLCJ4NWMiOltdfQ..."
    ]
  }
}
```

#### 4.3.2 Verifier (OpenID4VP)

**Context:** A Wallet Relying Party acting as a Verifier includes WRPRC in the Authorization Request.

**Per ETSI TS 119 472-2 (to be verified):**

- WRPRC JWTs included in `verifier_info` parameter
- Wallet verifies WRPRC signature against WRPRC Providers LoTE
- WRPRC expresses: **What attributes the verifier may request and for which purpose**

```
GET /authorize?
  response_type=vp_token
  &verifier_info=eyJhbGciOiJFUzI1NiIsInR5cCI6InJjLXdycCtqdHQiLCJ4NWMiOltdfQ...
  &...
```

#### 4.3.3 WRPRC and WRPAC Relationship

Per ETSI TS 119 475, Clause 6.2.2.2:

> "**REG-6.2.2.2-03:** The WRPRC provider shall ensure that at the time of issuance WRP holds at least one WRPAC and the
> WRPAC certificate is valid."

**Finding 4.3.3:** A WRP must have a **valid WRPAC** before it can obtain a WRPRC. This ensures:

1. **Authentication first** (WRPAC proves identity)
2. **Authorization second** (WRPRC declares entitlements)

---

## 4.4 Validation Method for EU WRPRC Providers List - Issuance Service

This section covers using the WRPRC Providers List with **service type Issuance** (`http://uri.etsi.org/19602/SvcType/WRPRC/Issuance`) to validate the WRPRC JWT signature via PKIX certificate chain validation.

**Note:** For Token Status List validation (WRPRC revocation checking), see Section 4.5.

#### 4.4.1 WRPRC JWT Verification Flow

```
┌─────────────────────────────────┐
│  EU WRPRC Providers LoTE        │
│  (WRPRC Provider CA Certificate)│
│  - X.509 CA certificate         │
│  - basicConstraints: cA=TRUE    │
│  - Trust Anchor                 │
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         ▲
         │ PKIX chain validation
         │
┌─────────────────────────────────┐
│  WRPRC JWT x5c Chain            │
│  - [0]: End-entity signing cert │
│  - [1]: Intermediate CA (opt)   │
│  - basicConstraints: cA=TRUE    │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  WRPRC JWT                      │
│  - Header: { typ: rc-wrp+jwt,   │
│              alg: ES256,        │
│              x5c: [...] }       │
│  - Payload: {                   │
│      intended_use: "...",       │
│      entitlements: [...],       │
│      data_policies: [...]       │
│    }                            │
│  - Signature                    │
└─────────────────────────────────┘
         │
         │ Included in
         ▼
┌─────────────────────────────────┐
│  OpenID4VCI: issuer_info        │
│  OpenID4VP: verifier_info       │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: WRPRC Provider CA certificate (X.509)
Validation Method: PKIX (x5c chain → LoTE CA), then JWT signature verification (JWS per RFC 7515)
```

#### 4.4.2 Validation Steps

WRPRC JWT validation requires two phases:

**Phase 1: Certificate Chain Validation (PKIX)**

1. Parse WRPRC JWT header
2. Verify `typ` is `rc-wrp+jwt`
3. Extract `x5c` certificate chain from header
4. Load trust anchors from EU WRPRC Providers LoTE (CA certificates)
5. Build certification path from `x5c` end-entity certificate to LoTE CA
6. Validate chain using PKIX (RFC 5280 Section 6.3):
    - Verify signatures along the chain
    - Verify validity periods
    - Verify basicConstraints (cA=TRUE for intermediate CA)
    - Verify keyUsage (keyCertSign for CA, digitalSignature for end-entity)
    - Check revocation status (CRL/OCSP)

**Phase 2: JWT Signature Verification**

7. Extract validated end-entity signing certificate from `x5c`
8. Verify JWT signature using the certificate's public key (RFC 7515)
9. Parse and validate WRPRC payload claims:
    - `intended_use`: Declared purpose of WRP
    - `entitlements`: Data access permissions
    - `data_policies`: Data handling commitments

#### 4.4.3 Why This is Different from WRPAC

| Aspect                 | WRPAC Validation                          | WRPRC Validation                         |
|------------------------|-------------------------------------------|------------------------------------------|
| **What is validated**  | X.509 certificate (WRPAC)                 | JWT attestation (WRPRC)                  |
| **LoTE contains**      | CA certificate                            | CA certificate                           |
| **LoTE validates**     | Certificate chain (PKIX)                  | Certificate chain (PKIX) + JWT signature |
| **x5c location**       | In JWT signing Credential Issuer Metadata | In WRPRC JWT header                      |
| **x5c content**        | End-entity cert (WRPAC)                   | End-entity → Intermediate → CA chain     |
| **Trust relationship** | Direct (WRPAC → LoTE CA)                  | Indirect (WRPRC signing cert → LoTE CA)  |
| **Validation phases**  | Single (PKIX)                             | Dual (PKIX + JWS)                        |
| **Outcome**            | "WRP is authenticated"                    | "WRP is authorized for X"                |

**Finding 4.4.3:** Both WRPAC and WRPRC use **PKIX certificate chain validation** against LoTE CA certificates. The key
difference is that WRPRC adds a **second phase** (JWT signature verification) because the WRPRC itself is a JWT
attestation, not an X.509 certificate.

---

## 4.5 Token Status List Validation - Revocation Service

ETSI TS 119 475 v1.2.1 (March 2026) introduces a **Token Status List (TSL)** mechanism for WRPRC revocation checking. This section covers using the WRPRC Providers List with **service type Revocation** (`http://uri.etsi.org/19602/SvcType/WRPRC/Revocation`) to validate the Token Status List signature.

### 4.5.1 IETF Token Status List Format

Per IETF draft-ietf-oauth-status-list, the `status` claim in the JWT/CWT payload contains a `status_list` object:

```json
{
  "status": {
    "status_list": {
      "uri": "https://wrprc-provider.example/status-list",
      "index": 12345
    }
  }
}
```

**Key Points:**
- **Single claim**: `status` (not separate `status_list_uri` and `status_list_index`)
- **Object structure**: `status_list` is an object with two properties:
  - `uri`: URI to fetch the Token Status List
  - `index`: Bit position in the status list bitstring
- **Privacy**: Each WRPRC MUST have a unique, unpredictable index (per draft-ietf-oauth-status-list Section 13.2)

**Reference:** IETF draft-ietf-oauth-status-list, Section 5 ("Status Claim")

### 4.5.2 WRPRC Providers List - Revocation Service

Per ETSI TS 119 602 Annex G Table G.3, the WRPRC Providers List supports TWO service types:

| Service Type Identifier | Purpose |
|------------------------|---------|
| `http://uri.etsi.org/19602/SvcType/WRPRC/Issuance` | Validate WRPRC JWT signature (Section 4.4) |
| `http://uri.etsi.org/19602/SvcType/WRPRC/Revocation` | Validate Token Status List signature (this section) |

**ServiceDigitalIdentity for Revocation Service:**

> "The ServiceDigitalIdentity component shall contain one or more X.509 certificates that can be used to verify the signature or seal created by the provider of wallet-relying party registration certificates **on the Token Status List**..."

**Critical Finding:** The LoTE (Revocation service) contains a **CA certificate** for validating the Token Status List signature, following the same pattern as the Issuance service.

### 4.5.3 Token Status List Validation Flow

```
┌─────────────────────────────────────────┐
│  EU WRPRC Providers LoTE                │
│  (Revocation Service)                   │
│  - WRPRC Provider CA Certificate        │
│  - basicConstraints: cA=TRUE            │
│  - Trust Anchor                         │
│  ★ PUBLISHED IN LoTE ★                  │
└─────────────────────────────────────────┘
         ▲
         │ PKIX chain validation
         │
┌─────────────────────────────────────────┐
│  Token Status List (TSL) x5c Chain      │
│  - [0]: End-entity signing cert         │
│  - [1]: Intermediate CA (opt)           │
│  - basicConstraints: cA=TRUE            │
└─────────────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────────────┐
│  Token Status List (JWT/CWT)            │
│  - Header: { typ: application/status+jwt, │
│              alg: ES256,                │
│              x5c: [...] }               │
│  - Payload: {                           │
│      status_list: {                     │
│        bits: "00000000...",             │
│        uri: "..."                       │
│      }                                  │
│    }                                    │
│  - Signature                            │
└─────────────────────────────────────────┘
         ▲
         │ Referenced by
         │
┌─────────────────────────────────────────┐
│  WRPRC JWT Payload                      │
│  - status: {                            │
│      status_list: {                     │
│        uri: "https://...",              │
│        index: 12345                     │
│      }                                  │
│    }                                    │
└─────────────────────────────────────────┘

LoTE ServiceDigitalIdentity (Revocation) contains: WRPRC Provider CA certificate (X.509)
Validation Method: PKIX (TSL x5c chain → LoTE CA), then TSL signature verification, then bit check
```

### 4.5.4 Validation Steps

Token Status List validation requires three phases:

**Phase 1: Extract TSL Reference from WRPRC**

1. Parse WRPRC JWT payload
2. Extract `status.status_list.uri` (TSL endpoint)
3. Extract `status.status_list.index` (bit position)

**Phase 2: Certificate Chain Validation (PKIX)**

4. Fetch Token Status List (JWT/CWT) from URI
5. Parse TSL header
6. Extract `x5c` certificate chain from header
7. Load trust anchors from EU WRPRC Providers LoTE (Revocation service - CA certificates)
8. Build certification path from `x5c` end-entity certificate to LoTE CA
9. Validate chain using PKIX (RFC 5280 Section 6.3):
    - Verify signatures along the chain
    - Verify validity periods
    - Verify basicConstraints (cA=TRUE for intermediate CA)
    - Verify keyUsage (keyCertSign for CA, digitalSignature for end-entity)
    - Check revocation status (CRL/OCSP)

**Phase 3: TSL Signature Verification and Status Check**

10. Extract validated end-entity signing certificate from `x5c`
11. Verify TSL signature using the certificate's public key
12. Parse TSL payload to extract status bitstring
13. Check bit at `status.status_list.index`:
    - **0 (valid)**: WRPRC is currently trusted
    - **1 (revoked)**: WRPRC has been revoked
14. Return revocation status

### 4.5.5 WRPRC Complete Validation Flow

Complete WRPRC validation now requires **three** validation steps:

```
1. WRPRC JWT x5c Chain Validation (PKIX)
   → Uses LoTE (Issuance service)
   → Validates WRPRC Provider's signature capability

2. WRPRC JWT Signature Verification (JWS)
   → Uses validated certificate from step 1
   → Validates WRPRC payload integrity

3. Token Status List Validation
   → Uses LoTE (Revocation service)
   → Checks WRPRC revocation status
   → Returns: VALID or REVOKED
```

**Finding 4.5.5:** The WRPRC Providers LoTE serves **dual purposes**:
- **Issuance service**: Validates WRPRC JWT signature (proves WRPRC authenticity)
- **Revocation service**: Validates Token Status List signature (proves WRPRC is not revoked)

Both services use PKIX certificate chain validation against CA certificates in the LoTE, but they validate **different certificates** (WRPRC signing cert vs TSL signing cert).

---

## 4.6 Compliance Requirements Summary for WRPRC Providers

| Requirement                      | Specification       | Clause             | Requirement Level        |
|----------------------------------|---------------------|--------------------|--------------------------|
| WRPRC format is JWT/CWT          | ETSI TS 119 475     | 5.2.1              | MUST                     |
| WRPRC signed by Provider         | ETSI TS 119 475     | 5.2.1              | MUST                     |
| WRPRC typ = rc-wrp+jwt           | ETSI TS 119 475     | Table 5            | MUST                     |
| WRPRC contains x5c (whole chain) | ETSI TS 119 475     | Table 5            | MUST                     |
| LoTE contains Provider CA cert   | ETSI TS 119 602     | Annex G, Table G.3 | MUST                     |
| WRP must have valid WRPAC        | ETSI TS 119 475     | 6.2.2.2            | MUST                     |
| Validation method                | Inferred from above | N/A                | **PKIX + JWT Signature** |

---

## 4.7 Comparison: All WRP Certificate Types

| Aspect                | WRPAC                               | WRPRC                           |
|-----------------------|-------------------------------------|---------------------------------|
| **Format**            | X.509 end-entity certificate        | JWT attestation                 |
| **Purpose**           | Authentication                      | Authorization/Entitlements      |
| **Answers**           | "Who are you?"                      | "What can you do?"              |
| **LoTE contains**     | WRPAC Provider CA                   | WRPRC Provider CA               |
| **Validation**        | PKIX chain validation               | PKIX (x5c) + JWT signature      |
| **Used in**           | JWT `x5c` (signing metadata)        | `issuer_info` / `verifier_info` |
| **Prerequisite**      | WRP registration                    | Valid WRPAC                     |
| **ETSI TS 119 411-8** | Applies (Access Certificate Policy) | Not applicable                  |
| **ETSI TS 119 475**   | Referenced                          | Defines format & requirements   |

---

## 4.8 Open Questions for Further Investigation

1. **ETSI TS 119 472-2/3 Verification:** Confirm exact placement of WRPRC in OpenID4VP Authorization Request (
   `verifier_info`) and OpenID4VCI Credential Issuer Metadata (`issuer_info`).
    - **Status:** These specifications are not in the RAG system; requires external verification.

2. **WRPRC Claims Structure:** What specific claims are included in WRPRC JWT payload (entitlements, intended_use,
   data_policies)?
    - **Status:** ETSI TS 119 475 defines the format but not the exact claim structure; may be implementation-specific.

3. **WRPRC Revocation - Token Status List:** ETSI TS 119 475 v1.2.1 introduces Token Status List for WRPRC revocation.
    - **Status:** **Answered by v1.2.1** - Token Status List per IETF draft-ietf-oauth-status-list
    - WRPRC contains `status.status_list` object with TSL URI and bit index
    - TSL is a JWT/CWT with its own `x5c` chain
    - WRPRC Providers LoTE (Revocation service) contains CA certificate for TSL validation
    - **Open:** TSL certificate architecture - does LoTE contain end-entity or CA for Revocation service?

4. **Multiple WRPRCs:** Can a WRP have multiple WRPRCs for different use cases? How does Wallet select the appropriate
   one?
    - **Status:** ETSI TS 119 475 allows multiple WRPRCs; selection mechanism may be implementation-specific.

5. **WRPRC Provider CA Certificate Profile:** What are the specific certificate profile requirements for WRPRC Provider
   CA certificates in the LoTE?
    - **Status:** ETSI TS 119 412-6 does not cover WRPRC Providers; may follow ETSI EN 319 412-2/3 or ETSI TS 119 411-8.
    - **Note:** Section 4.2.2 clarifies that LoTE contains CA certificate (not end-entity), based on Table G.3 wording
      pattern matching Table F.3 (WRPAC).

---

## Implementation Guidance

### IG.1 Summary of Validation Methods

| Provider List        | Validation Methods | Specification Status           |
|----------------------|--------------------|--------------------------------|
| **PID Providers**    | Direct Trust AND PKIX | Ambiguous (Annex D)         |
| **Wallet Providers** | Direct Trust AND PKIX | Ambiguous (Annex E)         |
| **WRPAC Providers**  | PKIX                | Clear (Annex F)                |
| **WRPRC Providers (Issuance)**  | PKIX + JWT Signature | Clear (Annex G + TS 119 475 v1.2.1) |
| **WRPRC Providers (Revocation)** | PKIX + TSL Signature | Clear (TS 119 475 v1.2.1) |

### IG.2 Recommended Implementation Strategy

#### IG.2.1 For PID and Wallet Providers

The specification allows both Direct Trust and PKIX validation. Implementations should:

1. **Support Both Validation Methods**
   - Direct Trust: For deployments where LoTE contains end-entity certificates
   - PKIX: For deployments where LoTE contains CA certificates
   - Both options are valid per the specification

2. **Log Validation Method**
   - Record which method was used (Direct Trust vs PKIX)
   - Include in audit trails for compliance
   - Helps identify specification interpretation patterns

3. **Monitor for Clarification**
   - Track ETSI corrigenda or new revisions
   - Monitor national implementation guidance
   - Adjust implementation based on clarified requirements

#### IG.2.2 For WRPAC and WRPRC Providers

Specification is clear - use PKIX validation:

1. **WRPAC Validation**
   - Extract WRPAC certificate from JWT `x5c`
   - Build path from WRPAC to LoTE trust anchor
   - Verify WRPAC is end-entity (cA=FALSE)
   - Verify LoTE certificate is CA (cA=TRUE)

2. **WRPRC Validation (Issuance Service)**
   - Extract certificate chain from WRPRC JWT `x5c`
   - Build path from `x5c` end-entity to LoTE trust anchor (Issuance service)
   - Verify WRPRC JWT signature using validated certificate
   - Two-step process: PKIX then JWT signature verification

3. **WRPRC Token Status List Validation (Revocation Service) - NEW in v1.2.1**
   - Extract `status.status_list.uri` and `status.status_list.index` from WRPRC payload
   - Fetch Token Status List (JWT/CWT) from URI
   - Extract certificate chain from TSL `x5c`
   - Build path from TSL `x5c` end-entity to LoTE trust anchor (Revocation service)
   - Verify TSL signature using validated certificate
   - Check bit at `status.status_list.index`: 0 = valid, 1 = revoked
   - Three-step process: PKIX + TSL signature + bit check

### IG.3 Error Handling and Reporting

#### IG.3.1 Validation Failure Categories

| Failure Type              | Action                                    | Log Level |
|---------------------------|-------------------------------------------|-----------|
| Direct Trust mismatch     | Try PKIX                                  | DEBUG     |
| PKIX path building fails  | Report validation failure                 | WARN      |
| Certificate expired       | Report validation failure                 | WARN      |
| Revocation check fails    | Report validation failure (if required)   | WARN      |
| QCStatement missing       | Report constraint violation               | WARN      |
| Wrong key usage           | Report constraint violation               | WARN      |

#### IG.3.2 Validation Method Logging

For PID/Wallet validation, log which method succeeded:

```kotlin
when (validationMethod) {
    DIRECT_TRUST -> logger.debug("Validated via Direct Trust (subject+serial match)")
    PKIX -> logger.debug("Validated via PKIX (LoTE contained CA certificate)")
}
```

### IG.4 Testing Recommendations

#### IG.4.1 Test Scenarios for PID/Wallet Providers

1. **Direct Trust Success**
   - LoTE contains end-entity certificate
   - `x5c` contains same end-entity certificate
   - Expected: Direct Trust validation succeeds

2. **PKIX Success**
   - LoTE contains CA certificate
   - `x5c` contains end-entity issued by CA
   - Expected: PKIX validation succeeds

3. **Validation Failure**
   - LoTE contains unrelated certificate
   - `x5c` contains untrusted certificate
   - Expected: Both Direct Trust and PKIX fail

#### IG.4.2 Test Scenarios for WRPAC/WRPRC Providers

1. **WRPAC PKIX Success**
   - LoTE contains WRPAC Provider CA
   - `x5c` contains WRPAC issued by CA
   - Expected: PKIX validation succeeds

2. **WRPRC Two-Step Success**
   - LoTE contains WRPRC Provider CA
   - WRPRC `x5c` contains chain to CA
   - Expected: PKIX + JWT signature validation succeed

### IG.5 Specification Feedback

If you encounter issues or ambiguities in implementation:

1. **Document the Issue**
   - Specification reference (Annex, clause, table)
   - Actual behavior observed
   - Expected behavior based on interpretation

2. **Report Through Appropriate Channels**
   - National authorities (for national implementations)
   - ETSI technical committee (for specification issues)
   - EUDI Wallet reference implementation maintainers

3. **Share with Community**
   - Contribute to open-source implementations
   - Participate in EUDI Wallet working groups
   - Help clarify specification through practical experience

---

# Pub-EAA Providers and EU Pub-EAA Providers List (To Be Added)

---

## References

### ETSI Specifications

- **ETSI TS 119 412-6 V1.1.1**: "Certificate profile requirements for PID, Wallet, EAA, QEAA, and PSBEAA providers"
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

### IETF RFCs

- **RFC 5280**: "Internet X.509 Public Key Infrastructure Certificate and CRL Profile"
- **RFC 7515**: "JSON Web Signature (JWS)"
- **RFC 9901**: "SD-JWT (Selective Disclosure for JWT)"

### IETF Internet-Drafts

- **draft-ietf-oauth-status-list**: Looker, T., Bastian, P., and C. Bormann, "Token Status List (TSL)", Work in Progress, Internet-Draft, draft-ietf-oauth-status-list-18, 18 February 2026, <https://datatracker.ietf.org/doc/html/draft-ietf-oauth-status-list-18>

---

**Disclaimer:** This analysis is based on publicly available ETSI specifications and should be validated against actual
implementations and national requirements.
