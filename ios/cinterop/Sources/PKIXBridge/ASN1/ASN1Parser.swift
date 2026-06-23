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

internal enum ASN1Parser {

    static func parse(_ data: Data) throws -> ASN1Element {
        var cursor = Cursor(data: data, offset: data.startIndex)
        let element = try parseElement(&cursor)
        guard cursor.offset == data.endIndex else {
            throw ASN1Error.trailingBytes(data.endIndex - cursor.offset)
        }
        return element
    }

    static func parseAll(_ data: Data) throws -> [ASN1Element] {
        var cursor = Cursor(data: data, offset: data.startIndex)
        var elements: [ASN1Element] = []
        while cursor.offset < data.endIndex {
            elements.append(try parseElement(&cursor))
        }
        return elements
    }

    private struct Cursor {
        let data: Data
        var offset: Int
    }

    private static func parseElement(_ cursor: inout Cursor) throws -> ASN1Element {
        let (tag, constructed) = try parseTag(&cursor)
        let length = try parseLength(&cursor)
        guard cursor.offset + length <= cursor.data.endIndex else {
            throw ASN1Error.unexpectedEnd
        }
        let valueRange = cursor.offset..<(cursor.offset + length)
        let valueBytes = cursor.data.subdata(in: valueRange)
        cursor.offset += length

        let content: ASN1Element.Content
        if constructed {
            content = .constructed(try parseAll(valueBytes))
        } else {
            content = .primitive(valueBytes)
        }
        return ASN1Element(tag: tag, constructed: constructed, content: content)
    }

    private static func parseTag(_ cursor: inout Cursor) throws -> (ASN1Tag, Bool) {
        guard cursor.offset < cursor.data.endIndex else { throw ASN1Error.unexpectedEnd }
        let first = cursor.data[cursor.offset]
        cursor.offset += 1
        let classBits = (first & 0xC0) >> 6
        let constructed = (first & 0x20) != 0
        var tagNumber: UInt32 = UInt32(first & 0x1F)
        if tagNumber == 0x1F {
            tagNumber = 0
            var bytesRead = 0
            while true {
                guard cursor.offset < cursor.data.endIndex else { throw ASN1Error.unexpectedEnd }
                let b = cursor.data[cursor.offset]
                cursor.offset += 1
                bytesRead += 1
                if bytesRead > 4 { throw ASN1Error.tagNumberTooLarge }
                let chunk = UInt32(b & 0x7F)
                tagNumber = (tagNumber << 7) | chunk
                if (b & 0x80) == 0 { break }
            }
        }
        let tag: ASN1Tag
        switch classBits {
        case 0: tag = .universal(tagNumber)
        case 1: tag = .application(tagNumber)
        case 2: tag = .contextSpecific(tagNumber)
        case 3: tag = .private(tagNumber)
        default: throw ASN1Error.unexpectedEnd
        }
        return (tag, constructed)
    }

    private static func parseLength(_ cursor: inout Cursor) throws -> Int {
        guard cursor.offset < cursor.data.endIndex else { throw ASN1Error.unexpectedEnd }
        let first = cursor.data[cursor.offset]
        cursor.offset += 1
        if first < 0x80 {
            return Int(first)
        }
        if first == 0x80 {
            throw ASN1Error.indefiniteLength
        }
        let byteCount = Int(first & 0x7F)
        if byteCount > 8 {
            throw ASN1Error.lengthTooLarge
        }
        guard cursor.offset + byteCount <= cursor.data.endIndex else {
            throw ASN1Error.unexpectedEnd
        }
        var length: Int = 0
        for _ in 0..<byteCount {
            length = (length << 8) | Int(cursor.data[cursor.offset])
            cursor.offset += 1
        }
        // DER canonical length: long form must encode at least 128
        if byteCount == 1 && length < 128 {
            throw ASN1Error.nonCanonicalLength
        }
        return length
    }
}
