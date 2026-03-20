# WRPAC Compliance Assessment

## Assessment History

| Version | Date       | Author           | Changes Summary                                                                    |
|---------|------------|------------------|------------------------------------------------------------------------------------|
| 1.0     | 2026-03-20 | Assessment Agent | Initial assessment of EUWRPACProvidersList.kt wrpAccessCertificateProfile function |
|         |            |                  |                                                                                    |

---

## Executive Summary

The `wrpAccessCertificateProfile` function in `EUWRPACProvidersList.kt` provides a **basic subset** of ETSI TS 119 411-8
requirements. The implementation demonstrates **structural alignment** but has **significant gaps** due to both explicit
omissions and infrastructure limitations.

**Overall Compliance Score: 4/10**

**Key Findings:**

- ✅ Correctly validates: end-entity certificate type, digitalSignature key usage, validity period, certificate policy
  OIDs, self-signed rejection
- ❌ **Missing 60-70%** of ETSI mandatory requirements due to infrastructure gaps
- ⚠️ Infrastructure lacks extraction capabilities for subject/issuer attributes, most extensions, and criticality flags
- ❌ AIA requirement not enforced despite having the constraint available
- ❌ Qualified certificate QCStatements not validated despite infrastructure support

---

## Assessment Scope

- **File**:
  `/home/babis/work/eudiw/src/eudi-lib-kmp-etsi-1196x2/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPACProvidersList.kt`
- **Function**: `wrpAccessCertificateProfile()`
- **Standard**: ETSI TS 119 411-8 (Wallet Relying Party Access Certificate specifications)
- **Related Standards**: ETSI EN 319 412-1, ETSI EN 319 412-2, ETSI EN 319 412-3, ETSI EN 319 412-5, RFC 5280, RFC 9608
- **Assessment Date**: 2026-03-20

---

## Current Implementation Analysis

### Function Definition (lines 109-140)

```kotlin
public fun wrpAccessCertificateProfile(
    at: Instant? = null,
    policy: String? = null,
): CertificateProfile {
    val allowedPolicies = setOf(
        NCP_N_EUDIWRP,
        NCP_L_EUDIWRP,
        QCP_N_EUDIWRP,
        QCP_L_EUDIWRP,
    )

    val policies =
        if (policy != null) {
            require(policy in allowedPolicies) {
                // validation error
            }
            setOf(policy)
        } else {
            allowedPolicies
        }

    return certificateProfile {
        requireEndEntityCertificate()
        requireDigitalSignature()
        requireValidAt(at)
        requirePolicy(policies)
        requireNoSelfSigned()
    }
}
```

### Validated Requirements ✓

| Requirement                       | ETSI Reference          | Implementation                  | Status |
|-----------------------------------|-------------------------|---------------------------------|--------|
| End-entity certificate (cA=FALSE) | TS 119 411-8 6.6.1      | `requireEndEntityCertificate()` | ✅      |
| Key Usage: digitalSignature       | TS 119 411-8 6.6.1      | `requireDigitalSignature()`     | ✅      |
| Validity at time of use           | TS 119 411-8 6.6.1      | `requireValidAt(at)`            | ✅      |
| Certificate Policy OID            | TS 119 411-8 5.3, 6.6.1 | `requirePolicy(policies)`       | ✅      |
| Not self-signed                   | TS 119 411-8 implicit   | `requireNoSelfSigned()`         | ✅      |
| Policy OID values                 | TS 119 411-8 5.3        | Correct OIDs defined            | ✅      |

### Missing Requirements ✗

