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

final class PKIXBridgeSmokeTests: XCTestCase {

    func test_PKIXConfiguration_defaultEnablesRevocation() {
        let config = PKIXConfiguration()
        XCTAssertTrue(config.isRevocationEnabled)
    }

    func test_PKIXConfiguration_explicitInit() {
        let config = PKIXConfiguration(isRevocationEnabled: false)
        XCTAssertFalse(config.isRevocationEnabled)
    }

    func test_PKIXConfiguration_defaultRevocationMethodIsOCSP() {
        XCTAssertEqual(PKIXConfiguration().revocationMethod, .ocsp)
        XCTAssertEqual(PKIXConfiguration(isRevocationEnabled: true).revocationMethod, .ocsp)
    }

    func test_PKIXConfiguration_revocationMethodInit() {
        let config = PKIXConfiguration(isRevocationEnabled: true, revocationMethod: .crl)
        XCTAssertTrue(config.isRevocationEnabled)
        XCTAssertEqual(config.revocationMethod, .crl)
    }

    func test_PKIXRevocationMethod_ocspFlags() {
        let flags = PKIXRevocationMethod.ocsp.secRevocationFlags
        XCTAssertEqual(flags, CFOptionFlags(kSecRevocationOCSPMethod | kSecRevocationRequirePositiveResponse))
    }

    func test_PKIXRevocationMethod_crlFlags() {
        let flags = PKIXRevocationMethod.crl.secRevocationFlags
        XCTAssertEqual(flags, CFOptionFlags(kSecRevocationCRLMethod | kSecRevocationRequirePositiveResponse))
        XCTAssertEqual(flags & CFOptionFlags(kSecRevocationOCSPMethod), 0)
    }

    func test_PKIXRevocationMethod_anyPreferOCSPFlags() {
        let flags = PKIXRevocationMethod.anyPreferOCSP.secRevocationFlags
        XCTAssertEqual(
            flags,
            CFOptionFlags(kSecRevocationOCSPMethod | kSecRevocationCRLMethod | kSecRevocationRequirePositiveResponse)
        )
        XCTAssertEqual(flags & CFOptionFlags(kSecRevocationPreferCRL), 0)
    }

    func test_PKIXRevocationMethod_anyPreferCRLFlags() {
        let flags = PKIXRevocationMethod.anyPreferCRL.secRevocationFlags
        XCTAssertEqual(
            flags,
            CFOptionFlags(
                kSecRevocationOCSPMethod | kSecRevocationCRLMethod
                    | kSecRevocationPreferCRL | kSecRevocationRequirePositiveResponse
            )
        )
    }

    func test_PKIXValidator_acceptsCRLConfiguration() {
        let config = PKIXConfiguration(isRevocationEnabled: true, revocationMethod: .crl)
        let validator = PKIXValidator(configuration: config)
        XCTAssertNotNil(validator)
    }

    func test_PKIXValidator_canBeInstantiated() {
        let validator = PKIXValidator()
        XCTAssertNotNil(validator)
    }

    func test_PKIXCertificateInspector_canBeInstantiated() {
        let inspector = PKIXCertificateInspector()
        XCTAssertNotNil(inspector)
    }

    func test_PKIXValidator_rejectsEmptyTrustAnchors() {
        let validator = PKIXValidator()
        let expect = expectation(description: "completion")
        let leaf = "garbage" .data(using: .utf8)! as NSData
        validator.validateCertificateChain(
            leafCertificate: leaf,
            intermediateCertificates: [],
            trustAnchors: []
        ) { result, error in
            XCTAssertNil(result)
            XCTAssertNotNil(error)
            expect.fulfill()
        }
        wait(for: [expect], timeout: 1.0)
    }

    func test_PKIXValidator_rejectsInvalidLeafDER() {
        let validator = PKIXValidator()
        let expect = expectation(description: "completion")
        let garbage = Data(repeating: 0xFF, count: 32) as NSData
        validator.validateCertificateChain(
            leafCertificate: garbage,
            intermediateCertificates: [],
            trustAnchors: [garbage]
        ) { result, error in
            XCTAssertNil(result)
            XCTAssertNotNil(error)
            let nsError = error! as NSError
            XCTAssertEqual(nsError.domain, "eu.europa.ec.eudi.etsi1196x2.consultation.pkix")
            expect.fulfill()
        }
        wait(for: [expect], timeout: 1.0)
    }
}
