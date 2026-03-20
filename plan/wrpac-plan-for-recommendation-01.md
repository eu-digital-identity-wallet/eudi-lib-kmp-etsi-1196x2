# Priority 1 Implementation Plan: Immediate Profile Fixes

## Objective
Enhance the `wrpAccessCertificateProfile` function with ETSI TS 119 411-8 mandatory requirements that can be implemented using existing infrastructure:
- Enforce AuthorityInfoAccess (AIA) extension for CA-issued certificates
- Validate QCStatements for qualified certificates (QCP-n and QCP-l)
- Add comprehensive test coverage for both positive and negative cases
- Ensure `.gradlew spotlessApply check` passes

---

## Files to Modify

| File | Purpose |
|------|---------|
| `119602-consultation/src/commonMain/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPACProvidersList.kt` | Add AIA and QCStatement constraints |
| `119602-consultation/src/jvmAndAndroidTest/kotlin/eu/europa/ec/eudi/etsi119602/consultation/CertOps.kt` | Support multiple QCStatements in test certificate generator |
| `119602-consultation/src/jvmAndAndroidTest/kotlin/eu/europa/ec/eudi/etsi119602/consultation/eu/EUWRPAccessCertificateTest.kt` | Add tests for new constraints, adjust existing tests |

---

## Step 1: Update WRPAC Profile

**File**: `EUWRPACProvidersList.kt`  
**Location**: Inside `wrpAccessCertificateProfile` function, after `requireNoSelfSigned()`

### Change
```kotlin
return certificateProfile {
    requireEndEntityCertificate()
    requireDigitalSignature()
    requireValidAt(at)
    requirePolicy(policies)
    requireNoSelfSigned()
    // ADD BELOW
    requireAiaForCaIssued()
    if (QCP_N_EUDIWRP in policies || QCP_L_EUDIWRP in policies) {
        requireQcStatement("0.4.0.1862.1.1") // QcCompliance
        requireQcStatement("0.4.0.1862.1.4") // QcSSCD
    }
    if (QCP_L_EUDIWRP in policies) {
        requireQcStatement("0.4.0.1862.1.6") // QcPurpose (seal)
    }
}
```

### Notes
- `requireAiaForCaIssued()` is already available in `CertificateProfileConstraints.kt` and checks for `id-ad-caIssuers` presence in AIA for non-self-signed certs.
- `requireQcStatement(oid)` is available; it verifies presence of the QCStatement with given OID.
- QCStatement OIDs:
  - `0.4.0.1862.1.1` = QcCompliance (required for all QCP)
  - `0.4.0.1862.1.4` = QcSSCD (required for all QCP)
  - `0.4.0.1862.1.6` = QcPurpose (required for QCP-l only)

---

## Step 2: Enhance Test Certificate Generator

**File**: `CertOps.kt` (119602-consultation module)

The current generator only supports a single QCStatement via `qcTypeAndCompliance: Pair<String, Boolean>?`. We need to support multiple statements.

### 2.1 Update Public Factory Signatures

Change all occurrences of `qcTypeAndCompliance: Pair<String, Boolean>?` to `qcStatements: List<Pair<String, Boolean>>?`:

- `genTrustAnchor(...)`
- `genCAIssuedEndEntityCertificate(...)`
- `genSelfSignedEndEntityCertificate(...)`

Update parameter names and default values accordingly.

### 2.2 Update Private Builders

- `createTrustAnchor(...)`
- `createEndEntity(...)`
- `createSelfSignedEndEntity(...)`

Replace single QC statement logic with batch logic:

```kotlin
if (!qcStatements.isNullOrEmpty()) {
    val qcStatementSequences = qcStatements.map { (qcType, qcCompliance) ->
        DERSequence(
            arrayOf(
                ASN1ObjectIdentifier(qcType),
                DERUTF8String(if (qcCompliance) "compliant" else "non-compliant")
            )
        )
    }
    val qcStatementsSeq = DERSequence(qcStatementSequences.toTypedArray())
    addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"), false, qcStatementsSeq)
}
```

