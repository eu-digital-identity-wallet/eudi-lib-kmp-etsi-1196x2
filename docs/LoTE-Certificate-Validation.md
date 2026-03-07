# Certificate Chain Validation Using EU Provider Lists (LoTE) for EUDI Wallet Attestations

**Document Version:** 4.4
**Date:** 2026-03-04
**Purpose:** Analysis of ETSI specifications for certificate chain validation against EU Provider Lists (LoTE) serving
as trust anchor sources for PID, Wallet, WRPAC, and WRPRC providers

**Version 4.4 Changes:**

- Updated improvement status: AIA validation for PID/Wallet providers is now implemented and integrated.
- Standardized OIDs and fixed Wallet Provider policy OID inconsistency.

---

## Executive Summary

This document analyzes ETSI specifications to understand how certificates found in EU Providers Lists (defined in ETSI
TS 119 602) can be used to validate certificate chains for attestations issued in SD-JWT-VC or JWT format.

### Key Findings

| Provider List        | ETSI TS 119 602 Annex | LoTE Certificate Type | What LoTE Validates        | Certificate Chain Validation |
|----------------------|-----------------------|-----------------------|----------------------------|------------------------------|
| **PID Providers**    | Annex D               | End-entity ONLY       | PID signing certificate    | **Direct Trust**             |
| **Wallet Providers** | Annex E               | End-entity ONLY       | Wallet signing certificate | **Direct Trust**             |
| **WRPAC Providers**  | Annex F               | CA (WRPAC Provider)   | WRPAC X.509 certificate    | **PKIX**                     |
| **WRPRC Providers**  | Annex G               | CA (WRPRC Provider)¹  | WRPRC JWT `x5c` chain      | **PKIX**                     |

