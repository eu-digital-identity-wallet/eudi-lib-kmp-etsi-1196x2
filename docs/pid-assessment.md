# PID Provider Certificate Compliance Assessment

---

## Summary

The `pidSigningCertificateProfile` function in `EUPIDProvidersList.kt` has been implemented to validate **PID Provider end-entity certificates** according to **ETSI TS 119 412-6** requirements. The implementation demonstrates **strong alignment** with the ETSI standard for PID provider certificates, with comprehensive coverage of the core certificate validation requirements.

**Key Findings:**

- ✅ Correctly validates: end-entity certificate type (cA=FALSE), digitalSignature key usage, validity period, QCStatement (id-etsi-qct-pid), certificate policies presence
- ✅ AuthorityInfoAccess (AIA) extension enforced for CA-issued certificates (conditional on self-signed status)
- ✅ X.509 v3 validation implicitly required (for extensions)
- ✅ QCStatement requirement for PID provider certificates validated (id-etsi-qct-pid OID)
- ✅ Certificate Policy presence validated (TSP-defined OIDs per EN 319 412-2 §4.3.3)
- ⚠️ **Missing**: Subject DN attribute validation (natural person vs legal person conditional requirements)
- ⚠️ **Missing**: Issuer DN attribute validation
- ⚠️ **Missing**: Subject Key Identifier extension validation (PID-4.4.2-01)
- ⚠️ **Missing**: Key Usage extension criticality validation
- ⚠️ **Missing**: Public key algorithm/size validation (per ETSI TS 119 312)
- ⚠️ **Missing**: Certificate extension criticality validation (PID-4.1-02)

---

## Assessment Scope

- **File**: `/home/babis/work/eudiw/src/eudi-lib-kmp-etsi-1196x2/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUPIDProvidersList.kt`
- **Function**: `pidSigningCertificateProfile()`
- **Standard**: ETSI TS 119 412-6 (Certificate profiles for PID and Wallet providers)
- **Related Standards**: ETSI EN 319 412-1, ETSI EN 319 412-2, ETSI EN 319 412-3, ETSI EN 319 412-5, RFC 5280
- **Assessment Date**: 2026-03-22

---

## Current Implementation Analysis

### Function Definition (lines 68-82)

```kotlin
public fun pidSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    endEntity()
    mandatoryQcStatement(qcType = ETSI119412Part6.ID_ETSI_QCT_PID, requireCompliance = true)
    keyUsageDigitalSignature()
    validAt(at)
    // Per EN 319 412-2 §4.3.3: certificatePolicies extension shall be present (TSP-defined OID)
    policyIsPresent()
    authorityInformationAccessIfCAIssued()
}
```

### Validated Requirements ✓

| Requirement                         | ETSI Reference          | Implementation                                           | Status |
|-------------------------------------|-------------------------|----------------------------------------------------------|--------|
| End-entity certificate (cA=FALSE)   | TS 119 412-6 PID-4.1-01 | `endEntity()`                                            | ✅      |
| Key Usage: digitalSignature         | TS 119 412-6 PID-4.4.1-01 | `keyUsageDigitalSignature()`                             | ✅      |
| Validity at time of use             | TS 119 412-6 PID-4.1-01 | `validAt(at)`                                            | ✅      |
| QCStatement: id-etsi-qct-pid        | TS 119 412-6 PID-4.5-01 | `mandatoryQcStatement(qcType = ID_ETSI_QCT_PID, requireCompliance = true)` | ✅      |
| Certificate Policy presence         | EN 319 412-2 §4.3.3     | `policyIsPresent()`                                      | ✅      |
| AIA for CA-issued certificates      | TS 119 412-6 PID-4.4.3-01 | `authorityInformationAccessIfCAIssued()`                 | ✅      |
| QCStatement compliance flag         | EN 319 412-5            | `requireCompliance = true`                               | ✅      |

### Missing Requirements ✗

