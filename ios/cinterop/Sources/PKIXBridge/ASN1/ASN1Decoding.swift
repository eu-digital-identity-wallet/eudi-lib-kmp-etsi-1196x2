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

import Foundation

extension ASN1Element {

    func requirePrimitive() throws -> Data {
        guard case .primitive(let data) = content else { throw ASN1Error.primitiveExpected }
        return data
    }

    func requireConstructed() throws -> [ASN1Element] {
        guard case .constructed(let elements) = content else { throw ASN1Error.constructedExpected }
        return elements
    }

    func requireUniversal(_ tagNumber: UInt32) throws {
        guard case .universal(let n) = tag, n == tagNumber else {
            throw ASN1Error.unexpectedTag(expected: "universal \(tagNumber)", actual: tagDescription())
        }
    }

    func sequence() throws -> [ASN1Element] {
        try requireUniversal(ASN1UniversalTag.sequence)
        guard constructed else { throw ASN1Error.constructedExpected }
        return try requireConstructed()
    }

    func set() throws -> [ASN1Element] {
        try requireUniversal(ASN1UniversalTag.set)
        guard constructed else { throw ASN1Error.constructedExpected }
        return try requireConstructed()
    }

    func bool() throws -> Bool {
        try requireUniversal(ASN1UniversalTag.boolean)
        let bytes = try requirePrimitive()
        guard bytes.count == 1 else {
            throw ASN1Error.invalidPrimitive(reason: "BOOLEAN must be one byte, got \(bytes.count)")
        }
        // DER: TRUE must be exactly 0xFF; FALSE must be 0x00
        switch bytes[bytes.startIndex] {
        case 0x00: return false
        case 0xFF: return true
        default:
            throw ASN1Error.invalidPrimitive(reason: "DER BOOLEAN must be 0x00 or 0xFF")
        }
    }

    func integerBytes() throws -> Data {
        try requireUniversal(ASN1UniversalTag.integer)
        return try requirePrimitive()
    }

    func octetString() throws -> Data {
        try requireUniversal(ASN1UniversalTag.octetString)
        return try requirePrimitive()
    }

    /// Returns (unusedBitsInLastByte, dataBytes). For most cert uses the unused-bits prefix is dropped.
    func bitString() throws -> (unusedBits: Int, data: Data) {
        try requireUniversal(ASN1UniversalTag.bitString)
        let bytes = try requirePrimitive()
        guard let first = bytes.first else {
            throw ASN1Error.invalidPrimitive(reason: "BIT STRING is empty")
        }
        let unused = Int(first)
        if unused > 7 {
            throw ASN1Error.invalidPrimitive(reason: "BIT STRING unused-bits must be 0..7, got \(unused)")
        }
        let rest = bytes.dropFirst()
        return (unused, Data(rest))
    }

    func objectIdentifier() throws -> String {
        try requireUniversal(ASN1UniversalTag.objectIdentifier)
        let bytes = try requirePrimitive()
        return try ASN1Element.decodeOid(bytes)
    }