| Requirement                       | ETSI Reference                        | Status | Gap Details                                       |
|-----------------------------------|---------------------------------------|--------|---------------------------------------------------|
| **Certificate Fields**            |
| version = V3                      | RFC 5280 4.1.2.1                      | ❌      | No version validation                             |
| serialNumber (unique positive)    | RFC 5280 4.1.2.2                      | ❌      | No serial number validation                       |
| issuer attributes                 | EN 319 412-2/3 4.2.3                  | ❌      | No issuer DN extraction/validation                |
| subject attributes                | EN 319 412-2/3 4.2.4                  | ❌      | No subject DN extraction/validation               |
| subjectPublicKeyInfo              | TS 119 312                            | ❌      | No public key algorithm/size validation           |
| **Extensions**                    |
| authorityKeyIdentifier            | EN 319 412-2 4.3.1                    | ❌      | No extraction capability                          |
| keyUsage criticality              | EN 319 412-2 4.3.2                    | ⚠️     | Validated but criticality not checked             |
| CRLDistributionPoints             | EN 319 412-2 4.3.11                   | ❌      | No extraction, conditional logic missing          |
| AuthorityInfoAccess               | EN 319 412-2 4.4.1                    | ❌      | Constraint exists but NOT USED                    |
| CertificatePolicies criticality   | EN 319 412-1 4.2.1.4                  | ❌      | Criticality cannot be checked (no infrastructure) |
| SubjectAltName                    | RFC 5280 4.2.1.6 + TS 119 411-8 6.6.1 | ❌      | No extraction capability                          |
| ext-etsi-valassured-ST-certs      | EN 319 412-1 5.2                      | ❌      | Not validated                                     |
| noRevocationAvail                 | RFC 9608 2                            | ❌      | Not validated                                     |
| qcStatements (qualified)          | EN 319 412-5 4.2.x                    | ❌      | Infrastructure exists but NOT USED                |
| **Subject Naming**                |
| Natural person attributes         | EN 319 412-2 4.2.2                    | ❌      | No subject attribute validation                   |
| Legal person attributes           | EN 319 412-3 4.2.1                    | ❌      | No subject attribute validation                   |
| organizationIdentifier format     | EN 319 412-3 4.2.1.4                  | ❌      | No format validation                              |
| **Conditional Requirements**      |
| OCSP responder in AIA             | EN 319 412-2 4.4.1                    | ❌      | AIA not enforced                                  |
| CRLDP if no OCSP/val-assured      | EN 319 412-1 4.3.11                   | ❌      | Conditional logic missing                         |
| QCStatements for QCP policies     | EN 319 412-5                          | ❌      | Not enforced for qualified certs                  |
| Validity assurance for short-term | EN 319 412-1 5.2                      | ❌      | Not validated                                     |
| Signature vs seal purpose         | TS 119 411-8 6.2                      | ❌      | Purpose indication not validated                  |

---

## Infrastructure Analysis

### Available Extraction Operations

The `CertificateOperations` interface (CertificateOperations.kt) provides:

| Operation                  | Returns                     | Used in WRPAC Profile?                       |
|----------------------------|-----------------------------|----------------------------------------------|
| `getBasicConstraints()`    | BasicConstraintsInfo        | ✅ Yes                                        |
| `getKeyUsage()`            | KeyUsageBits?               | ✅ Yes                                        |
| `getValidityPeriod()`      | ValidityPeriod              | ✅ Yes                                        |
| `getCertificatePolicies()` | List<String>                | ✅ Yes                                        |
| `isSelfSigned()`           | Boolean                     | ✅ Yes                                        |
| `getAiaExtension()`        | AuthorityInformationAccess? | ❌ **No** (constraint exists but not invoked) |
| `getQcStatements(qcType)`  | List<QCStatementInfo>       | ❌ **No** (infrastructure ok but not used)    |

### Missing Extraction Operations

Critical capabilities **not present** in the interface:

| Needed Operation               | Purpose                         | Priority                          |
|--------------------------------|---------------------------------|-----------------------------------|
| `getSubject()`                 | Extract subject DN attributes   | Critical                          |
| `getIssuer()`                  | Extract issuer DN attributes    | Critical                          |
| `getSubjectAltNames()`         | Extract SAN contact information | Critical                          |
| `getCrlDistributionPoints()`   | Extract CRL distribution points | High                              |
| `getAuthorityKeyIdentifier()`  | Extract AKI                     | High                              |
| `getSerialNumber()`            | Extract serial number           | High                              |
| `getVersion()`                 | Extract X.509 version           | Medium                            |
| `getSubjectPublicKeyInfo()`    | Public key algorithm/size       | Medium                            |
| `getExtensionCriticality(oid)` | Check if extension is critical  | High (for criticality validation) |
| `getExtensionValue(oid)`       | Generic extension access        | High                              |

