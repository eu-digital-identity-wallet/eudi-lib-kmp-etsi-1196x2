# Subject Key Identifier (SKI) Validation Implementation Plan

## Overview

This plan outlines the steps required to implement Subject Key Identifier (SKI) validation for PID Provider certificates according to ETSI TS 119 412-6 requirement **PID-4.4.2-01**.

**Current Gap**: The `pidSigningCertificateProfile()` function does not validate the presence of the subjectKeyIdentifier extension.

**Target Compliance**: Achieve full compliance with PID-4.4.2-01 by adding SKI presence validation.

**Key Finding**: The `CertOps` certificate generation functions already include SKI by default, so no changes are needed to the generation logic itself.

---

## Implementation Steps

### 1. Extend CertificateOperationsAlgebra

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt`

Add a new algebra operation to retrieve the Subject Key Identifier:

```kotlin
/**
 * Extract the Subject Key Identifier extension.
 * Returns null if the extension is not present.
 */
public data object GetSubjectKeyIdentifier : CertificateOperationsAlgebra<ByteArray?>
```

**Rationale**: This follows the existing pattern of extraction operations (GetAuthorityKeyIdentifier, GetCrlDistributionPoints, etc.). The SKI is returned as a byte array (the raw key identifier bytes).

---

### 2. Update CertificateOperations Interface

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateOperations.kt`

Add the corresponding abstract method:

```kotlin
public fun getSubjectKeyIdentifier(certificate: CERT): ByteArray?
```

**Rationale**: This defines the contract that all platform-specific implementations must fulfill.

---

### 3. Implement SKI Extraction in CertificateOperationsJvm

**File**: `consultation/src/jvmAndAndroidMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/CertificateOperationsJvm.kt`

Add the implementation using Bouncy Castle:

```kotlin
public override fun getSubjectKeyIdentifier(certificate: X509Certificate): ByteArray? = try {
    val skiExtension = certificate.getExtensionValue(Extension.subjectKeyIdentifier.id)
    skiExtension?.parseSubjectKeyIdentifier()
} catch (e: Exception) {
    logger.warn("Failed to parse SubjectKeyIdentifier from certificate: ${e.message}", e)
    null
}

/**
 * Helper function to parse Subject Key Identifier from DER-encoded extension value.
 *
 * The SubjectKeyIdentifier extension contains an OCTET STRING with the key identifier.
 * Per RFC 5280 Section 4.2.1.2, the keyIdentifier is an octet string.
 */
private fun ByteArray.parseSubjectKeyIdentifier(): ByteArray? = try {
    val octetString = DEROctetString.getInstance(this)
    octetString.octets.copyOf()
} catch (e: Exception) {
    logger.warn("Failed to parse SubjectKeyIdentifier: ${e.message}", e)
    null
}
```

**Location in file**: Place the override method with other extension getters (around line 476). Place the helper function near other parsing helpers (around line 408).

**Rationale**: Uses standard Bouncy Castle parsing. The extension value is wrapped in an OCTET STRING per RFC 5280. The implementation follows the same pattern as `parseAuthorityKeyIdentifier()`.

---

### 4. Add SKI Constraint to CertificateConstraintsEvaluations

**File**: `consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi1196x2/consultation/certs/CertificateConstraintsEvaluations.kt`

Add two elements:

**4.1. Violation message property** (around line 572, after AKI violations):

```kotlin
public val missingSubjectKeyIdentifier: CertificateConstraintViolation
    get() = CertificateConstraintViolation(
        reason = "Certificate missing subjectKeyIdentifier extension",
    )
```

**Rationale**: This follows the same pattern as other extension constraints (e.g., `missingAuthorityKeyIdentifier`). The violation message is concise and doesn't reference a specific standard, making it reusable across different certificate profiles. The ETSI TS 119 412-6 requirement (PID-4.4.2-01) is already documented in the profile function (`pidSigningCertificateProfile()`) where the constraint is invoked.

**4.2. Validation function** (around line 314, after `authorityKeyIdentifier()`):

```kotlin
public fun subjectKeyIdentifier(
    ski: ByteArray?,
): CertificateConstraintEvaluation = CertificateConstraintEvaluation {
    if (ski == null) {
        add(missingSubjectKeyIdentifier)
    }
}
```

**Rationale**: This follows the same pattern as `authorityKeyIdentifier()` constraint. The constraint simply checks that SKI is present (non-null). Per PID-4.4.2-01, the extension **shall be present**.

---

### 5. Integrate SKI Validation into PID Provider Certificate Profile

**File**: `119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUPIDProviderCertificate.kt`