    func utf8String() throws -> String {
        try requireUniversal(ASN1UniversalTag.utf8String)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .utf8) else {
            throw ASN1Error.invalidString(reason: "invalid UTF8String")
        }
        return s
    }

    func printableString() throws -> String {
        try requireUniversal(ASN1UniversalTag.printableString)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .ascii) else {
            throw ASN1Error.invalidString(reason: "invalid PrintableString")
        }
        return s
    }

    func ia5String() throws -> String {
        try requireUniversal(ASN1UniversalTag.ia5String)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .ascii) else {
            throw ASN1Error.invalidString(reason: "invalid IA5String")
        }
        return s
    }

    func bmpString() throws -> String {
        try requireUniversal(ASN1UniversalTag.bmpString)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .utf16BigEndian) else {
            throw ASN1Error.invalidString(reason: "invalid BMPString")
        }
        return s
    }

    /// Decodes any of the X.509 directory-string types into a Swift String. Best-effort for legacy TeletexString.
    func directoryString() throws -> String {
        switch tag {
        case .universal(ASN1UniversalTag.utf8String):
            return try utf8String()
        case .universal(ASN1UniversalTag.printableString):
            return try printableString()
        case .universal(ASN1UniversalTag.ia5String):
            return try ia5String()
        case .universal(ASN1UniversalTag.bmpString):
            return try bmpString()
        case .universal(ASN1UniversalTag.teletexString):
            let bytes = try requirePrimitive()
            // TeletexString in EU certs is typically just Latin-1.
            return String(data: bytes, encoding: .isoLatin1)
                ?? String(data: bytes, encoding: .ascii)
                ?? ""
        default:
            throw ASN1Error.invalidString(reason: "unexpected string tag \(tagDescription())")
        }
    }

    func utcTime() throws -> Date {
        try requireUniversal(ASN1UniversalTag.utcTime)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .ascii) else {
            throw ASN1Error.invalidTime(reason: "UTCTime is not ASCII")
        }
        return try ASN1Element.parseUtcTime(s)
    }

    func generalizedTime() throws -> Date {
        try requireUniversal(ASN1UniversalTag.generalizedTime)
        let bytes = try requirePrimitive()
        guard let s = String(data: bytes, encoding: .ascii) else {
            throw ASN1Error.invalidTime(reason: "GeneralizedTime is not ASCII")
        }
        return try ASN1Element.parseGeneralizedTime(s)
    }

    /// Decodes any cert-validity time (UTCTime or GeneralizedTime).
    func anyTime() throws -> Date {
        switch tag {
        case .universal(ASN1UniversalTag.utcTime): return try utcTime()
        case .universal(ASN1UniversalTag.generalizedTime): return try generalizedTime()
        default:
            throw ASN1Error.invalidTime(reason: "unexpected time tag \(tagDescription())")
        }
    }

    func tagDescription() -> String {
        switch tag {
        case .universal(let n): return "UNIVERSAL \(n)"
        case .application(let n): return "APPLICATION \(n)"
        case .contextSpecific(let n): return "CONTEXT \(n)"
        case .private(let n): return "PRIVATE \(n)"
        }
    }

    // MARK: - OID decoding (BER subidentifier encoding)

    static func decodeOid(_ bytes: Data) throws -> String {
        guard let first = bytes.first else {
            throw ASN1Error.invalidOid(reason: "empty OID")
        }
        var components: [UInt64] = []
        let firstByte = UInt64(first)
        if firstByte < 80 {
            components.append(firstByte / 40)
            components.append(firstByte % 40)
        } else {
            components.append(2)
            components.append(firstByte - 80)
        }
        var value: UInt64 = 0
        var inProgress = false
        for b in bytes.dropFirst() {
            value = (value << 7) | UInt64(b & 0x7F)
            inProgress = true
            if (b & 0x80) == 0 {
                components.append(value)
                value = 0
                inProgress = false
            }
        }
        if inProgress {
            throw ASN1Error.invalidOid(reason: "truncated OID encoding")
        }
        return components.map(String.init).joined(separator: ".")
    }

    // MARK: - Time decoding

    /// UTCTime: YYMMDDHHMMSSZ (13 chars, Z mandatory in DER). Per RFC 5280, YY 00-49 -> 20YY; 50-99 -> 19YY.
    static func parseUtcTime(_ s: String) throws -> Date {
        guard s.count == 13, s.hasSuffix("Z") else {
            throw ASN1Error.invalidTime(reason: "UTCTime must be 13 chars YYMMDDHHMMSSZ, got '\(s)'")
        }
        guard let yy = Int(s.prefix(2)) else {
            throw ASN1Error.invalidTime(reason: "UTCTime year not numeric in '\(s)'")
        }
        let fullYear = yy < 50 ? 2000 + yy : 1900 + yy
        let rest = s.dropFirst(2) // MMDDHHMMSSZ
        let normalized = "\(fullYear)" + rest
        return try parseDateString(normalized, format: "yyyyMMddHHmmss'Z'")
    }

    /// GeneralizedTime: YYYYMMDDHHMMSSZ (15 chars). Per RFC 5280 cert validity rules, must be UTC with no fractional seconds.
    static func parseGeneralizedTime(_ s: String) throws -> Date {
        guard s.count == 15, s.hasSuffix("Z") else {
            throw ASN1Error.invalidTime(reason: "GeneralizedTime (cert validity) must be 15 chars YYYYMMDDHHMMSSZ, got '\(s)'")
        }
        return try parseDateString(s, format: "yyyyMMddHHmmss'Z'")
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.timeZone = TimeZone(secondsFromGMT: 0)
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private static func parseDateString(_ s: String, format: String) throws -> Date {
        timeFormatter.dateFormat = format
        guard let date = timeFormatter.date(from: s) else {
            throw ASN1Error.invalidTime(reason: "could not parse '\(s)' with format '\(format)'")
        }
        return date
    }
}