### 2.3 Remove or Deprecate Single-Statement Helper

The existing extension function `JcaX509v3CertificateBuilder.qcStatement(qcType, qcCompliance)` (lines 259–276) can be removed or kept as private. To avoid confusion, replace its usage with the batch logic above.

### 2.4 Propagate Parameter Changes

Update all calls to these builder functions within `CertOps.kt` to use the new parameter name and pass through the list.

---

## Step 3: Update Tests

**File**: `EUWRPAccessCertificateTest.kt`

### 3.1 Adjust `shouldAcceptPolicy` to Include Required Extensions

```kotlin
fun shouldAcceptPolicy(policyOid: String) = runTest {
    val qcStatements = when (policyOid) {
        ETSI119411.QCP_N_EUDIWRP -> listOf(
            "0.4.0.1862.1.1" to true,
            "0.4.0.1862.1.4" to true
        )
        ETSI119411.QCP_L_EUDIWRP -> listOf(
            "0.4.0.1862.1.1" to true,
            "0.4.0.1862.1.4" to true,
            "0.4.0.1862.1.6" to true
        )
        else -> null
    }
    val certificate = genCAIssuedEndEntityCertificate(
        policyOids = listOf(policyOid),
        qcStatements = qcStatements,
        caIssuersUri = "http://test.example.com/ca.crt",
        ocspUri = "http://test.example.com/ocsp",
    )
    val constraintEvaluation = evaluateEndEntityCertificateConstraints(certificate)
    assertTrue(constraintEvaluation.isMet())
}
```

- NCP policies: no QCStatements, but still require AIA.
- QCP policies: include required QCStatements and AIA.

### 3.2 Add Negative Tests

```kotlin
@Test
fun `WRPAC should reject CA-issued certificate without AIA`() = runTest {
    val certificate = genCAIssuedEndEntityCertificate(
        policyOids = listOf(ETSI119411.NCP_N_EUDIWRP),
        qcStatements = null,
        caIssuersUri = null,
        ocspUri = null,
    )
    val evaluation = evaluateEndEntityCertificateConstraints(certificate)
    assertFalse(evaluation.isMet())
    evaluation.assertSingleViolation { it.contains("AIA", ignoreCase = true) }
}

@Test
fun `WRPAC should reject QCP-n without QCStatements`() = runTest {
    val certificate = genCAIssuedEndEntityCertificate(
        policyOids = listOf(ETSI119411.QCP_N_EUDIWRP),
        qcStatements = null,
        caIssuersUri = "http://test.example.com/ca.crt",
        ocspUri = "http://test.example.com/ocsp",
    )
    val evaluation = evaluateEndEntityCertificateConstraints(certificate)
    assertFalse(evaluation.isMet())
    evaluation.assertSingleViolation { it.contains("qcstatement", ignoreCase = true) }
}

@Test
fun `WRPAC should reject QCP-n missing QcSSCD`() = runTest {
    val certificate = genCAIssuedEndEntityCertificate(
        policyOids = listOf(ETSI119411.QCP_N_EUDIWRP),
        qcStatements = listOf("0.4.0.1862.1.1" to true),
        caIssuersUri = "http://test.example.com/ca.crt",
        ocspUri = "http://test.example.com/ocsp",
    )
    val evaluation = evaluateEndEntityCertificateConstraints(certificate)
    assertFalse(evaluation.isMet())
    evaluation.assertSingleViolation { it.contains("qcstatement", ignoreCase = true) }
}

@Test
fun `WRPAC should reject QCP-l missing QcPurpose`() = runTest {
    val certificate = genCAIssuedEndEntityCertificate(
        policyOids = listOf(ETSI119411.QCP_L_EUDIWRP),
        qcStatements = listOf(
            "0.4.0.1862.1.1" to true,
            "0.4.0.1862.1.4" to true
        ),
        caIssuersUri = "http://test.example.com/ca.crt",
        ocspUri = "http://test.example.com/ocsp",
    )
    val evaluation = evaluateEndEntityCertificateConstraints(certificate)
    assertFalse(evaluation.isMet())
    evaluation.assertSingleViolation { it.contains("qcstatement", ignoreCase = true) }
}
```