Add the constraint call in the `pidSigningCertificateProfile()` function (after line 50):

```kotlin
subjectKeyIdentifier()
```

**Updated function (lines 26-52)**:

```kotlin
public fun pidSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    // X.509 v3 required (for extensions)
    version3()
    endEntity()
    mandatoryQcStatement(qcType = ETSI119412Part6.ID_ETSI_QCT_PID, requireCompliance = true)
    keyUsageDigitalSignature()
    validAt(at)
    // Per EN 319 412-2 §4.3.3: certificatePolicies extension shall be present (TSP-defined OID)
    policyIsPresent()
    authorityInformationAccessIfCAIssued()

    // Serial number must be positive (RFC 5280)
    positiveSerialNumber()

    // Public key requirements (TS 119 312)
    publicKey(
        options = PublicKeyAlgorithmOptions.of(
            PublicKeyAlgorithmOptions.AlgorithmRequirement.RSA_2048,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.EC_256,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.ECDSA_256,
        ),
    )
    // (TS 119 412-6, PID-4.2-01)
    // (TS 119 412-6, PID-4.3-01, PID-4.3-02)
    issuerAndSubjectForPIDProvider()

    // Subject Key Identifier required (TS 119 412-6, PID-4.4.2-01)
    subjectKeyIdentifier()
}
```

**Rationale**: The constraint is added at the end of the profile builder, consistent with other extension validations. It's a simple presence check.

---

### 6. Add `withSKI` Parameter to Certificate Generation Functions

**File**: `119602-consultation/src/jvmAndAndroidTest/kotlin/eu/europa/ec/eudi/etsi119602/consultation/CertOps.kt`

**Key Finding**: Both `genCAIssuedEndEntityCertificate()` and `genSelfSignedEndEntityCertificate()` already include SKI by default via the `subjectKeyIdentifier()` helper method. We need to add a `withSKI` parameter to allow test certificates to be generated **without** SKI for negative testing.

**6.1. Update `genCAIssuedEndEntityCertificate()` function signature** (around line 127):

Add `withSKI: Boolean = true` parameter:

```kotlin
fun genCAIssuedEndEntityCertificate(
    signerCert: X509CertificateHolder,
    signerKey: PrivateKey,
    sigAlg: String,
    subject: X500Name,
    keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
    qcStatements: List<Pair<String, Boolean>>? = null,
    policyOids: List<String>? = null,
    caIssuersUri: String? = null,
    ocspUri: String? = null,
    crlDistributionPointUri: String? = null,
    subjectAltNameUri: String? = null,
    subjectKeyPairAlg: String = "EC",
    subjectKeySize: Int? = null,
    notAfter: Date? = null,
    customExtensions: List<Triple<String, Boolean, ASN1Encodable>> = emptyList(),
    withSKI: Boolean = true, // NEW parameter
): Pair<KeyPair, X509CertificateHolder>
```

**6.2. Update `createEndEntity()` private function** (around line 179):

Add the same `withSKI` parameter and conditionally add SKI:

```kotlin
private fun createEndEntity(
    signerCert: X509CertificateHolder,
    signerKey: PrivateKey,
    sigAlg: String,
    certKey: PublicKey,
    subject: X500Name,
    keyUsage: KeyUsage,
    qcStatements: List<Pair<String, Boolean>>? = null,
    policyOids: List<String>? = null,
    caIssuersUri: String? = null,
    ocspUri: String? = null,
    crlDistributionPointUri: String? = null,
    subjectAltNameUri: String? = null,
    notAfter: Date? = null,
    customExtensions: List<Triple<String, Boolean, ASN1Encodable>> = emptyList(),
    withSKI: Boolean = true, // NEW parameter
): X509CertificateHolder =
    JcaX509v3CertificateBuilder(
        signerCert.subject,
        calculateSerialNumber(),
        Date.from(notBefore().toJavaInstant()),
        notAfter ?: calculateDate(24 * 31),
        subject,
        certKey,
    ).apply {
        authorityKeyIdentifier(signerCert)
        if (withSKI) { // CONDITIONAL SKI
            subjectKeyIdentifier(certKey)
        }
        basicConstraints(BasicConstraints(false))
        keyUsage(keyUsage)
        // ... rest of existing code
    }.build(sigAlg, signerKey)
```

**6.3. Update `genSelfSignedEndEntityCertificate()` function signature** (around line 147):