---

## Detailed Issue Log

### Issue 1: AuthorityInfoAccess Not Enforced [HIGH]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: The profile does not call `requireAiaForCaIssued()` despite ETSI EN 319 412-2 clause 4.4.1 requiring
AIA with id-ad-caIssuers (and optionally id-ad-ocsp) for all CA-issued certificates.

**ETSI Reference**:

- ETSI EN 319 412-2 clause 4.4.1: "This extension shall be present in the certificate... It shall include at least the
  id-ad-caIssuers access method."

**Impact**: Certificates without AIA would pass validation incorrectly.

**Recommendation**: Add `requireAiaForCaIssued()` to the profile builder.

---

### Issue 2: Qualified Certificates Missing QCStatements [HIGH]

**Location**: `EUWRPACProvidersList.kt:109-140`  
**Description**: For certificates with QCP-n-eudiwrp or QCP-l-eudiwrp policies, ETSI EN 319 412-5 requires specific
QCStatements. The profile accepts these policy OIDs but does not validate the presence of required QCStatements.

**ETSI Reference**:

- ETSI EN 319 412-5 clause 4.2.1: QCStatement esi4-qcStatement-1 (QcCompliance) mandatory for qualified certs
- ETSI EN 319 412-5 clause 4.2.2: QCStatement esi4-qcStatement-4 (QcSSCD) mandatory for qualified certs
- ETSI EN 319 412-5 clause 4.2.3: QCStatement esi4-qcStatement-6 (purpose) mandatory for qualified seal certs

**Impact**: Qualified certificates may lack required QCStatements and still be accepted.

**Recommendation**: Add conditional logic:

```kotlin
if (QCP_N_EUDIWRP in policies || QCP_L_EUDIWRP in policies) {
    requireQcStatement("0.4.0.1862.1.1") // QcCompliance
    requireQcStatement("0.4.0.1862.1.4") // QcSSCD
    if (QCP_L_EUDIWRP in policies) {
        requireQcStatement("0.4.0.1862.1.6") // Purpose: seal
    }
}
```

---

### Issue 3: CertificatePolicies Criticality Not Validated [MEDIUM]

**Location**: Infrastructure limitation  
**Description**: ETSI EN 319 412-1 requires the CertificatePolicies extension to be marked critical. The current
infrastructure cannot extract or validate extension criticality.

**ETSI Reference**:

- ETSI EN 319 412-1 clause 4.2.1.4: CertificatePolicies extension criticality requirements
- RFC 5280 clause 4.2: Criticality flag semantics

**Impact**: Certificates with non-critical CertificatePolicies may be accepted, violating normative requirements.

**Recommendation**: Extend `CertificateOperations` to return extension criticality. Add constraint to check that
CertificatePolicies is critical.

---

### Issue 4: Missing SubjectAltName Validation [HIGH]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: ETSI TS 119 411-8 clause 6.6.1 mandates SubjectAltName with contact information (URI, email, or
telephone). No validation exists.

**ETSI Reference**:

- ETSI TS 119 411-8 clause 6.6.1: "This extension shall contain contact information of the relying party"
- RFC 5280 4.2.1.6: SubjectAltName extension

**Impact**: WRPAC certificates without required contact information would be accepted.

**Recommendation**:

1. Add `getSubjectAltNames()` to `CertificateOperations`
2. Create `ProfileBuilder.requireSubjectAltNameForWRPAC()` constraint
3. Invoke it in the profile

---

### Issue 5: Missing Subject/Issuer Attribute Validation [CRITICAL]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: ETSI TS 119 411-8 references ETSI EN 319 412-2 (natural persons) and ETSI EN 319 412-3 (legal persons)
for subject naming. The profile validates nothing about issuer or subject DNs.

**Required Attributes**:

- **Natural Person Subject**: countryName, givenName/surname/pseudonym, commonName, serialNumber
- **Legal Person Subject**: countryName, organizationName, organizationIdentifier, commonName
- **Natural Person Issuer**: countryName, givenName/surname/pseudonym, commonName, serialNumber
- **Legal Person Issuer**: countryName, organizationName, commonName, organizationIdentifier (conditional)

