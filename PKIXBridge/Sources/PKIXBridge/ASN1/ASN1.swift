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

internal enum ASN1Class: UInt8 {
    case universal = 0
    case application = 1
    case contextSpecific = 2
    case `private` = 3
}

internal enum ASN1Tag: Equatable {
    case universal(UInt32)
    case application(UInt32)
    case contextSpecific(UInt32)
    case `private`(UInt32)

    var number: UInt32 {
        switch self {
        case .universal(let n), .application(let n), .contextSpecific(let n), .private(let n):
            return n
        }
    }
}

internal enum ASN1UniversalTag {
    static let boolean: UInt32 = 0x01
    static let integer: UInt32 = 0x02
    static let bitString: UInt32 = 0x03
    static let octetString: UInt32 = 0x04
    static let null: UInt32 = 0x05
    static let objectIdentifier: UInt32 = 0x06
    static let utf8String: UInt32 = 0x0C
    static let printableString: UInt32 = 0x13
    static let teletexString: UInt32 = 0x14
    static let ia5String: UInt32 = 0x16
    static let utcTime: UInt32 = 0x17
    static let generalizedTime: UInt32 = 0x18
    static let bmpString: UInt32 = 0x1E
    static let sequence: UInt32 = 0x10
    static let set: UInt32 = 0x11
}

internal struct ASN1Element {
    let tag: ASN1Tag
    let constructed: Bool
    let content: Content

    enum Content {
        case primitive(Data)
        case constructed([ASN1Element])
    }

    var primitiveBytes: Data? {
        if case .primitive(let data) = content { return data }
        return nil
    }

    var children: [ASN1Element]? {
        if case .constructed(let elements) = content { return elements }
        return nil
    }

    func isUniversal(_ tagNumber: UInt32) -> Bool {
        if case .universal(let n) = tag { return n == tagNumber }
        return false
    }

    func isContextSpecific(_ tagNumber: UInt32) -> Bool {
        if case .contextSpecific(let n) = tag { return n == tagNumber }
        return false
    }
}

internal enum ASN1Error: Error, Equatable {
    case unexpectedEnd
    case trailingBytes(Int)
    case indefiniteLength
    case nonCanonicalLength
    case tagNumberTooLarge
    case lengthTooLarge
    case unexpectedTag(expected: String, actual: String)
    case invalidPrimitive(reason: String)
    case invalidString(reason: String)
    case invalidOid(reason: String)
    case invalidTime(reason: String)
    case constructedExpected
    case primitiveExpected
}