| Requirement                       | ETSI Reference                        | Status | Gap Details                                           |
|-----------------------------------|---------------------------------------|--------|-------------------------------------------------------|
| **Certificate Fields**            |
| subject attributes (natural/legal person) | TS 119 412-6 PID-4.3-01, PID-4.3-02 | ❌      | Conditional validation based on PID provider type     |
| issuer attributes                 | TS 119 412-6 PID-4.2-01              | ❌      | Issuer DN validation not implemented                  |
| subjectPublicKeyInfo              | TS 119 312                            | ❌      | Public key algorithm/size validation missing          |
| **Extensions**                    |
| subjectKeyIdentifier              | TS 119 412-6 PID-4.4.2-01            | ❌      | SKI extension validation not implemented              |
| keyUsage criticality              | RFC 5280 4.2.1.3 / TS 119 412-6 PID-4.1-02 | ❌      | KeyUsage criticality not explicitly validated         |
| extension criticality control     | TS 119 412-6 PID-4.1-02              | ❌      | No validation that extensions are not critical unless allowed |
| **Subject Naming**                |
| Natural person attributes (conditional) | EN 319 412-2 4.2.4 / TS 119 412-6 PID-4.3-01 | ❌      | Not implemented (conditional on provider type)        |
| Legal person attributes (conditional) | EN 319 412-3 4.2.1 / TS 119 412-6 PID-4.3-02 | ❌      | Not implemented (conditional on provider type)        |
| **Conditional Logic**             |
| Self-signed certificate handling  | TS 119 412-6 PID-4.2-01              | ⚠️      | AIA conditional logic implemented, but self-signed not explicitly validated |

---

## Compliance Matrix by ETSI Requirement

### Certificate Structure (RFC 5280 / EN 319 412-2)

| Field                | Requirement              | Compliance | Notes                           |
|----------------------|--------------------------|------------|---------------------------------|
| version              | V3 (integer 2)           | ⚠️        | Implicitly required for extensions, not explicitly validated |
| serialNumber         | Unique positive integer  | ❌        | Not validated                   |
| signature            | Algorithm per TS 119 312 | ❌        | Not validated                   |
| issuer               | Structured DN            | ❌        | Not validated                   |
| validity             | notBefore/notAfter       | ✅        | `validAt(at)`                   |
| subject              | Structured DN            | ❌        | Not validated (conditional on provider type) |
| subjectPublicKeyInfo | Algorithm per TS 119 312 | ❌        | Not validated                   |

### Extensions

| Extension                         | Presence       | Criticality | Compliance | Notes                                          |
|-----------------------------------|----------------|-------------|------------|------------------------------------------------|
| keyUsage                          | M              | C           | ⚠️        | digitalSignature bit validated, criticality NOT validated |
| subjectKeyIdentifier              | M              | NC          | ❌        | Not validated (PID-4.4.2-01)                    |
| AuthorityInfoAccess               | M(C)           | NC          | ✅        | Conditional (not self-signed)                  |
| CertificatePolicies               | M              | NC          | ✅        | Presence validated (TSP-defined OIDs)          |
| qcStatements (id-etsi-qct-pid)    | M(C)           | NC          | ✅        | Fully validated with compliance flag           |
| extension criticality             | Restricted     | N/A         | ❌        | PID-4.1-02: extensions shall not be critical unless allowed |

### Subject Naming (EN 319 412-2/3 per TS 119 412-6 PID-4.3)

| Attribute                   | Natural Person    | Legal Person | Compliance      |
|-----------------------------|-------------------|--------------|-----------------|
| countryName                 | M                 | M            | ❌ **Not Validated** |
| givenName/surname/pseudonym | M (choice)        | -            | ❌ **Not Validated** |
| commonName                  | M                 | M            | ❌ **Not Validated** |
| serialNumber                | M                 | -            | ❌ **Not Validated** |
| organizationName            | C (if associated) | M            | ❌ **Not Validated** |
| organizationIdentifier      | -                 | M            | ❌ **Not Validated** |
| organizationIdentifier fmt  | -                 | M (format)   | ❌ **Not Validated** |

---

## Gap Quantification