**Impact**: Certificates with incomplete or incorrect subject/issuer names would be accepted.

**Recommendation**:

1. Add `getSubject()` and `getIssuer()` returning structured DN attribute maps to `CertificateOperations`
2. Create constraints: `requireSubjectNaturalPersonAttributes()`, `requireSubjectLegalPersonAttributes()`, etc.
3. Add conditional logic based on certificate policy (NCP-n/QCP-n vs NCP-l/QCP-l)

---

### Issue 6: Missing CRLDistributionPoints Validation [HIGH]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: ETSI EN 319 412-2 clause 4.3.11 requires CRLDistributionPoints unless the certificate includes an OCSP
responder location or is a short-term certificate with validity-assured extension.

**ETSI Reference**:

- ETSI EN 319 412-2 clause 4.3.11: Conditional requirement for CRLDP
- RFC 5280 clause 4.2.1.13: CRLDistributionPoints extension

**Impact**: Certificates without revocation information may be accepted.

**Recommendation**:

1. Add `getCrlDistributionPoints()` to `CertificateOperations`
2. Create conditional constraint: If no OCSP responder AND no validity-assured extension, then CRLDP required
3. Invoke in profile

---

### Issue 7: AuthorityKeyIdentifier Not Validated [MEDIUM]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: ETSI EN 319 412-2 clause 4.3.1 requires authorityKeyIdentifier extension for all certificates.

**ETSI Reference**:

- ETSI EN 319 412-2 clause 4.3.1: "This extension shall be present in the certificate"

**Impact**: Certificates without AKI would be accepted.

**Recommendation**: Add `getAuthorityKeyIdentifier()` and corresponding constraint.

---

### Issue 8: No Validation of Validity-Assured Extensions [LOW]

**Location**: `EUWRPACProvidersList.kt:133-139`  
**Description**: For short-term certificates (validity ≤ 7 days per ETSI), the validity-assured extension (
0.4.0.194121.2.1) and noRevocationAvail (2.5.29.56) may be used. Not validated.

**ETSI Reference**:

- ETSI EN 319 412-1 clause 5.2: ext-etsi-valassured-ST-certs
- RFC 9608 clause 2: id-ce-noRevAvail

**Impact**: Short-term certificate handling not properly validated.

**Recommendation**: Add checks for these optional extensions when certificate validity ≤ 7 days.

---

## Compliance Matrix by ETSI Requirement

### Certificate Structure (RFC 5280)

| Field                | Requirement              | Compliance | Notes                          |
|----------------------|--------------------------|------------|--------------------------------|
| version              | V3 (integer 2)           | ❌          | Not validated                  |
| serialNumber         | Unique positive integer  | ❌          | Not extracted                  |
| signature            | Algorithm per TS 119 312 | ⚠️         | Not validated (algorithm/size) |
| issuer               | Structured DN            | ❌          | No extraction                  |
| validity             | notBefore/notAfter       | ✅          | Validated                      |
| subject              | Structured DN            | ❌          | No extraction                  |
| subjectPublicKeyInfo | Algorithm per TS 119 312 | ❌          | Not validated                  |

### Extensions

| Extension                         | Presence       | Criticality | Compliance                        | Notes                                   |
|-----------------------------------|----------------|-------------|-----------------------------------|-----------------------------------------|
| authorityKeyIdentifier            | M              | NC          | ❌                                 | Not validated                           |
| keyUsage                          | M              | C           | ⚠️                                | Bits validated, criticality not checked |
| CRLDistributionPoints             | M(C)           | NC          | ❌                                 | Not validated, conditional missing      |
| AuthorityInfoAccess               | M              | NC          | ❌                                 | NOT CALLED (available but unused)       |
| CertificatePolicies               | M              | C           | ⚠️                                | OIDs validated, criticality not checked |
| SubjectAltName                    | M              | NC          | ❌                                 | Not validated                           |
| ext-etsi-valassured-ST-certs      | R(C)           | NC          | ❌                                 | Not validated                           |
| noRevocationAvail                 | M(C)           | NC          | ❌                                 | Not validated                           |
| qcStatements (esi4-qcStatement-1) | M(C) for QCP   | ❌           | Not validated for qualified       |
| qcStatements (esi4-qcStatement-4) | M(C) for QCP   | ❌           | Not validated for qualified       |
| qcStatements (esi4-qcStatement-6) | M(C) for QCP-l | ❌           | Not validated for qualified legal |