```kotlin
fun genSelfSignedEndEntityCertificate(
    sigAlg: String,
    subject: X500Name,
    keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
    qcStatements: List<Pair<String, Boolean>>? = null,
    policyOids: List<String>? = null,
    withSKI: Boolean = true, // NEW parameter
): Pair<KeyPair, X509CertificateHolder>
```

**6.4. Update `createSelfSignedEndEntity()` private function** (around line 159):

```kotlin
private fun createSelfSignedEndEntity(
    keyPair: KeyPair,
    sigAlg: String,
    name: X500Name,
    keyUsage: KeyUsage,
    qcStatements: List<Pair<String, Boolean>>?,
    policyOids: List<String>?,
    withSKI: Boolean = true, // NEW parameter
): X509CertificateHolder {
    return JcaX509v3CertificateBuilder(
        name,
        calculateSerialNumber(),
        Date.from(notBefore().toJavaInstant()),
        calculateDate(24 * 31),
        name,
        keyPair.public,
    ).apply {
        if (withSKI) { // CONDITIONAL SKI
            subjectKeyIdentifier(keyPair.public)
        }
        basicConstraints(BasicConstraints(false)) // end-entity (cA=FALSE)
        keyUsage(keyUsage)
        // ... rest of existing code
    }.build(sigAlg, keyPair.private)
}
```

**Rationale**: The `withSKI` parameter allows test certificates to be generated without SKI to verify the constraint violation. Default `true` ensures backward compatibility for existing tests.

---

### 7. Update Test Suite

**File**: `119602-consultation/src/jvmAndAndroidTest/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUPIDSigningCertificateProfileTests.kt`

**7.1. Update certificate generation helpers**:

The test file contains private helper functions that wrap `CertOps`. Update them to accept and pass the `withSKI` parameter.

Locate the helper functions (likely around lines 50-100) and add the parameter:

```kotlin
private fun genCAIssuedEndEntityCertificate(
    subject: X500Name,
    qcStatements: List<Pair<String, Boolean>>? = null,
    policyOids: List<String>? = null,
    caIssuersUri: String? = null,
    ocspUri: String? = null,
    subjectAltNameUri: String? = null,
    keyUsage: KeyUsage,
    withSKI: Boolean = true, // NEW
): X509Certificate {
    // ...
    val (_, certHolder) = CertOps.genCAIssuedEndEntityCertificate(
        // ... existing parameters
        withSKI = withSKI, // NEW
    )
    // ...
}
```

Do the same for `genSelfSignedEndEntityCertificate()`.

**7.2. Add test for missing SKI**:

Add a new test function that verifies absence of SKI is reported as a violation:

```kotlin
@Test
fun `CA-issued certificate should require subjectKeyIdentifier`() = runTest {
    val certificate = genCAIssuedEndEntityCertificate(
        qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
        policyOids = listOf("1.2.3.4.5"),
        caIssuersUri = "http://example.com/ca.crt",
        ocspUri = "http://example.com/ocsp",
        keyUsage = KeyUsage(KeyUsage.digitalSignature),
        subject = legalEntityPidProviderName,
        withSKI = false, // Explicitly omit SKI
    )

    val constraintEvaluation = evaluateCertificateConstraints(certificate)
    assertFalse(constraintEvaluation.isMet())
    constraintEvaluation.assertSingleViolation { it.contains("subjectKeyIdentifier", ignoreCase = true) }
}
```

And for self-signed:

```kotlin
@Test
fun `Self-signed certificate should require subjectKeyIdentifier`() = runTest {
    val certificate = genSelfSignedEndEntityCertificate(
        qcStatements = listOf(ETSI119412Part6.ID_ETSI_QCT_PID to true),
        policyOids = listOf("1.2.3.4.5"),
        keyUsage = KeyUsage(KeyUsage.digitalSignature),
        subject = legalEntityPidProviderName,
        withSKI = false, // Explicitly omit SKI
    )

    val constraintEvaluation = evaluateCertificateConstraints(certificate)
    assertFalse(constraintEvaluation.isMet())
    constraintEvaluation.assertSingleViolation { it.contains("subjectKeyIdentifier", ignoreCase = true) }
}
```

**7.3. Ensure existing tests still pass**:

All existing tests rely on default `withSKI = true`, so they should continue to pass. However, verify that the valid certificate tests (`CA Issued certificate should be valid`, `Self-signed LP certificate should be valid`, `Self-signed NP certificate should be valid`) still pass after SKI constraint is added. They should, because SKI will be present by default.

**7.4. Single-violation rule**:

The new tests respect the "single violation per test" principle. They explicitly check for one violation about SKI.

---

### 8. Verify Complete Integration

**8.1. Check the extension is marked non-critical**:

