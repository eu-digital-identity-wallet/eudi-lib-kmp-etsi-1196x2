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

final class PKIXValidatorChainTests: XCTestCase {

    private func loadFixture(_ name: String) throws -> NSData {
        guard let url = Bundle.module.url(forResource: name, withExtension: "der") else {
            throw XCTSkip("Fixture \(name).der not found")
        }
        return try Data(contentsOf: url) as NSData
    }

    func test_selfSignedRoot_validatesAgainstItself() throws {
        let root = try loadFixture("test-root-ca")
        let validator = PKIXValidator()
        let expect = expectation(description: "completion")
        var matched: NSData?
        var failure: Error?
        validator.validateCertificateChain(
            leafCertificate: root,
            intermediateCertificates: [],
            trustAnchors: [root]
        ) { anchorDer, error in
            matched = anchorDer
            failure = error
            expect.fulfill()
        }
        wait(for: [expect], timeout: 5.0)
        XCTAssertNil(failure, "self-signed CA should validate against itself")
        XCTAssertNotNil(matched, "matched anchor DER should be returned")
        XCTAssertEqual(matched, root, "matched anchor should equal the root DER")
    }
}