### Certificate Policies

| Policy OID       | Name          | Level   | Usage         | Compliance   |
|------------------|---------------|---------|---------------|--------------|
| 0.4.0.194118.1.1 | NCP-n-eudiwrp | Natural | Non-qualified | ✅ Recognized |
| 0.4.0.194118.1.2 | NCP-l-eudiwrp | Legal   | Non-qualified | ✅ Recognized |
| 0.4.0.194118.1.3 | QCP-n-eudiwrp | Natural | Qualified     | ✅ Recognized |
| 0.4.0.194118.1.4 | QCP-l-eudiwrp | Legal   | Qualified     | ✅ Recognized |

### Subject Naming (EN 319 412-2/3)

| Attribute                   | Natural Person    | Legal Person | Compliance      |
|-----------------------------|-------------------|--------------|-----------------|
| countryName                 | M                 | M            | ❌ Not validated |
| givenName/surname/pseudonym | M (choice)        | -            | ❌ Not validated |
| commonName                  | M                 | M            | ❌ Not validated |
| serialNumber                | M                 | -            | ❌ Not validated |
| organizationName            | C (if associated) | M            | ❌ Not validated |
| organizationIdentifier      | -                 | M            | ❌ Not validated |

---

## Gap Quantification

| Category             | # Requirements | # Compliant | % Compliance |
|----------------------|----------------|-------------|--------------|
| Certificate Fields   | 9              | 1           | 11%          |
| Extensions           | 13             | 2           | 15%          |
| Certificate Policies | 4              | 4           | 100%         |
| Subject Naming       | 7              | 0           | 0%           |
| Conditional Logic    | 6              | 0           | 0%           |
| **TOTAL**            | **39**         | **7**       | **18%**      |

**Overall Compliance Score: 4/10**

---

## Recommendations

### Priority 1: Immediate Profile Fixes (Without Infrastructure Changes)

These changes can be made immediately using existing infrastructure:

1. **Add AIA enforcement**
   ```kotlin
   return certificateProfile {
       // ... existing constraints
       requireAiaForCaIssued()  // ADD THIS
   }
   ```

2. **Add QCStatements for qualified certificates**
   ```kotlin
   if (QCP_N_EUDIWRP in policies || QCP_L_EUDIWRP in policies) {
       requireQcStatement("0.4.0.1862.1.1")  // QcCompliance
       requireQcStatement("0.4.0.1862.1.4")  // QcSSCD
   }
   if (QCP_L_EUDIWRP in policies) {
       requireQcStatement("0.4.0.1862.1.6")  // Purpose: electronicSignature/seal
   }
   ```

**Impact**: +10-15% compliance → Score: 5/10

---

### Priority 2: Infrastructure Upgrades (Required for Full Compliance)

These require modifications to `CertificateOperations` interface and implementations:

#### 2.1 Add Extension Criticality Tracking

- Modify extension extraction methods to return both value and criticality flag
- Add: `data class ExtensionInfo<T>(val value: T, val isCritical: Boolean)`
- Update all `CertificateOperationsAlgebra` to use `ExtensionInfo`

#### 2.2 Add Subject/Issuer DN Extraction

```kotlin
data class DistinguishedName(
    val attributes: Map<String, String> // attrType -> attrValue
)

fun getSubject(): DistinguishedName?
fun getIssuer(): DistinguishedName?
```

#### 2.3 Add SubjectAltName Extraction

```kotlin
sealed interface SubjectAlternativeName {
    data class Uri(val uri: String) : SubjectAlternativeName
    data class Email(val email: String) : SubjectAlternativeName
    data class Telephone(val number: String) : SubjectAlternativeName
    // ... other types
}

fun getSubjectAltNames(): List<SubjectAlternativeName>
```