Per ETSI TS 119 412-6, the subjectKeyIdentifier extension should not be marked critical (PID-4.1-02). Our constraint only checks presence, not criticality. This aligns with the assessment gap: extension criticality control is a separate missing requirement. We don't need to validate criticality for SKI specifically unless the standard explicitly mandates it. The SKI should typically be non-critical per RFC 5280.

**Note**: The existing `CertOps` code already sets SKI as non-critical:
```kotlin
private fun JcaX509v3CertificateBuilder.subjectKeyIdentifier(certKey: PublicKey) {
    addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(certKey))
}
```

**8.2. Confirm SKI generation algorithm**:

Bouncy Castle's `JcaX509ExtensionUtils.createSubjectKeyIdentifier()` computes the key identifier as:
- For RSA/DSA/EC: SHA-1 hash of the BIT STRING of the subject public key (per RFC 5280 4.2.1.2)

This is the standard method. The existing code handles this correctly.

---

## Implementation Order

1. **Step 1-2**: Add algebra and interface method (commonMain)
2. **Step 3**: Implement JVM extraction function
3. **Step 4**: Add constraint to CertificateConstraintsEvaluations
4. **Step 5**: Integrate constraint into PID provider profile
5. **Step 6**: Modify `CertOps.kt` certificate generation functions with `withSKI` parameter
6. **Step 7**: Update test helpers and add new test methods
7. **Step 8**: Run full test suite to verify all tests pass, especially new SKI-violation tests

---

## Files to Modify

| File | Changes |
|------|---------|
| `consultation/src/commonMain/kotlin/.../CertificateOperations.kt` | Add `GetSubjectKeyIdentifier` object; add `getSubjectKeyIdentifier()` method |
| `consultation/src/jvmAndAndroidMain/kotlin/.../CertificateOperationsJvm.kt` | Implement `getSubjectKeyIdentifier()` override; add `parseSubjectKeyIdentifier()` helper |
| `consultation/src/commonMain/kotlin/.../CertificateConstraintsEvaluations.kt` | Add `missingSubjectKeyIdentifier` violation; add `subjectKeyIdentifier()` function |
| `119602-consultation/src/commonMain/kotlin/.../EUPIDProviderCertificate.kt` | Add `subjectKeyIdentifier()` call in profile |
| `119602-consultation/src/jvmAndAndroidTest/kotlin/.../CertOps.kt` | Add `withSKI` parameter to `genCAIssuedEndEntityCertificate()` and `genSelfSignedEndEntityCertificate()`; implement conditional SKI extension generation |
| `119602-consultation/src/jvmAndAndroidTest/kotlin/.../EUPIDSigningCertificateProfileTests.kt` | Update test helper functions; add 2 new test methods for SKI absence |

---

## Expected Outcome

After implementing this plan:

- The `pidSigningCertificateProfile()` will enforce presence of subjectKeyIdentifier extension.
- Test certificates without SKI will fail validation with a clear violation message.
- All existing tests should continue to pass (since SKI will be included by default).
- Compliance with ETSI TS 119 412-6 **PID-4.4.2-01** will be achieved.
- The assessment compliance score will improve from **82% (19/23)** to **86% (20/23)**, assuming other gaps remain.
- The Extensions category will improve from **50% (3/6)** to **67% (4/6)**.

---

## Additional Considerations

### Backward Compatibility

Adding `withSKI: Boolean = true` to generation functions preserves backward compatibility. Existing callers that don't specify the parameter will get SKI included by default.

### Error Handling

The JVM implementation includes try-catch and logging for robustness. If the SKI extension is malformed, the getter returns `null`, which triggers the constraint violation (missing SKI). This is acceptable.

### Testing Strategy

- **Positive tests**: Confirm that certificates generated with default `withSKI=true` pass SKI validation.
- **Negative tests**: The new tests verify that `withSKI=false` produces a missing SKI violation.
- **Single violation**: The new tests are designed to fail with exactly one violation (SKI), isolating the test to that specific requirement.

### Future Work

The assessment also identified **extension criticality control** (PID-4.1-02) as missing. That is a separate, more complex feature requiring validation that no extension is marked critical unless explicitly allowed. It should be implemented independently of this SKI feature.

---

## References

- **ETSI TS 119 412-6**: PID-4.4.2-01 - "The certificate holder's public key shall be identified by the subjectKeyIdentifier extension"
- **RFC 5280 Section 4.2.1.2**: Subject Key Identifier definition and generation method
- **Implementation Pattern**: Follows existing extension handling (AIA, AKI, etc.)

---

**End of Plan**
