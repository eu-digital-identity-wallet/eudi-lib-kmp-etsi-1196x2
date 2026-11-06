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

final class StubRevocationChecker: NSObject, PKIXRevocationChecker {

    private let status: PKIXRevocationStatus
    private(set) var checked: [(certificate: Data, issuer: Data)] = []

    init(_ status: PKIXRevocationStatus) {
        self.status = status
    }

    func revocationStatus(of certificate: NSData, issuer: NSData) -> PKIXRevocationResult {
        checked.append((certificate as Data, issuer as Data))
        return PKIXRevocationResult(status: status, reason: "stub \(status.rawValue)")
    }
}

/// Custom CRL checkers and the per-certificate order of the mixed methods, with the `crl-test-*` fixtures.
final class PKIXRevocationCheckerTests: XCTestCase {

    private func fixture(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "der") else {
            throw XCTSkip("Fixture \(name).der not found")
        }
        return try Data(contentsOf: url)
    }

    /// Without `ocsp`, the validator is built with the public initializer.
    private func validate(
        method: PKIXRevocationMethod,
        crl: PKIXRevocationChecker,
        ocsp: PKIXRevocationChecker? = nil
    ) throws -> Error? {
        let configuration = PKIXConfiguration(isRevocationEnabled: true, revocationMethod: method)
        let validator = ocsp.map { PKIXValidator(configuration: configuration, crlChecker: crl, ocspChecker: $0) }
            ?? PKIXValidator(configuration: configuration, crlChecker: crl)
        let expect = expectation(description: "completion")
        var failure: Error?
        validator.validateCertificateChain(
            leafCertificate: try fixture("crl-test-valid-leaf") as NSData,
            intermediateCertificates: [],
            trustAnchors: [try fixture("crl-test-ca") as NSData]
        ) { _, error in
            failure = error
            expect.fulfill()
        }
        wait(for: [expect], timeout: 10)
        return failure
    }

    func test_customCrlChecker_receivesCertificateAndIssuer() throws {
        let crl = StubRevocationChecker(.good)
        XCTAssertNil(try validate(method: .crl, crl: crl))
        XCTAssertEqual(crl.checked.count, 1)
        XCTAssertEqual(crl.checked.first?.certificate, try fixture("crl-test-valid-leaf"))
        XCTAssertEqual(crl.checked.first?.issuer, try fixture("crl-test-ca"))
    }

    func test_crlMethodsWithoutChecker_areNotTrusted() throws {
        for method in [PKIXRevocationMethod.crl, .anyPreferOCSP, .anyPreferCRL] {
            let validator = PKIXValidator(configuration: PKIXConfiguration(isRevocationEnabled: true, revocationMethod: method))
            let expect = expectation(description: "completion")
            validator.validateCertificateChain(
                leafCertificate: try fixture("crl-test-valid-leaf") as NSData,
                intermediateCertificates: [],
                trustAnchors: [try fixture("crl-test-ca") as NSData]
            ) { anchor, error in
                XCTAssertNil(anchor)
                XCTAssertTrue((error?.localizedDescription ?? "").contains("requires a CRL checker"))
                expect.fulfill()
            }
            wait(for: [expect], timeout: 10)
        }
    }

    func test_customCrlChecker_revoked_isNotTrusted() throws {
        XCTAssertNotNil(try validate(method: .crl, crl: StubRevocationChecker(.revoked), ocsp: StubRevocationChecker(.good)))
    }

    func test_anyPreferCRL_crlUnknown_fallsBackToOcsp() throws {
        let ocsp = StubRevocationChecker(.good)
        XCTAssertNil(try validate(method: .anyPreferCRL, crl: StubRevocationChecker(.unknown), ocsp: ocsp))
        XCTAssertEqual(ocsp.checked.count, 1)
    }

    func test_anyPreferCRL_crlRevoked_isNotTrustedWithoutOcsp() throws {
        let ocsp = StubRevocationChecker(.good)
        XCTAssertNotNil(try validate(method: .anyPreferCRL, crl: StubRevocationChecker(.revoked), ocsp: ocsp))
        XCTAssertTrue(ocsp.checked.isEmpty)
    }

    func test_anyPreferOCSP_ocspGood_skipsCrl() throws {
        let crl = StubRevocationChecker(.revoked)
        XCTAssertNil(try validate(method: .anyPreferOCSP, crl: crl, ocsp: StubRevocationChecker(.good)))
        XCTAssertTrue(crl.checked.isEmpty)
    }

    func test_anyPreferOCSP_ocspUnknown_fallsBackToCrl() throws {
        let crl = StubRevocationChecker(.good)
        XCTAssertNil(try validate(method: .anyPreferOCSP, crl: crl, ocsp: StubRevocationChecker(.unknown)))
        XCTAssertEqual(crl.checked.count, 1)
    }

    func test_mixed_bothUnknown_isNotTrusted() throws {
        XCTAssertNotNil(try validate(method: .anyPreferOCSP, crl: StubRevocationChecker(.unknown), ocsp: StubRevocationChecker(.unknown)))
        XCTAssertNotNil(try validate(method: .anyPreferCRL, crl: StubRevocationChecker(.unknown), ocsp: StubRevocationChecker(.unknown)))
    }
}