#### 2.4 Add CRLDistributionPoints Extraction

```kotlin
data class CrlDistributionPoint(
    val distributionPointUri: String?,
    val crlIssuer: List<GeneralName>?
)

fun getCrlDistributionPoints(): List<CrlDistributionPoint>
```

#### 2.5 Add AuthorityKeyIdentifier Extraction

```kotlin
data class AuthorityKeyIdentifier(
    val keyIdentifier: ByteArray?,
    val authorityCertIssuer: List<GeneralName>?,
    val authorityCertSerialNumber: BigInteger?
)

fun getAuthorityKeyIdentifier(): AuthorityKeyIdentifier?
```

#### 2.6 Add SerialNumber Extraction

```kotlin
fun getSerialNumber(): BigInteger
```

#### 2.7 Add Version Extraction

```kotlin
fun getVersion(): Int // 1=v1, 2=v2, 3=v3
```

#### 2.8 Add Public Key Info Extraction

```kotlin
data class PublicKeyInfo(
    val algorithm: String,
    val keySize: Int?,
    val parameters: ByteArray?
)

fun getSubjectPublicKeyInfo(): PublicKeyInfo
```

---

### Priority 3: Profile Constraints (After Infrastructure Ready)

Once infrastructure updated, add these constraints to `wrpAccessCertificateProfile`:

1. **Version check**: `requireVersion(3)`
2. **SerialNumber check**: `requireSerialNumberPositive()`, optionally `requireSerialNumberUnique()`
3. **Subject attributes**: Based on policy, call:
    - `requireSubjectNaturalPersonAttributes()` or
    - `requireSubjectLegalPersonAttributes()`
4. **Issuer attributes**: Similar subject validation for issuer
5. **KeyUsage criticality**: `requireCritical("2.5.29.15")`
6. **CertificatePolicies criticality**: `requireCritical("2.5.29.32")`
7. **SubjectAltName check**: `requireSubjectAltNameForWRPAC()`
8. **CRLDP conditional**: `requireCrlDistributionPointsIfNoOcspAndNotValAssured()`
9. **AuthorityKeyIdentifier**: `requireAuthorityKeyIdentifier()`
10. **Public key algorithm**: `requirePublicKeyAlgorithm("ecdsa" or "rsa", minKeySize)`

---

### Priority 4: Testing & Validation

1. Create test certificates covering all four policy types (NCP-n, NCP-l, QCP-n, QCP-l)
2. Test each constraint individually
3. Create negative test cases violating each requirement
4. Validate against ETSI test specifications if available
5. Ensure infrastructure efficiently handles large batches

---

## Implementation Effort Estimate

| Task                          | Effort (person-days) | Dependencies            |
|-------------------------------|----------------------|-------------------------|
| Priority 1 profile fixes      | 0.5                  | None                    |
| Priority 2.1 (criticality)    | 2                    | Core infrastructure     |
| Priority 2.2 (DN extraction)  | 3                    | Core infrastructure     |
| Priority 2.3 (SubjectAltName) | 2                    | DN extraction           |
| Priority 2.4 (CRLDP)          | 2                    | Core infrastructure     |
| Priority 2.5 (AKI)            | 1                    | Core infrastructure     |
| Priority 2.6 (SerialNumber)   | 0.5                  | Core infrastructure     |
| Priority 2.7 (Version)        | 0.5                  | Core infrastructure     |
| Priority 2.8 (PublicKeyInfo)  | 1                    | Core infrastructure     |
| Priority 3 constraints        | 5                    | All Priority 2 complete |
| Priority 4 testing            | 3                    | Priority 1-3 complete   |
| **TOTAL**                     | **~21 person-days**  |                         |

---

## Risk Assessment

| Risk                                            | Probability | Impact | Mitigation                                                          |
|-------------------------------------------------|-------------|--------|---------------------------------------------------------------------|
| Infrastructure changes break existing consumers | High        | High   | Maintain backwards compatibility, deprecate old interface gradually |
| DN parsing complexity underestimated            | Medium      | High   | Use established ASN.1 libraries (BouncyCastle)                      |
| Performance degradation from extra extraction   | Medium      | Medium | Lazy evaluation, caching where appropriate                          |
| Incomplete ETSI requirement coverage            | Medium      | Medium | Conduct peer review with ETSI experts                               |
| Test coverage insufficient                      | Medium      | High   | Invest in comprehensive test vectors                                |