**Footnotes:**
¹ **WRPRC LoTE Certificate Type:** Table G.3 wording ("verify the signature... on the registration certificate it
provides") is identical to Table F.3 (WRPAC), indicating the LoTE contains a **CA certificate** (WRPRC Provider), not an
end-entity signing certificate. The WRPRC JWT `x5c` header contains "the whole certificate chain" (ETSI TS 119 475 Table
5), which builds via PKIX to the LoTE CA. See Section 4.2.2 for detailed analysis.

**Critical Distinction:**

- **PID/Wallet Providers**: Sign data directly → End-entity certificate in LoTE → Direct Trust validation (certificate
  match)
- **WRPAC Providers**: Issue X.509 certificates to Relying Parties → CA certificate in LoTE → PKIX validation (chain
  from WRPAC to LoTE CA)
- **WRPRC Providers**: Sign JWT attestations → CA certificate in LoTE → PKIX validation (chain from WRPRC `x5c` to LoTE
  CA), then JWT signature verification

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
- **LoTE** = Contains WRPRC Provider's CA certificate (trust anchor)
- **Certificate Chain Validation** = PKIX (chain from WRPRC `x5c` end-entity to LoTE CA)
- **JWT Signature Verification** = Uses validated end-entity certificate from `x5c` (separate step)

**Dual-Layer Trust Framework:**

- **WRPAC** = Authentication ("Who are you?") - X.509 certificate
- **WRPRC** = Authorization ("What can you do?") - JWT attestation
- WRP must have valid WRPAC before obtaining WRPRC

### HAIP v1 / OpenID4VCI v1 Context

- `x5c` in JWT header MUST NOT include trust anchor (per HAIP v1)
- For PID/Wallet: `x5c` contains end-entity, LoTE contains same end-entity → Direct match
- For WRPAC: `x5c` contains end-entity, LoTE contains CA → PKIX path validation required
- For WRPRC: `x5c` in WRPRC JWT header contains cert chain (end-entity → CA), LoTE contains CA → PKIX path validation
  required (then JWT signature verification)

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

**Critical Finding 1.2.2:** The wording explicitly states certificates are used to verify signatures **"on the person
identification data it provides"** - this indicates the **actual signing certificate** (end-entity), NOT a CA
certificate.

#### 1.2.3 Absence of CA Certificate Option

**Critical Observation:** Unlike Annex H (Pub-EAA Providers), Annex D **does NOT contain** the following NOTE:

> ~~"NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic
attestation of attributes, or it can be the X.509 certificate corresponding to a CA issuing such X.509 certificates
provided the other requirements applying to the present component are met."~~

**Finding 1.2.3:** The absence of this NOTE in Annex D (while present in Annex H) indicates that **CA certificates are
NOT allowed** in the ServiceDigitalIdentity for EU PID Providers List.

#### 1.2.4 Summary: EU PID Providers LoTE Requirements (ETSI TS 119 602 Annex D)

| Aspect                       | Requirement                                      |
|------------------------------|--------------------------------------------------|
| **Certificate Type in LoTE** | End-entity ONLY (the actual signing certificate) |
| **CA Certificates Allowed?** | **NO** - Annex D does not permit CA certificates |
| **Purpose**                  | Verify signatures on PID data directly           |
| **Validation Method**        | **Direct Trust ONLY**                            |

---

## 1.3 Reconciliation: Certificate Profile vs LoTE Profile

#### 1.3.1 Apparent Tension

| Specification                               | Allows                                                 |
|---------------------------------------------|--------------------------------------------------------|
| **ETSI TS 119 412-6 (Certificate Profile)** | Self-signed OR CA-issued end-entity certificates       |
| **ETSI TS 119 602 Annex D (LoTE Profile)**  | Only end-entity certificates in ServiceDigitalIdentity |

**Resolution:**

1. **PID Provider certificates** themselves CAN be CA-issued per ETSI TS 119 412-6 (the PID Provider may have an
   intermediate CA)
2. **BUT** the certificate published in the EU PID Providers LoTE MUST be the **end-entity signing certificate**, NOT
   the intermediate CA
3. This means **Direct Trust validation** is the ONLY method for EU PID Providers List

#### 1.3.2 Certificate Chain Structure for EU PID Providers

```
If PID Provider uses CA-issued model internally:

┌─────────────────────────────────┐
│  Intermediate CA Certificate    │
│  (NOT published in LoTE)        │
│  - basicConstraints: cA=TRUE    │
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
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  PID Attestation (SD-JWT-VC)    │
│  - x5c: [PID Provider Cert]     │
│  - (MAY include intermediate)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: PID Provider end-entity certificate ONLY
Validation Method: Direct Trust (certificate subject + serial match)
```

**Key Point:** Even if the PID Provider internally uses a CA-issued certificate model, the **end-entity certificate
itself** is published in the LoTE, not the issuing CA.

---

## 1.4 Validation Method for EU PID Providers List

#### 1.4.1 Direct Trust Validation

Since the LoTE contains the end-entity signing certificate (same as the one in the JWT `x5c`), validation is performed
by direct certificate matching:

1. Extract the first certificate from JWT `x5c` header
2. Load trust anchors from EU PID Providers LoTE ServiceDigitalIdentity
3. Match certificate by subject name and serial number
4. Verify validity period (notBefore, notAfter)
5. Verify revocation status (if CRL/OCSP available)
6. Verify key usage (digitalSignature bit)

#### 1.4.2 Why PKIX is NOT Applicable

PKIX validation requires:

1. A certification path from end-entity to trust anchor
2. The trust anchor to be a CA certificate

For EU PID Providers List:

- The LoTE contains the **end-entity certificate itself** (not a CA)
- There is no certification path to build
- PKIX validation would degenerate to direct certificate matching

**Conclusion:** Direct Trust is the ONLY appropriate validation method for EU PID Providers List.

---

## 1.5 Compliance Requirements Summary for PID Providers

| Requirement                      | Specification       | Clause                    | Requirement Level     |
|----------------------------------|---------------------|---------------------------|-----------------------|
| PID certificates are end-entity  | ETSI TS 119 412-6   | Scope                     | MUST                  |
| Self-signed OR CA-issued allowed | ETSI TS 119 412-6   | PID-4.2-01                | MAY (issuer choice)   |
| AIA if CA-issued                 | ETSI TS 119 412-6   | PID-4.4.3-01              | MUST (conditional)    |
| QCStatement with id-etsi-qct-pid | ETSI TS 119 412-6   | PID-4.5-01                | MUST                  |
| Certificate Policy present       | EN 319 412-2        | §4.3.3                    | MUST                  |
| LoTE contains end-entity cert    | ETSI TS 119 602     | Annex D, Table D.3        | MUST                  |
| CA certificates NOT in LoTE      | ETSI TS 119 602     | Annex D (absence of NOTE) | MUST NOT              |
| Validation method                | Inferred from above | N/A                       | **Direct Trust ONLY** |

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

**Critical Finding 2.2.2:** The wording explicitly states certificates are used to "authenticate and validate the
components of the wallet unit" - this indicates the **actual signing certificate** (end-entity), NOT a CA certificate.

#### 2.2.3 Absence of CA Certificate Option

**Critical Observation:** Unlike Annex H (Pub-EAA Providers), Annex E **does NOT contain** the following NOTE:

> ~~"NOTE: This can be the X.509 certificate corresponding to the private key used to sign or seal the electronic
attestation of attributes, or it can be the X.509 certificate corresponding to a CA issuing such X.509 certificates
provided the other requirements applying to the present component are met."~~

**Finding 2.2.3:** The absence of this NOTE in Annex E (while present in Annex H) indicates that **CA certificates are
NOT allowed** in the ServiceDigitalIdentity for EU Wallet Providers List.

#### 2.2.4 Summary: EU Wallet Providers LoTE Requirements (ETSI TS 119 602 Annex E)

| Aspect                       | Requirement                                      |
|------------------------------|--------------------------------------------------|
| **Certificate Type in LoTE** | End-entity ONLY (the actual signing certificate) |
| **CA Certificates Allowed?** | **NO** - Annex E does not permit CA certificates |
| **Purpose**                  | Authenticate and validate wallet unit components |
| **Validation Method**        | **Direct Trust ONLY**                            |

---

## 2.3 Reconciliation: Certificate Profile vs LoTE Profile

#### 2.3.1 Apparent Tension

| Specification                               | Allows                                                 |
|---------------------------------------------|--------------------------------------------------------|
| **ETSI TS 119 412-6 (Certificate Profile)** | Self-signed OR CA-issued end-entity certificates       |
| **ETSI TS 119 602 Annex E (LoTE Profile)**  | Only end-entity certificates in ServiceDigitalIdentity |

**Resolution:**

1. **Wallet Provider certificates** themselves CAN be CA-issued per ETSI TS 119 412-6 (the Wallet Provider may have an
   intermediate CA)
2. **BUT** the certificate published in the EU Wallet Providers LoTE MUST be the **end-entity signing certificate**, NOT
   the intermediate CA
3. This means **Direct Trust validation** is the ONLY method for EU Wallet Providers List

#### 2.3.2 Certificate Chain Structure for EU Wallet Providers

```
If Wallet Provider uses CA-issued model internally:

┌─────────────────────────────────┐
│  Intermediate CA Certificate    │
│  (NOT published in LoTE)        │
│  - basicConstraints: cA=TRUE    │
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
│  ★ PUBLISHED IN LoTE ★          │
└─────────────────────────────────┘
         │
         │ Signs
         ▼
┌─────────────────────────────────┐
│  Wallet Attestation (JWT)       │
│  - x5c: [Wallet Provider Cert]  │
│  - (MAY include intermediate)   │
└─────────────────────────────────┘

LoTE ServiceDigitalIdentity contains: Wallet Provider end-entity certificate ONLY
Validation Method: Direct Trust (certificate subject + serial match)
```

**Key Point:** Even if the Wallet Provider internally uses a CA-issued certificate model, the **end-entity certificate
itself** is published in the LoTE, not the issuing CA.

---

## 2.4 Validation Method for EU Wallet Providers List

#### 2.4.1 Direct Trust Validation

Since the LoTE contains the end-entity signing certificate (same as the one in the JWT `x5c`), validation is performed
by direct certificate matching:

1. Extract the first certificate from JWT `x5c` header
2. Load trust anchors from EU Wallet Providers LoTE ServiceDigitalIdentity
3. Match certificate by subject name and serial number
4. Verify validity period (notBefore, notAfter)
5. Verify QCStatement contains `id-etsi-qct-wal`
6. Verify revocation status (if CRL/OCSP available)
7. Verify key usage (digitalSignature bit)

#### 2.4.2 Why PKIX is NOT Applicable

PKIX validation requires:

1. A certification path from end-entity to trust anchor
2. The trust anchor to be a CA certificate

For EU Wallet Providers List:

- The LoTE contains the **end-entity certificate itself** (not a CA)
- There is no certification path to build
- PKIX validation would degenerate to direct certificate matching

**Conclusion:** Direct Trust is the ONLY appropriate validation method for EU Wallet Providers List.

---

## 2.5 Compliance Requirements Summary for Wallet Providers

| Requirement                        | Specification       | Clause                        | Requirement Level     |
|------------------------------------|---------------------|-------------------------------|-----------------------|
| Wallet certificates are end-entity | ETSI TS 119 412-6   | Scope (via WAL-5.1-01)        | MUST                  |
| Self-signed OR CA-issued allowed   | ETSI TS 119 412-6   | PID-4.2-01 (via WAL-5.1-01)   | MAY (issuer choice)   |
| AIA if CA-issued                   | ETSI TS 119 412-6   | PID-4.4.3-01 (via WAL-5.1-01) | MUST (conditional)    |
| QCStatement with id-etsi-qct-wal   | ETSI TS 119 412-6   | WAL-5.2-01                    | MUST                  |
| Certificate Policy present         | EN 319 412-2        | §4.3.3                        | MUST                  |
| LoTE contains end-entity cert      | ETSI TS 119 602     | Annex E, Table E.3            | MUST                  |
| CA certificates NOT in LoTE        | ETSI TS 119 602     | Annex E (absence of NOTE)     | MUST NOT              |
| Validation method                  | Inferred from above | N/A                           | **Direct Trust ONLY** |

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

## 4.4 Validation Method for EU WRPRC Providers List

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

## 4.5 Compliance Requirements Summary for WRPRC Providers

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

## 4.6 Comparison: All WRP Certificate Types

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

## 4.7 Open Questions for Further Investigation

1. **ETSI TS 119 472-2/3 Verification:** Confirm exact placement of WRPRC in OpenID4VP Authorization Request (
   `verifier_info`) and OpenID4VCI Credential Issuer Metadata (`issuer_info`).
    - **Status:** These specifications are not in the RAG system; requires external verification.

2. **WRPRC Claims Structure:** What specific claims are included in WRPRC JWT payload (entitlements, intended_use,
   data_policies)?
    - **Status:** ETSI TS 119 475 defines the format but not the exact claim structure; may be implementation-specific.

3. **WRPRC Revocation:** How are revoked WRPRCs handled? Is there a status list mechanism?
    - **Status:** Not specified in ETSI TS 119 475; may use JWT `exp` claim or external revocation list.

4. **Multiple WRPRCs:** Can a WRP have multiple WRPRCs for different use cases? How does Wallet select the appropriate
   one?
    - **Status:** ETSI TS 119 475 allows multiple WRPRCs; selection mechanism may be implementation-specific.

5. **WRPRC Provider CA Certificate Profile:** What are the specific certificate profile requirements for WRPRC Provider
   CA certificates in the LoTE?
    - **Status:** ETSI TS 119 412-6 does not cover WRPRC Providers; may follow ETSI EN 319 412-2/3 or ETSI TS 119 411-8.
    - **Note:** Section 4.2.2 clarifies that LoTE contains CA certificate (not end-entity), based on Table G.3 wording
      pattern matching Table F.3 (WRPAC).

---

# Pub-EAA Providers and EU Pub-EAA Providers List (To Be Added)

---

## References

### ETSI Specifications

- **ETSI TS 119 412-6 V1.1.1**: "Certificate profile requirements for PID, Wallet, EAA, QEAA, and PSBEAA providers"
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

---

**Disclaimer:** This analysis is based on publicly available ETSI specifications and should be validated against actual
implementations and national requirements.