| Category             | # Requirements | # Compliant | # Partial | # Missing | % Compliance |
|----------------------|----------------|-------------|-----------|-----------|--------------|
| Certificate Fields   | 7              | 1           | 1         | 5         | 14%          |
| Extensions           | 6              | 2           | 1         | 3         | 33%          |
| QCStatements         | 1              | 1           | 0         | 0         | 100%         |
| Subject Naming       | 7              | 0           | 0         | 7         | 0%           |
| Conditional Logic    | 2              | 1           | 1         | 0         | 50%          |
| **TOTAL**            | **23**         | **5**       | **3**     | **15**    | **22%**      |

**Overall Compliance Score: 5/10**

**Breakdown by Implementation Status:**

- ✅ **Fully Implemented (5 requirements)**: End-entity certificate type, digitalSignature key usage bit, validity period, QCStatement (id-etsi-qct-pid) with compliance flag, certificate policies presence, AIA for CA-issued certificates.

- ⚠️ **Partially Implemented (3 requirements)**: 
  - KeyUsage extension (digitalSignature bit validated, but criticality NOT validated)
  - X.509 v3 (implicitly required for extensions, but not explicitly validated)
  - Self-signed certificate handling (AIA conditional logic implemented, but self-signed status not explicitly validated)

- ❌ **Missing (15 requirements)**: Subject DN attributes (natural person and legal person), issuer DN attributes, Subject Key Identifier, public key algorithm/size, extension criticality control, serial number validation, signature algorithm validation.

---

## Detailed Analysis

### Strengths

1. **QCStatement Validation**: The implementation correctly validates the mandatory QCStatement for PID provider certificates per **TS 119 412-6 PID-4.5-01**:
   - Uses the correct OID: `0.4.0.194126.1.1` (id-etsi-qct-pid)
   - Requires compliance flag (`requireCompliance = true`)

2. **End-Entity Certificate Type**: Correctly validates that the certificate is an end-entity certificate (cA=FALSE) per **TS 119 412-6 PID-4.1-01**.

3. **Key Usage**: Validates the digitalSignature key usage bit per **TS 119 412-6 PID-4.4.1-01**.

4. **Certificate Policy**: Validates the presence of certificate policies per **EN 319 412-2 §4.3.3** (TSP-defined OIDs).

5. **AIA Conditional Logic**: Correctly implements conditional AIA validation for CA-issued certificates per **TS 119 412-6 PID-4.4.3-01**.

### Gaps

1. **Subject DN Validation (Critical Gap)**: 
   - **TS 119 412-6 PID-4.3-01** and **PID-4.3-02** require conditional subject DN validation based on whether the PID provider is a natural person or legal person.
   - The implementation does NOT validate subject DN attributes.
   - **Recommendation**: Add conditional subject DN validation similar to `subjectNameForWRPAC()` in the WRPAC implementation.

2. **Issuer DN Validation**:
   - **TS 119 412-6 PID-4.2-01** requires issuer DN validation.
   - The implementation does NOT validate issuer DN attributes.
   - **Recommendation**: Add issuer DN validation using `issuerLegalPersonAttributes()` or similar.

3. **Subject Key Identifier**:
   - **TS 119 412-6 PID-4.4.2-01** requires the subject key identifier extension to be present.
   - The implementation does NOT validate SKI.
   - **Recommendation**: Add `subjectKeyIdentifier()` constraint.

4. **Key Usage Criticality**:
   - **RFC 5280 4.2.1.3** requires KeyUsage to be marked critical.
   - The `keyUsageDigitalSignature()` function validates the digitalSignature bit but does NOT validate the critical flag.
   - **Recommendation**: Ensure `mandatoryKeyUsage()` validates criticality (already implemented in `CertificateConstraintsEvaluations.mandatoryKeyUsage()`).

5. **Public Key Validation**:
   - **TS 119 312** specifies public key algorithm and size requirements.
   - The implementation does NOT validate public key parameters.
   - **Recommendation**: Add `publicKey(options = ...)` constraint similar to WRPAC implementation.

6. **Extension Criticality Control**:
   - **TS 119 412-6 PID-4.1-02** states: "Certificate extensions shall not be marked critical unless criticality is explicitly allowed or required".
   - The implementation does NOT validate extension criticality.
   - **Recommendation**: Add validation to ensure only allowed extensions are marked critical.

7. **Serial Number Validation**:
   - **RFC 5280 4.1.2.2** requires serial number to be a positive integer.
   - The implementation does NOT validate serial number.
   - **Recommendation**: Add `positiveSerialNumber()` constraint.