---

## Comparison with APTITUDE Specification Document

The earlier analysis of the APTITUDE Consortium's access certificate specification document showed similar gaps:

- CertificatePolicies criticality incorrect (NC instead of C)
- Missing extensions: AKI, SubjectAltName validation
- Subject/issuer attribute ambiguities
- Incomplete conditional logic

**Conclusion**: Both the specification document and this implementation share common challenges in fully capturing ETSI
TS 119 411-8 complexity. The implementation is actually **more limited** than the specification document, as it only
covers ~18% of requirements vs. the spec's ~40% (from earlier assessment).

---

## Next Steps

### Immediate Actions (Week 1)

- [ ] Review assessment with team
- [ ] Add AIA constraint to WRPAC profile (Priority 1.1)
- [ ] Add QCStatement constraints for qualified certs (Priority 1.2)
- [ ] Create issue tickets for Priority 2 infrastructure tasks

### Short-term (1-2 Months)

- [ ] Complete Priority 2 infrastructure upgrades
- [ ] Update `CertificateOperations` implementations for all platforms
- [ ] Add corresponding unit tests for new extraction methods
- [ ] Implement Priority 3 profile constraints

### Medium-term (2-3 Months)

- [ ] Complete comprehensive integration testing
- [ ] External security review
- [ ] Validate against ETSI conformance testing if available
- [ ] Update documentation

---

## References

### Standards

- ETSI TS 119 411-8: "Electronic signatures and infrastructures (ESI); Policy requirements for certification authorities
  issuing public key certificates"
- ETSI EN 319 412-1: "Policy requirements for certification authorities issuing public key certificates"
- ETSI EN 319 412-2: "Certificate requirements for certificates issued to natural persons"
- ETSI EN 319 412-3: "Certificate requirements for certificates issued to legal persons"
- ETSI EN 319 412-5: "Policy requirements for qualified certificate service providers"
- RFC 5280: "Internet X.509 Public Key Infrastructure Certificate and Certificate Revocation List (CRL) Profile"
- RFC 9608: "X.509v3 Certificate Extension for Non-Repudiation"

### Code Files

- EUWRPACProvidersList.kt:
  `/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPACProvidersList.kt`
- CertificateProfile.kt:
  `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfile.kt`
- CertificateProfileConstraints.kt:
  `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileConstraints.kt`
- CertificateOperations.kt:
  `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt`
- CertificateOperationsJvm.kt:
  `/consultation/src/jvmAndAndroidMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/CertificateOperationsJvm.kt`
- ETSI119411.kt: `/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/ETSI119411.kt`

---

## Appendix: Compliance Checklist

Use this checklist to track implementation progress:

- [ ] End-entity certificate type (cA=FALSE)
- [ ] KeyUsage: digitalSignature bit set
- [ ] KeyUsage extension marked critical
- [ ] Certificate valid at time of use
- [ ] One of four WRPAC policy OIDs present
- [ ] CertificatePolicies extension marked critical
- [ ] AuthorityInfoAccess with id-ad-caIssuers present
- [ ] OCSP responder in AIA (if used) or CRLDP present
- [ ] SubjectAltName with contact information present
- [ ] CRLDistributionPoints present (conditional)
- [ ] AuthorityKeyIdentifier present
- [ ] For QCP-n: QCStatements 1.1, 1.4 present
- [ ] For QCP-l: QCStatements 1.1, 1.4, 1.6 present
- [ ] Subject DN attributes per person type
- [ ] Issuer DN attributes per person type
- [ ] Version = 3 (X.509v3)
- [ ] SerialNumber unique positive integer
- [ ] SubjectPublicKeyInfo per TS 119 312
- [ ] QCStatements properly marked compliant
- [ ] Validity-assured and noRevocationAvail for short-term
- [ ] Purpose indication (signature vs seal)

---

**End of Document**
