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

final class QcTypeDecoderTests: XCTestCase {

    func test_validQcTypeWithOneOid_isReturned() throws {
        let statements = try X509ExtensionDecoder.decodeQcStatements(qcStatements(qcType([pidQcType])))

        XCTAssertEqual(statements, [.qcType(typeIdentifier: "0.4.0.194126.1.1")])
    }

    func test_qcTypeWithMultipleOids_throws() {
        XCTAssertThrowsError(
            try X509ExtensionDecoder.decodeQcStatements(qcStatements(qcType([pidQcType, walletQcType])))
        )
    }

    func test_qcTypeWithNoOids_throws() {
        XCTAssertThrowsError(try X509ExtensionDecoder.decodeQcStatements(qcStatements(qcType([]))))
    }

    func test_qcTypeWithNonOidValue_throws() {
        XCTAssertThrowsError(
            try X509ExtensionDecoder.decodeQcStatements(qcStatements(qcType([Data([0x05, 0x00])])))
        )
    }

    private let pidQcType = Data([0x06, 0x07, 0x04, 0x00, 0x8B, 0xEC, 0x4E, 0x01, 0x01])
    private let walletQcType = Data([0x06, 0x07, 0x04, 0x00, 0x8B, 0xEC, 0x4E, 0x01, 0x02])

    private func qcStatements(_ statements: Data...) -> Data {
        derSequence(statements)
    }

    private func qcType(_ typeIdentifiers: [Data]) -> Data {
        let statementId = Data([0x06, 0x05, 0x04, 0x00, 0x8E, 0x46, 0x01, 0x06])
        return derSequence([statementId, derSequence(typeIdentifiers)])
    }

    private func derSequence(_ elements: [Data]) -> Data {
        let content = elements.reduce(into: Data()) { $0.append($1) }
        return Data([0x30, UInt8(content.count)]) + content
    }
}
