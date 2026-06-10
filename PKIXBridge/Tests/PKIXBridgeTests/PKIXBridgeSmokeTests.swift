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

    func test_PKIXConfiguration_defaultDisablesRevocation() {
        let config = PKIXConfiguration()
        XCTAssertFalse(config.isRevocationEnabled)
    }

    func test_PKIXConfiguration_explicitInit() {
        let config = PKIXConfiguration(isRevocationEnabled: true)
        XCTAssertTrue(config.isRevocationEnabled)
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
