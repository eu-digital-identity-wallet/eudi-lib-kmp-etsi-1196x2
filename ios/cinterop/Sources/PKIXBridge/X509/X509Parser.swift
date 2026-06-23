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

internal enum X509Parser {

    static func parse(_ der: Data) throws -> X509Certificate {
        let cert = try ASN1Parser.parse(der)
        let topChildren = try cert.sequence()
        guard !topChildren.isEmpty else {
            throw ASN1Error.invalidPrimitive(reason: "Certificate SEQUENCE is empty")
        }
        let tbsChildren = try topChildren[0].sequence()
        var idx = 0

        // version [0] EXPLICIT Version DEFAULT v1
        var version = 0
        if idx < tbsChildren.count, case .contextSpecific(0) = tbsChildren[idx].tag {
            let wrapper = tbsChildren[idx]
            let inner = try wrapper.requireConstructed()
            guard let first = inner.first else {
                throw ASN1Error.invalidPrimitive(reason: "Empty version wrapper")
            }
            let intBytes = try first.integerBytes()
            version = Int(intBytes.first ?? 0)
            idx += 1
        }

        // serialNumber INTEGER
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing serialNumber")
        }
        let serial = try tbsChildren[idx].integerBytes()
        idx += 1

        // signature AlgorithmIdentifier
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing signatureAlgorithm")
        }
        let sigAlgChildren = try tbsChildren[idx].sequence()
        let sigAlgOid = try sigAlgChildren[0].objectIdentifier()
        idx += 1

        // issuer Name
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing issuer")
        }
        let issuer = try parseName(tbsChildren[idx])
        idx += 1

        // validity Validity
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing validity")
        }
        let validityChildren = try tbsChildren[idx].sequence()
        guard validityChildren.count >= 2 else {
            throw ASN1Error.invalidPrimitive(reason: "Validity SEQUENCE must have 2 fields")
        }
        let notBefore = try validityChildren[0].anyTime()
        let notAfter = try validityChildren[1].anyTime()
        idx += 1

        // subject Name
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing subject")
        }
        let subject = try parseName(tbsChildren[idx])
        idx += 1

        // subjectPublicKeyInfo SubjectPublicKeyInfo — skip (use SecCertificateCopyKey from Security.framework)
        guard idx < tbsChildren.count else {
            throw ASN1Error.invalidPrimitive(reason: "Missing subjectPublicKeyInfo")
        }
        idx += 1

        // skip optional issuerUniqueID [1] IMPLICIT and subjectUniqueID [2] IMPLICIT
        while idx < tbsChildren.count {
            if case .contextSpecific(1) = tbsChildren[idx].tag {
                idx += 1
            } else if case .contextSpecific(2) = tbsChildren[idx].tag {
                idx += 1
            } else {
                break
            }
        }

        // extensions [3] EXPLICIT Extensions OPTIONAL
        var extensions: [X509Extension] = []
        if idx < tbsChildren.count, case .contextSpecific(3) = tbsChildren[idx].tag {
            let wrapper = tbsChildren[idx]
            let inner = try wrapper.requireConstructed()
            guard let extSeq = inner.first else {
                throw ASN1Error.invalidPrimitive(reason: "Empty extensions wrapper")
            }
            let extElements = try extSeq.sequence()
            extensions = try extElements.map(parseExtension)
        }

        return X509Certificate(
            version: version,
            serialNumber: serial,
            signatureAlgorithmOid: sigAlgOid,
            issuer: issuer,
            notBefore: notBefore,
            notAfter: notAfter,
            subject: subject,
            extensions: extensions
        )
    }

    static func parseName(_ element: ASN1Element) throws -> X509DistinguishedName {
        let rdnSequence = try element.sequence()
        var attributes: [X509DistinguishedName.Attribute] = []
        for rdn in rdnSequence {
            let atvs = try rdn.set()
            for atv in atvs {
                let pair = try atv.requireConstructed()
                guard pair.count >= 2 else { continue }
                let type = try pair[0].objectIdentifier()
                let value = try pair[1].directoryString()
                attributes.append(.init(oid: type, value: value))
            }
        }
        return X509DistinguishedName(attributes: attributes)
    }

    private static func parseExtension(_ element: ASN1Element) throws -> X509Extension {
        let children = try element.sequence()
        guard children.count >= 2 else {
            throw ASN1Error.invalidPrimitive(reason: "Extension SEQUENCE must have >=2 fields")
        }
        let oid = try children[0].objectIdentifier()
        var critical = false
        let valueElement: ASN1Element
        if children.count == 2 {
            valueElement = children[1]
        } else {
            critical = try children[1].bool()
            valueElement = children[2]
        }
        let valueBytes = try valueElement.octetString()
        return X509Extension(oid: oid, critical: critical, valueBytes: valueBytes)
    }
}
