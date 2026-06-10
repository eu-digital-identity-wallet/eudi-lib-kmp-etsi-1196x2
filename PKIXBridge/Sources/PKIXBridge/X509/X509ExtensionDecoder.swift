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

internal enum X509ExtensionDecoder {

    // MARK: - 2.5.29.15 KeyUsage

    /// KeyUsage ::= BIT STRING — bit 0 is digitalSignature, bit 8 is decipherOnly.
    static func decodeKeyUsage(_ value: Data) throws -> KeyUsageBits {
        let (_, bits) = try ASN1Parser.parse(value).bitString()
        func bit(_ index: Int) -> Bool {
            let byteIndex = index / 8
            let bitIndex = index % 8
            guard byteIndex < bits.count else { return false }
            let mask: UInt8 = 0x80 >> bitIndex
            return (bits[bits.startIndex + byteIndex] & mask) != 0
        }
        return KeyUsageBits(
            digitalSignature: bit(0),
            nonRepudiation: bit(1),
            keyEncipherment: bit(2),
            dataEncipherment: bit(3),
            keyAgreement: bit(4),
            keyCertSign: bit(5),
            crlSign: bit(6),
            encipherOnly: bit(7),
            decipherOnly: bit(8)
        )
    }

    // MARK: - 2.5.29.19 BasicConstraints

    /// BasicConstraints ::= SEQUENCE { cA BOOLEAN DEFAULT FALSE, pathLenConstraint INTEGER (0..MAX) OPTIONAL }
    static func decodeBasicConstraints(_ value: Data) throws -> BasicConstraintsInfo {
        let seq = try ASN1Parser.parse(value).sequence()
        var isCa = false
        var pathLen: Int? = nil
        for elem in seq {
            switch elem.tag {
            case .universal(ASN1UniversalTag.boolean):
                isCa = try elem.bool()
            case .universal(ASN1UniversalTag.integer):
                let bytes = try elem.integerBytes()
                pathLen = Self.intValue(from: bytes)
            default:
                continue
            }
        }
        return BasicConstraintsInfo(isCa: isCa, pathLenConstraint: pathLen)
    }

    // MARK: - 2.5.29.17 SubjectAlternativeName

    /// SubjectAltName ::= GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
    static func decodeSubjectAltName(_ value: Data) throws -> [SubjectAltName] {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        return seq.compactMap { decodeGeneralName($0) }
    }

    private static func decodeGeneralName(_ element: ASN1Element) -> SubjectAltName? {
        switch element.tag {
        case .contextSpecific(0):
            // otherName: SEQUENCE { type-id OID, value [0] EXPLICIT ANY }
            guard let children = element.children, let first = children.first,
                  let oid = try? first.objectIdentifier() else { return nil }
            return .otherName(typeId: oid)
        case .contextSpecific(1):
            guard let bytes = element.primitiveBytes,
                  let s = String(data: bytes, encoding: .ascii) else { return nil }
            return .rfc822Name(s)
        case .contextSpecific(2):
            guard let bytes = element.primitiveBytes,
                  let s = String(data: bytes, encoding: .ascii) else { return nil }
            return .dnsName(s)
        case .contextSpecific(6):
            guard let bytes = element.primitiveBytes,
                  let s = String(data: bytes, encoding: .ascii) else { return nil }
            return .uri(s)
        case .contextSpecific(7):
            guard let bytes = element.primitiveBytes else { return nil }
            return .ipAddress(bytes)
        case .contextSpecific(8):
            guard let bytes = element.primitiveBytes,
                  let oid = try? ASN1Element.decodeOid(bytes) else { return nil }
            return .registeredId(oid)
        default:
            return nil
        }
    }

    // MARK: - 2.5.29.35 AuthorityKeyIdentifier

    /// AuthorityKeyIdentifier ::= SEQUENCE {
    ///     keyIdentifier             [0] IMPLICIT OCTET STRING OPTIONAL,
    ///     authorityCertIssuer       [1] IMPLICIT GeneralNames OPTIONAL,
    ///     authorityCertSerialNumber [2] IMPLICIT INTEGER OPTIONAL }
    static func decodeAuthorityKeyIdentifier(_ value: Data) throws -> AuthorityKeyIdentifierInfo {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        var keyId: Data? = nil
        var serial: Data? = nil
        for elem in seq {
            switch elem.tag {
            case .contextSpecific(0):
                keyId = elem.primitiveBytes
            case .contextSpecific(2):
                serial = elem.primitiveBytes
            default:
                continue
            }
        }
        return AuthorityKeyIdentifierInfo(keyIdentifier: keyId, authorityCertSerialNumber: serial)
    }

