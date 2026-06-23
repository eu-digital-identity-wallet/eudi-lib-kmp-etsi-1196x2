/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import XCTest
@testable import PKIXBridge

final class PKIXCertificateInspectorTests: XCTestCase {

    private var inspector: PKIXCertificateInspector!
    private var rootCertData: NSData!

    override func setUpWithError() throws {
        try super.setUpWithError()
        inspector = PKIXCertificateInspector()
        guard let url = Bundle.module.url(forResource: "test-root-ca", withExtension: "der") else {
            throw XCTSkip("Fixture test-root-ca.der not found")
        }
        rootCertData = try Data(contentsOf: url) as NSData
    }

    // MARK: - Top-level fields

    func test_getVersion_v3() {
        XCTAssertEqual(inspector.getVersion(rootCertData), 2)
    }

    func test_getSerialNumber_20Bytes() {
        let serial = inspector.getSerialNumber(rootCertData)
        XCTAssertEqual(serial?.length, 20)
    }

    func test_getValidity_epochSeconds() {
        let before = inspector.getValidityNotBeforeEpochSeconds(rootCertData)
        let after = inspector.getValidityNotAfterEpochSeconds(rootCertData)
        XCTAssertNotNil(before)
        XCTAssertNotNil(after)
        XCTAssertLessThan(before!.doubleValue, after!.doubleValue)
    }

    func test_getSubject() {
        let subject = inspector.getSubject(rootCertData)
        XCTAssertEqual(subject?["2.5.4.6"], "DE")
        XCTAssertEqual(subject?["2.5.4.10"], "EUDI Test Org")
        XCTAssertEqual(subject?["2.5.4.3"], "PKIXBridge Test Root CA")
    }

    func test_getIssuer() {
        let issuer = inspector.getIssuer(rootCertData)
        XCTAssertEqual(issuer?["2.5.4.6"], "DE")
        XCTAssertEqual(issuer?["2.5.4.3"], "PKIXBridge Test Root CA")
    }

    func test_isSelfSigned() {
        XCTAssertTrue(inspector.isSelfSigned(rootCertData))
    }

    // MARK: - Extensions

    func test_hasExtension() {
        XCTAssertTrue(inspector.hasExtension(rootCertData, oid: X509Oids.keyUsage))
        XCTAssertTrue(inspector.hasExtension(rootCertData, oid: X509Oids.basicConstraints))
        XCTAssertFalse(inspector.hasExtension(rootCertData, oid: X509Oids.qcStatements))
    }

    func test_getExtensionCriticality() {
        let crit = inspector.getExtensionCriticality(rootCertData)
        XCTAssertEqual(crit?[X509Oids.keyUsage]?.boolValue, true)
        XCTAssertEqual(crit?[X509Oids.basicConstraints]?.boolValue, true)
        XCTAssertEqual(crit?[X509Oids.subjectAltName]?.boolValue, false)
    }

    func test_getKeyUsage() {
        let ku = inspector.getKeyUsage(rootCertData)
        XCTAssertEqual(ku?["digitalSignature"]?.boolValue, true)
        XCTAssertEqual(ku?["keyCertSign"]?.boolValue, true)
        XCTAssertEqual(ku?["crlSign"]?.boolValue, true)
        XCTAssertEqual(ku?["keyEncipherment"]?.boolValue, false)
        XCTAssertEqual(ku?["keyAgreement"]?.boolValue, false)
    }

    func test_getBasicConstraints() {
        let bc = inspector.getBasicConstraints(rootCertData)
        XCTAssertEqual((bc?["isCA"] as? NSNumber)?.boolValue, true)
        XCTAssertEqual((bc?["pathLengthConstraint"] as? NSNumber)?.intValue, 3)
    }

    func test_getSubjectAltNames() {
        let sans = inspector.getSubjectAltNames(rootCertData)
        XCTAssertEqual(sans?.count, 3)
        let typesAndValues = (sans ?? []).map { ($0["type"] as? String ?? "", $0["value"] as? String ?? "") }
        XCTAssertTrue(typesAndValues.contains(where: { $0 == "dnsName" && $1 == "test.eudi.example" }))
        XCTAssertTrue(typesAndValues.contains(where: { $0 == "uri" && $1 == "https://test.eudi.example" }))
        XCTAssertTrue(typesAndValues.contains(where: { $0 == "email" && $1 == "test@eudi.example" }))
    }

    func test_getSubjectKeyIdentifier() {
        let ski = inspector.getSubjectKeyIdentifier(rootCertData)
        XCTAssertEqual(ski?.length, 20)
    }

    func test_getAuthorityKeyIdentifier() {
        let aki = inspector.getAuthorityKeyIdentifier(rootCertData)
        let keyId = aki?["keyIdentifier"] as? NSData
        XCTAssertEqual(keyId?.length, 20)
    }

    func test_getCertificatePolicies() {
        XCTAssertEqual(inspector.getCertificatePolicies(rootCertData), ["1.2.3.4.5.6"])
    }

    func test_getCrlDistributionPoints() {
        XCTAssertEqual(inspector.getCrlDistributionPoints(rootCertData), ["http://crl.eudi.example/test.crl"])
    }

    func test_getAuthorityInfoAccess() {
        let aia = inspector.getAuthorityInfoAccess(rootCertData)
        XCTAssertEqual(aia?["ocspUri"], "http://ocsp.eudi.example")
        XCTAssertEqual(aia?["caIssuersUri"], "http://ca.eudi.example/ca.cer")
    }

    func test_getQcStatements_absentReturnsNil() {
        // Fixture has no QCStatements extension
        XCTAssertNil(inspector.getQcStatements(rootCertData))
    }

    // MARK: - Public key info (via Security.framework)

    func test_getPublicKeyInfo_rsa2048() {
        let pki = inspector.getPublicKeyInfo(rootCertData)
        XCTAssertEqual(pki?["algorithm"] as? String, "RSA")
        XCTAssertEqual((pki?["keySize"] as? NSNumber)?.intValue, 2048)
    }
}
