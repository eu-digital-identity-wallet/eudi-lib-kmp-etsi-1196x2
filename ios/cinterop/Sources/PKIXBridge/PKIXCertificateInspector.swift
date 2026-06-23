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
import Security

@objc public final class PKIXCertificateInspector: NSObject {

    @objc public override init() {
        super.init()
    }

    // MARK: - Top-level fields

    @objc public func getVersion(_ der: NSData) -> Int {
        guard let cert = try? X509Parser.parse(der as Data) else { return 0 }
        return cert.version
    }

    @objc public func getSerialNumber(_ der: NSData) -> NSData? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        return cert.serialNumber as NSData
    }

    /// Returns notBefore as seconds since the Unix epoch (boxed), or nil if the cert can't be parsed.
    /// Epoch seconds rather than NSDate: Kotlin/Native's Foundation binding for NSDate does not
    /// expose `timeIntervalSince1970`, so a boxed Double crosses the cinterop boundary cleanly.
    @objc public func getValidityNotBeforeEpochSeconds(_ der: NSData) -> NSNumber? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        return NSNumber(value: cert.notBefore.timeIntervalSince1970)
    }

    @objc public func getValidityNotAfterEpochSeconds(_ der: NSData) -> NSNumber? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        return NSNumber(value: cert.notAfter.timeIntervalSince1970)
    }

    @objc public func getSubject(_ der: NSData) -> [String: String]? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        return dnDictionary(cert.subject)
    }

    @objc public func getIssuer(_ der: NSData) -> [String: String]? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        return dnDictionary(cert.issuer)
    }

    @objc public func isSelfSigned(_ der: NSData) -> Bool {
        guard let cert = try? X509Parser.parse(der as Data) else { return false }
        return cert.subject == cert.issuer
    }

    // MARK: - Extensions: discovery

    @objc public func hasExtension(_ der: NSData, oid: String) -> Bool {
        guard let cert = try? X509Parser.parse(der as Data) else { return false }
        return cert.findExtension(oid: oid) != nil
    }

    @objc public func getExtensionCriticality(_ der: NSData) -> [String: NSNumber]? {
        guard let cert = try? X509Parser.parse(der as Data) else { return nil }
        var result: [String: NSNumber] = [:]
        for ext in cert.extensions {
            result[ext.oid] = NSNumber(value: ext.critical)
        }
        return result
    }

    // MARK: - Extensions: typed decoders

    @objc public func getKeyUsage(_ der: NSData) -> [String: NSNumber]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.keyUsage),
              let ku = try? X509ExtensionDecoder.decodeKeyUsage(ext.valueBytes) else {
            return nil
        }
        return [
            "digitalSignature": NSNumber(value: ku.digitalSignature),
            "nonRepudiation": NSNumber(value: ku.nonRepudiation),
            "keyEncipherment": NSNumber(value: ku.keyEncipherment),
            "dataEncipherment": NSNumber(value: ku.dataEncipherment),
            "keyAgreement": NSNumber(value: ku.keyAgreement),
            "keyCertSign": NSNumber(value: ku.keyCertSign),
            "crlSign": NSNumber(value: ku.crlSign),
            "encipherOnly": NSNumber(value: ku.encipherOnly),
            "decipherOnly": NSNumber(value: ku.decipherOnly),
        ]
    }

    @objc public func getBasicConstraints(_ der: NSData) -> [String: Any]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.basicConstraints),
              let bc = try? X509ExtensionDecoder.decodeBasicConstraints(ext.valueBytes) else {
            return nil
        }
        var dict: [String: Any] = ["isCA": NSNumber(value: bc.isCa)]
        if let pathLen = bc.pathLenConstraint {
            dict["pathLengthConstraint"] = NSNumber(value: pathLen)
        }
        return dict
    }

    @objc public func getSubjectAltNames(_ der: NSData) -> [[String: Any]]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.subjectAltName),
              let names = try? X509ExtensionDecoder.decodeSubjectAltName(ext.valueBytes) else {
            return nil
        }
        return names.map { san -> [String: Any] in
            switch san {
            case .dnsName(let v): return ["type": "dnsName", "value": v]
            case .rfc822Name(let v): return ["type": "email", "value": v]
            case .uri(let v): return ["type": "uri", "value": v]
            case .ipAddress(let bytes): return ["type": "ipAddress", "value": bytes as NSData]
            case .registeredId(let v): return ["type": "registeredId", "value": v]
            case .otherName(let v): return ["type": "otherName", "value": v]
            }
        }
    }

    @objc public func getSubjectKeyIdentifier(_ der: NSData) -> NSData? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.subjectKeyIdentifier),
              let ski = try? X509ExtensionDecoder.decodeSubjectKeyIdentifier(ext.valueBytes) else {
            return nil
        }
        return ski as NSData
    }

    @objc public func getAuthorityKeyIdentifier(_ der: NSData) -> [String: Any]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.authorityKeyIdentifier),
              let aki = try? X509ExtensionDecoder.decodeAuthorityKeyIdentifier(ext.valueBytes) else {
            return nil
        }
        var dict: [String: Any] = [:]
        if let keyId = aki.keyIdentifier {
            dict["keyIdentifier"] = keyId as NSData
        }
        if let serial = aki.authorityCertSerialNumber {
            dict["authorityCertSerialNumber"] = serial as NSData
        }
        return dict.isEmpty ? nil : dict
    }

    @objc public func getCertificatePolicies(_ der: NSData) -> [String]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.certificatePolicies),
              let policies = try? X509ExtensionDecoder.decodeCertificatePolicies(ext.valueBytes) else {
            return nil
        }
        return policies
    }

    @objc public func getCrlDistributionPoints(_ der: NSData) -> [String]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.crlDistributionPoints),
              let uris = try? X509ExtensionDecoder.decodeCrlDistributionPoints(ext.valueBytes) else {
            return nil
        }
        return uris
    }

    @objc public func getAuthorityInfoAccess(_ der: NSData) -> [String: String]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.authorityInformationAccess),
              let aia = try? X509ExtensionDecoder.decodeAuthorityInformationAccess(ext.valueBytes) else {
            return nil
        }
        var dict: [String: String] = [:]
        if let v = aia.caIssuersUri { dict["caIssuersUri"] = v }
        if let v = aia.ocspUri { dict["ocspUri"] = v }
        return dict.isEmpty ? nil : dict
    }

    @objc public func getQcStatements(_ der: NSData) -> [[String: String]]? {
        guard let cert = try? X509Parser.parse(der as Data),
              let ext = cert.findExtension(oid: X509Oids.qcStatements),
              let stmts = try? X509ExtensionDecoder.decodeQcStatements(ext.valueBytes) else {
            return nil
        }
        return stmts.map { stmt -> [String: String] in
            switch stmt {
            case .qcType(let typeId):
                return ["kind": "qcType", "typeIdentifier": typeId]
            case .other(let stmtId):
                return ["kind": "other", "statementId": stmtId]
            }
        }
    }

    // MARK: - Public key info (via Security.framework)

    @objc public func getPublicKeyInfo(_ der: NSData) -> [String: Any]? {
        guard let secCert = SecCertificateCreateWithData(nil, der as CFData),
              let key = SecCertificateCopyKey(secCert),
              let attrs = SecKeyCopyAttributes(key) as? [String: Any] else {
            return nil
        }
        let rawType = attrs[kSecAttrKeyType as String] as? String ?? ""
        let algorithm = Self.algorithmName(forKeyType: rawType)
        var dict: [String: Any] = ["algorithm": algorithm]
        if let size = attrs[kSecAttrKeySizeInBits as String] as? Int {
            dict["keySize"] = NSNumber(value: size)
        }
        return dict
    }

    private static func algorithmName(forKeyType keyType: String) -> String {
        if keyType == (kSecAttrKeyTypeRSA as String) { return "RSA" }
        if keyType == (kSecAttrKeyTypeECSECPrimeRandom as String) { return "EC" }
        return keyType
    }

    // MARK: - Helpers

    private func dnDictionary(_ dn: X509DistinguishedName) -> [String: String] {
        // Note: collapses multi-valued / repeated attributes — last wins.
        // The Kotlin-side DistinguishedName uses Map<String, String> with the same semantics.
        var dict: [String: String] = [:]
        for attr in dn.attributes {
            dict[attr.oid] = attr.value
        }
        return dict
    }
}