    // MARK: - 2.5.29.14 SubjectKeyIdentifier

    /// SubjectKeyIdentifier ::= OCTET STRING
    static func decodeSubjectKeyIdentifier(_ value: Data) throws -> Data {
        try ASN1Parser.parse(value).octetString()
    }

    // MARK: - 2.5.29.31 CRLDistributionPoints

    /// CRLDistributionPoints ::= SEQUENCE SIZE (1..MAX) OF DistributionPoint
    /// DistributionPoint ::= SEQUENCE { distributionPoint [0] EXPLICIT DistributionPointName OPTIONAL, ... }
    /// DistributionPointName ::= CHOICE { fullName [0] IMPLICIT GeneralNames, ... }
    static func decodeCrlDistributionPoints(_ value: Data) throws -> [String] {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        var uris: [String] = []
        for dp in seq {
            guard let dpChildren = dp.children else { continue }
            for child in dpChildren {
                guard case .contextSpecific(0) = child.tag, let nameAlternatives = child.children else { continue }
                for name in nameAlternatives {
                    guard case .contextSpecific(0) = name.tag, let generalNames = name.children else { continue }
                    for gn in generalNames {
                        if case .contextSpecific(6) = gn.tag, let bytes = gn.primitiveBytes,
                           let s = String(data: bytes, encoding: .ascii) {
                            uris.append(s)
                        }
                    }
                }
            }
        }
        return uris
    }

    // MARK: - 1.3.6.1.5.5.7.1.1 AuthorityInformationAccess

    /// AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
    /// AccessDescription ::= SEQUENCE { accessMethod OID, accessLocation GeneralName }
    static func decodeAuthorityInformationAccess(_ value: Data) throws -> AuthorityInformationAccessInfo {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        var caIssuersUri: String? = nil
        var ocspUri: String? = nil
        for ad in seq {
            let children = try ad.requireConstructed()
            guard children.count >= 2 else { continue }
            let method = try children[0].objectIdentifier()
            let location = children[1]
            guard case .contextSpecific(6) = location.tag,
                  let bytes = location.primitiveBytes,
                  let uri = String(data: bytes, encoding: .ascii) else { continue }
            switch method {
            case X509Oids.aiaCaIssuers: caIssuersUri = uri
            case X509Oids.aiaOcsp: ocspUri = uri
            default: continue
            }
        }
        return AuthorityInformationAccessInfo(caIssuersUri: caIssuersUri, ocspUri: ocspUri)
    }

    // MARK: - 2.5.29.32 CertificatePolicies

    /// CertificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
    /// PolicyInformation ::= SEQUENCE { policyIdentifier OID, policyQualifiers SEQUENCE OF ... OPTIONAL }
    static func decodeCertificatePolicies(_ value: Data) throws -> [String] {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        return try seq.compactMap { pi in
            guard let first = try pi.requireConstructed().first else { return nil }
            return try first.objectIdentifier()
        }
    }

    // MARK: - 1.3.6.1.5.5.7.1.3 QCStatements

    /// QCStatements ::= SEQUENCE OF QCStatement
    /// QCStatement ::= SEQUENCE { statementId OID, statementInfo ANY DEFINED BY statementId OPTIONAL }
    static func decodeQcStatements(_ value: Data) throws -> [QcStatement] {
        let seq = try ASN1Parser.parse(value).requireConstructed()
        var result: [QcStatement] = []
        for qs in seq {
            let children = try qs.requireConstructed()
            guard let stmtId = try children.first?.objectIdentifier() else { continue }
            if stmtId == X509Oids.etsiQcsQcType, children.count >= 2 {
                // statementInfo = SEQUENCE OF OBJECT IDENTIFIER (the QcTypes)
                let typeSeq = try children[1].requireConstructed()
                for typeElem in typeSeq {
                    if let typeOid = try? typeElem.objectIdentifier() {
                        result.append(.qcType(typeIdentifier: typeOid))
                    }
                }
            } else {
                result.append(.other(statementId: stmtId))
            }
        }
        return result
    }

    // MARK: - Helpers

    /// Decodes a DER INTEGER's two's-complement big-endian bytes into a Swift Int.
    /// Returns nil if the value does not fit. Used for small positive integers like pathLenConstraint.
    private static func intValue(from bytes: Data) -> Int? {
        guard !bytes.isEmpty, bytes.count <= 8 else { return nil }
        var result: Int = 0
        let isNegative = (bytes[bytes.startIndex] & 0x80) != 0
        for b in bytes {
            result = (result << 8) | Int(b)
        }
        if isNegative {
            // sign-extend
            let bitCount = bytes.count * 8
            result -= 1 << bitCount
        }
        return result
    }
}