### 3.3 Update Existing Tests That Generate Certificates

- `test "WRPAC should require policy"`: currently calls `genCAIssuedEndEntityCertificate(policyOids = null)`. This test should remain as-is (no AIA) to verify policy requirement; however, the current generator may not add AIA by default. The test expects failure due to missing policy, so it remains correct.

- `test "WRPAC should reject unknown policy"`: similarly valid as-is.

No changes required to existing tests other than ensuring they still compile with the updated `CertOps` signatures. They may need to pass `caIssuersUri`/`ocspUri` if they rely on generated certificates. However, since they currently fail for other reasons, they can remain unchanged.

### 3.4 Self-Signed Tests Unchanged

- `test "WRPAC should not be CA certificate"` uses `genTrustAnchor` → parameter updates may be needed.
- `test "WRPAC should not be self-signed"` uses `genSelfSignedEndEntityCertificate` → parameter updates may be needed.

Adjust calls to `genTrustAnchor` and `genSelfSignedEndEntityCertificate` to use `qcStatements` param (or pass `null`). Trust anchor and self-signed certificates do not require AIA.

---

## Step 4: Build and Format

Run the following commands from the project root:

```bash
./gradlew spotlessApply
./gradlew check
```

All tests must pass. If formatting issues arise, `spotlessApply` should fix them.

---

## Verification Checklist

- [ ] `EUWRPACProvidersList.kt` compiles with added constraints
- [ ] `CertOps.kt` updated to support multiple QCStatements
- [ ] All public factory functions accept `qcStatements: List<Pair<String, Boolean>>?`
- [ ] Private builders correctly encode multiple QCStatements
- [ ] `EUWRPAccessCertificateTest.kt` updated helper includes AIA and appropriate QCStatements per policy
- [ ] New negative tests for missing AIA and QCStatements added and passing
- [ ] All existing tests still pass (modulo signature adjustments)
- [ ] `./gradlew spotlessApply` applies formatting without errors
- [ ] `./gradlew check` completes successfully

---

## Expected Compliance Increase

- From: 4/10 (~18% of ~39 requirements)
- To: 5/10 (~23% of ~39 requirements)

Newly satisfied requirements:
- AuthorityInfoAccess presence for CA-issued certs (EN 319 412-2 4.4.1)
- QCStatements QcCompliance for QCP-n and QCP-l (EN 319 412-5 4.2.1)
- QCStatements QcSSCD for QCP-n and QCP-l (EN 319 412-5 4.2.2)
- QCStatements QcPurpose for QCP-l (EN 319 412-5 4.2.3)

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| `CertOps` signature changes break other test classes | Search and update all call sites: `grep -r "genCAIssuedEndEntityCertificate"` and similar |
| QCStatements encoding differs from production expectations | Reuse existing encoding pattern from `CertOps.kt` lines 259–276 (already correct) |
| AIA constraint may require `id-ad-caIssuers` specifically | Confirm `requireAiaForCaIssued()` checks for `id-ad-caIssuers` (from code review) |
| Test certificate validity period affecting AIA checks | AIA is independent of validity; no issue |

---

## Rollback Plan

Since changes are additive and confined to test module + one profile file:
1. Revert `EUWRPACProvidersList.kt` to previous state
2. Revert `CertOps.kt` and `EUWRPAccessCertificateTest.kt` changes
3. Run `./gradlew clean check` to verify restoration

---

## Estimated Effort

- Profile update: 15 minutes
- CertOps refactor: 1 hour
- Test updates: 1 hour
- Verification: 30 minutes
- **Total**: ~3 hours

---

## Post-Implementation

After this plan:
- Document new test coverage in the assessment history table
- Consider adding similar AIA/QCStatement constraints to other profiles if missing
- Plan for Priority 2 infrastructure upgrades to address remaining gaps
