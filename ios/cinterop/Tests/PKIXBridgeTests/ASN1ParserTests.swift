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

final class ASN1ParserTests: XCTestCase {

    // MARK: - Length encoding

    func test_parseShortFormLength() throws {
        // SEQUENCE of length 3, containing INTEGER 0x42
        let bytes = Data([0x30, 0x03, 0x02, 0x01, 0x42])
        let element = try ASN1Parser.parse(bytes)
        XCTAssertTrue(element.isUniversal(ASN1UniversalTag.sequence))
        XCTAssertTrue(element.constructed)
        let children = try element.requireConstructed()
        XCTAssertEqual(children.count, 1)
        let intBytes = try children[0].integerBytes()
        XCTAssertEqual(intBytes, Data([0x42]))
    }

    func test_parseLongFormLength_oneByte() throws {
        // OCTET STRING of length 200
        let payload = Data(repeating: 0xAB, count: 200)
        var bytes = Data([0x04, 0x81, 0xC8])
        bytes.append(payload)
        let element = try ASN1Parser.parse(bytes)
        let octets = try element.octetString()
        XCTAssertEqual(octets.count, 200)
        XCTAssertEqual(octets.first, 0xAB)
    }

    func test_parseLongFormLength_twoBytes() throws {
        // OCTET STRING of length 300
        let payload = Data(repeating: 0xCD, count: 300)
        var bytes = Data([0x04, 0x82, 0x01, 0x2C])
        bytes.append(payload)
        let element = try ASN1Parser.parse(bytes)
        let octets = try element.octetString()
        XCTAssertEqual(octets.count, 300)
    }