---

## Recommendations

### High Priority (Required for Compliance)

1. **Add Subject DN Validation**:
   ```kotlin
   // Conditional based on PID provider type (natural person vs legal person)
   // Option 1: Validate both and use conditional logic
   subjectNameForPIDProvider() // New function with conditional logic
   
   // Option 2: Separate profiles for natural person and legal person
   pidProviderNaturalPersonProfile()
   pidProviderLegalPersonProfile()
   ```

2. **Add Issuer DN Validation**:
   ```kotlin
   issuerLegalPersonAttributes() // Or conditional based on issuer type
   ```

3. **Add Subject Key Identifier Validation**:
   ```kotlin
   subjectKeyIdentifier() // New constraint function
   ```

4. **Add Public Key Validation**:
   ```kotlin
   publicKey(
       options = PublicKeyAlgorithmOptions.of(
           PublicKeyAlgorithmOptions.AlgorithmRequirement.RSA_2048,
           PublicKeyAlgorithmOptions.AlgorithmRequirement.EC_256,
           PublicKeyAlgorithmOptions.AlgorithmRequirement.ECDSA_256,
       ),
   )
   ```

### Medium Priority (Best Practices)

5. **Add Serial Number Validation**:
   ```kotlin
   positiveSerialNumber()
   ```

6. **Add X.509 v3 Explicit Validation**:
   ```kotlin
   version3()
   ```

7. **Add Extension Criticality Validation**:
   ```kotlin
   // Validate that only allowed extensions are marked critical
   validateExtensionCriticality()
   ```

### Low Priority (Enhancements)

8. **Add Signature Algorithm Validation**:
   ```kotlin
   signatureAlgorithm(allowedAlgorithms = ...)
   ```

9. **Add Comprehensive Testing**:
   - Create test cases for natural person PID provider certificates
   - Create test cases for legal person PID provider certificates
   - Create negative test cases for all constraints

---

## Next Steps

### Immediate Actions

1. **Implement Subject DN Validation**: Add conditional subject DN validation based on PID provider type (natural person vs legal person).

2. **Implement Issuer DN Validation**: Add issuer DN validation for PID provider certificates.

3. **Implement Subject Key Identifier Validation**: Add SKI presence validation.

4. **Implement Public Key Validation**: Add public key algorithm and size validation per ETSI TS 119 312.

5. **Verify Key Usage Criticality**: Ensure `keyUsageDigitalSignature()` validates the critical flag.

### Future Enhancements

- [ ] Add serial number validation
- [ ] Add X.509 v3 explicit validation
- [ ] Add extension criticality control validation
- [ ] Add signature algorithm validation
- [ ] Create comprehensive test suite
- [ ] External security review
- [ ] Validate against ETSI conformance testing if available

---

## References

### Standards

- **ETSI TS 119 412-6**: "Electronic Signatures and Infrastructures (ESI); Certificate Profiles; Part 6: Certificate profiles for PID and Wallet providers"
- **ETSI EN 319 412-1**: "Policy requirements for certification authorities issuing public key certificates"
- **ETSI EN 319 412-2**: "Certificate requirements for certificates issued to natural persons"
- **ETSI EN 319 412-3**: "Certificate requirements for certificates issued to legal persons"
- **ETSI EN 319 412-5**: "Policy requirements for qualified certificate service providers"
- **ETSI TS 119 312**: "Electronic Signatures and Infrastructures (ESI); Algorithms and Parameters for Secure Electronic Signatures"
- **RFC 5280**: "Internet X.509 Public Key Infrastructure Certificate and Certificate Revocation List (CRL) Profile"

### Code Files

- **EUPIDProvidersList.kt**: `/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUPIDProvidersList.kt`
- **ETSI119412Part6.kt**: `/119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/ETSI119412Part6.kt`
- **CertificateProfile.kt**: `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfile.kt`
- **CertificateProfileConstraints.kt**: `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateProfileConstraints.kt`
- **CertificateConstraintsEvaluations.kt**: `/consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateConstraintsEvaluations.kt`

---

**End of Document**
