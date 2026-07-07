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

internal enum X509Oids {
    // X.509 v3 extensions (RFC 5280)
    static let keyUsage: String = "2.5.29.15"
    static let subjectAltName: String = "2.5.29.17"
    static let basicConstraints: String = "2.5.29.19"
    static let crlDistributionPoints: String = "2.5.29.31"
    static let certificatePolicies: String = "2.5.29.32"
    static let authorityKeyIdentifier: String = "2.5.29.35"
    static let subjectKeyIdentifier: String = "2.5.29.14"

    // PKIX private extensions
    static let authorityInformationAccess: String = "1.3.6.1.5.5.7.1.1"
    static let qcStatements: String = "1.3.6.1.5.5.7.1.3"

    // AIA access methods
    static let aiaCaIssuers: String = "1.3.6.1.5.5.7.48.2"
    static let aiaOcsp: String = "1.3.6.1.5.5.7.48.1"

    // ETSI QCStatements
    static let etsiQcsQcType: String = "0.4.0.1862.1.6"
}

internal struct X509Certificate {
    let version: Int  // 0 = v1, 1 = v2, 2 = v3
    let serialNumber: Data
    let signatureAlgorithmOid: String
    let issuer: X509DistinguishedName
    let notBefore: Date
    let notAfter: Date
    let subject: X509DistinguishedName
    let extensions: [X509Extension]

    func findExtension(oid: String) -> X509Extension? {
        extensions.first { $0.oid == oid }
    }
}

internal struct X509DistinguishedName: Equatable {
    /// Ordered list of (oid, value) pairs as they appear in the RDN sequence.
    /// Duplicates are preserved (multi-valued RDNs flatten into multiple entries).
    let attributes: [Attribute]

    struct Attribute: Equatable {
        let oid: String
        let value: String
    }

    func value(forOid oid: String) -> String? {
        attributes.first { $0.oid == oid }?.value
    }
}

internal struct X509Extension {
    let oid: String
    let critical: Bool
    /// Contents of the extnValue OCTET STRING (i.e., the inner DER of the extension value).
    let valueBytes: Data
}

// MARK: - Extension-typed payloads

internal struct KeyUsageBits {
    let digitalSignature: Bool
    let nonRepudiation: Bool
    let keyEncipherment: Bool
    let dataEncipherment: Bool
    let keyAgreement: Bool
    let keyCertSign: Bool
    let crlSign: Bool
    let encipherOnly: Bool
    let decipherOnly: Bool
}

internal struct BasicConstraintsInfo {
    let isCa: Bool
    let pathLenConstraint: Int?
}

internal enum SubjectAltName: Equatable {
    case dnsName(String)
    case rfc822Name(String)
    case uri(String)
    case ipAddress(Data)
    case registeredId(String)
    case otherName(typeId: String)
}

internal struct AuthorityKeyIdentifierInfo {
    let keyIdentifier: Data?
    let authorityCertSerialNumber: Data?
}

internal struct AuthorityInformationAccessInfo {
    let caIssuersUri: String?
    let ocspUri: String?
}

internal enum QcStatement: Equatable {
    /// id-etsi-qcs-QcType wrapping a specific QC type OID (e.g. id-etsi-qct-pid).
    case qcType(typeIdentifier: String)
    /// Any other QC statement; the statementId is the semantic OID.
    case other(statementId: String)

    var semanticOid: String {
        switch self {
        case .qcType(let typeId): return typeId
        case .other(let stmtId): return stmtId
        }
    }
}