    func test_rejectsIndefiniteLength() {
        // SEQUENCE with indefinite length marker
        let bytes = Data([0x30, 0x80, 0x00, 0x00])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .indefiniteLength)
        }
    }

    func test_rejectsNonCanonicalLength() {
        // Length encoded as 0x81 0x05 — 0x05 fits short form, so long form is non-canonical
        let bytes = Data([0x04, 0x81, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .nonCanonicalLength)
        }
    }

    func test_rejectsTruncatedValue() {
        // Declares length 5 but provides only 3 bytes
        let bytes = Data([0x04, 0x05, 0x01, 0x02, 0x03])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes)) { err in
            XCTAssertEqual(err as? ASN1Error, .unexpectedEnd)
        }
    }

    func test_rejectsTrailingBytes() {
        // Valid SEQUENCE followed by garbage
        let bytes = Data([0x30, 0x03, 0x02, 0x01, 0x42, 0xFF, 0xFF])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes))
    }

    // MARK: - Tag classes

    func test_parseContextSpecificTag() throws {
        // [0] EXPLICIT INTEGER 7
        let bytes = Data([0xA0, 0x03, 0x02, 0x01, 0x07])
        let element = try ASN1Parser.parse(bytes)
        XCTAssertTrue(element.isContextSpecific(0))
        XCTAssertTrue(element.constructed)
        let children = try element.requireConstructed()
        XCTAssertEqual(try children[0].integerBytes(), Data([0x07]))
    }

    // MARK: - BOOLEAN

    func test_decodeBoolean_true() throws {
        let bytes = Data([0x01, 0x01, 0xFF])
        XCTAssertTrue(try ASN1Parser.parse(bytes).bool())
    }

    func test_decodeBoolean_false() throws {
        let bytes = Data([0x01, 0x01, 0x00])
        XCTAssertFalse(try ASN1Parser.parse(bytes).bool())
    }

    func test_decodeBoolean_rejectsNonDerTrue() {
        // 0x01 is a valid BER true but not DER
        let bytes = Data([0x01, 0x01, 0x01])
        XCTAssertThrowsError(try ASN1Parser.parse(bytes).bool())
    }

    // MARK: - OID decoding

    func test_decodeOid_sha256WithRSAEncryption() throws {
        // OID 1.2.840.113549.1.1.11
        let bytes = Data([0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B])
        let oid = try ASN1Parser.parse(bytes).objectIdentifier()
        XCTAssertEqual(oid, "1.2.840.113549.1.1.11")
    }

    func test_decodeOid_keyUsage() throws {
        // OID 2.5.29.15
        let bytes = Data([0x06, 0x03, 0x55, 0x1D, 0x0F])
        let oid = try ASN1Parser.parse(bytes).objectIdentifier()
        XCTAssertEqual(oid, "2.5.29.15")
    }

    func test_decodeOid_basicConstraints() throws {
        // OID 2.5.29.19
        let bytes = Data([0x06, 0x03, 0x55, 0x1D, 0x13])
        let oid = try ASN1Parser.parse(bytes).objectIdentifier()
        XCTAssertEqual(oid, "2.5.29.19")
    }

    func test_decodeOid_etsiQcType() throws {
        // OID 0.4.0.194126.1.1 (id-etsi-qct-pid path).
        // Encoding: first = 0*40+4 = 0x04; then 0; then 194126 (base-128 digits 11/108/78 → 0x8B 0xEC 0x4E); then 1; then 1
        let bytes = Data([0x06, 0x07, 0x04, 0x00, 0x8B, 0xEC, 0x4E, 0x01, 0x01])
        let oid = try ASN1Parser.parse(bytes).objectIdentifier()
        XCTAssertEqual(oid, "0.4.0.194126.1.1")
    }

    // MARK: - Time decoding

    func test_decodeUtcTime_post2000() throws {
        // 230615120000Z = 2023-06-15 12:00:00 UTC
        let s = "230615120000Z"
        let bytes = Data([0x17, UInt8(s.count)]) + Data(s.utf8)
        let date = try ASN1Parser.parse(bytes).utcTime()
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let comps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        XCTAssertEqual(comps.year, 2023)
        XCTAssertEqual(comps.month, 6)
        XCTAssertEqual(comps.day, 15)
        XCTAssertEqual(comps.hour, 12)
    }

    func test_decodeUtcTime_pre2000() throws {
        // 950615120000Z = 1995-06-15
        let s = "950615120000Z"
        let bytes = Data([0x17, UInt8(s.count)]) + Data(s.utf8)
        let date = try ASN1Parser.parse(bytes).utcTime()
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        XCTAssertEqual(cal.component(.year, from: date), 1995)
    }

    func test_decodeGeneralizedTime() throws {
        // 20990101000000Z = 2099-01-01 00:00:00 UTC
        let s = "20990101000000Z"
        let bytes = Data([0x18, UInt8(s.count)]) + Data(s.utf8)
        let date = try ASN1Parser.parse(bytes).generalizedTime()
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        XCTAssertEqual(cal.component(.year, from: date), 2099)
    }

    func test_decodeUtcTime_rejectsMissingZ() {
        let s = "230615120000 " // 13 chars but trailing space, not Z
        let bytes = Data([0x17, UInt8(s.count)]) + Data(s.utf8)
        XCTAssertThrowsError(try ASN1Parser.parse(bytes).utcTime())
    }

    // MARK: - Strings

    func test_decodeUtf8String() throws {
        let payload = Data("héllo".utf8)
        var bytes = Data([0x0C, UInt8(payload.count)])
        bytes.append(payload)
        XCTAssertEqual(try ASN1Parser.parse(bytes).utf8String(), "héllo")
    }

    func test_decodePrintableString() throws {
        let payload = Data("DE".utf8)
        let bytes = Data([0x13, 0x02]) + payload
        XCTAssertEqual(try ASN1Parser.parse(bytes).printableString(), "DE")
    }

    func test_directoryString_dispatchesByTag() throws {
        let printable = Data([0x13, 0x02, 0x44, 0x45]) // "DE"
        XCTAssertEqual(try ASN1Parser.parse(printable).directoryString(), "DE")
        let utf8 = Data([0x0C, 0x03]) + Data("foo".utf8)
        XCTAssertEqual(try ASN1Parser.parse(utf8).directoryString(), "foo")
    }

    // MARK: - BIT STRING

    func test_decodeBitString() throws {
        // BIT STRING { 0x05 unused-bits, 0x03 0xE0 } → "01100000" with 5 unused → "011"
        let bytes = Data([0x03, 0x02, 0x05, 0x60])
        let (unused, data) = try ASN1Parser.parse(bytes).bitString()
        XCTAssertEqual(unused, 5)
        XCTAssertEqual(data, Data([0x60]))
    }

    // MARK: - Round-trip nested

    func test_nestedSequence() throws {
        // SEQUENCE { SEQUENCE { INTEGER 1, INTEGER 2 }, BOOLEAN true }
        let inner = Data([0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02])
        let outer = Data([0x30, UInt8(inner.count + 3)]) + inner + Data([0x01, 0x01, 0xFF])
        let element = try ASN1Parser.parse(outer)
        let children = try element.requireConstructed()
        XCTAssertEqual(children.count, 2)
        let innerChildren = try children[0].requireConstructed()
        XCTAssertEqual(innerChildren.count, 2)
        XCTAssertEqual(try innerChildren[0].integerBytes(), Data([0x01]))
        XCTAssertEqual(try innerChildren[1].integerBytes(), Data([0x02]))
        XCTAssertTrue(try children[1].bool())
    }
}
