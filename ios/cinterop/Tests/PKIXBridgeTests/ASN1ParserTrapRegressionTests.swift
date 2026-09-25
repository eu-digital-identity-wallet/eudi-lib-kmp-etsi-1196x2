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

// Regression tests for PAR_ETSI_KMP_19: attacker-crafted DER must throw, never
// trap the process, before any trust decision is made.

import XCTest
@testable import PKIXBridge

final class ASN1ParserTrapRegressionTests: XCTestCase {

    // MARK: - Length overflow / underflow

    /// Eight `0xFF` length bytes previously accumulated into a signed Int of `-1`,
    /// bypassing the bounds guard and triggering a Range precondition abort.
    func test_lengthAllOnes_isRejected_notTrap() {
        let bytes = Data([0x04, 0x88, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .lengthTooLarge)
        }
    }

    /// Any long-form length with the high bit set exceeds `Int.max` and must be rejected
    /// so the subsequent `offset + length` cannot alias to a negative value.
    func test_lengthWithHighBitSet_isRejected_notTrap() {
        let bytes = Data([0x04, 0x88, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .lengthTooLarge)
        }
    }

    /// A length just short of Int.max previously trapped in `cursor.offset + length`.
    /// It must now surface as `unexpectedEnd` because obviously no such payload follows.
    func test_lengthNearIntMax_reportsUnexpectedEnd_notTrap() {
        // 0x88 = long form, 8 length bytes. 0x7F...FF fits in Int.max exactly.
        let bytes = Data([0x04, 0x88, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .unexpectedEnd)
        }
    }

    /// A length beyond the buffer remains a plain parse error, no trap.
    func test_lengthExceedsBuffer_reportsUnexpectedEnd() {
        // Declares 1000 bytes, provides zero.
        let bytes = Data([0x04, 0x82, 0x03, 0xE8])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .unexpectedEnd)
        }
    }

    // MARK: - Depth-limited recursion

    /// Kilobytes of nested constructed headers must return an error instead of
    /// exhausting the stack.
    func test_deeplyNestedSequences_rejectedByDepthCap() {
        let bytes = nestedSequences(depth: ASN1Parser.maxDepth + 8)
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .nestingTooDeep)
        }
    }

    /// The deepest permitted call is at `depth = maxDepth - 1`, so exactly `maxDepth`
    /// nesting levels must still parse.
    func test_nestingAtDepthCap_parses() throws {
        _ = try ASN1Parser.parse(nestedSequences(depth: ASN1Parser.maxDepth - 1))
    }

    /// Builds `depth+1` levels: an innermost empty SEQUENCE wrapped `depth` times.
    /// Uses 2-byte long-form length everywhere so wrappers stay well-formed as they grow
    /// past 127 bytes.
    private func nestedSequences(depth: Int) -> Data {
        var bytes = Data([0x30, 0x82, 0x00, 0x00])
        for _ in 0..<depth {
            let len = bytes.count
            let hi = UInt8((len >> 8) & 0xFF)
            let lo = UInt8(len & 0xFF)
            bytes = Data([0x30, 0x82, hi, lo]) + bytes
        }
        return bytes
    }

    // MARK: - X509Parser: empty AlgorithmIdentifier

    /// PAR_ETSI_KMP_19: an empty `signatureAlgorithm` SEQUENCE previously reached
    /// `sigAlgChildren[0]` and aborted. Must now throw a normal parse error.
    func test_emptySignatureAlgorithm_inTBS_throws() {
        // tbsCertificate = SEQUENCE { [0] EXPLICIT INTEGER 2, INTEGER 1, SEQUENCE (empty) }
        let versionWrapper = Data([0xA0, 0x03, 0x02, 0x01, 0x02])
        let serial = Data([0x02, 0x01, 0x01])
        let emptySigAlg = Data([0x30, 0x00])
        let tbsContent = versionWrapper + serial + emptySigAlg
        let tbs = Data([0x30, UInt8(tbsContent.count)]) + tbsContent
        let cert = Data([0x30, UInt8(tbs.count)]) + tbs

        XCTAssertThrowsError(try X509Parser.parse(cert)) { err in
            guard let asn1 = err as? ASN1Error, case .invalidPrimitive = asn1 else {
                return XCTFail("expected invalidPrimitive, got \(err)")
            }
        }
    }
}
