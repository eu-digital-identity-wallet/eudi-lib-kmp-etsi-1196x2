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

final class X509ParserTests: XCTestCase {

    // OID constants used by openssl req's subject and SAN
    private let oidCountry = "2.5.4.6"
    private let oidOrganization = "2.5.4.10"
    private let oidCommonName = "2.5.4.3"

    private func loadFixture(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "der") else {
            XCTFail("Fixture \(name).der not found")
            return Data()
        }
        return try Data(contentsOf: url)
    }

    private func parseRoot() throws -> X509Certificate {
        try X509Parser.parse(try loadFixture("test-root-ca"))
    }

    // MARK: - Top-level fields

    func test_parsesVersionV3() throws {
        let cert = try parseRoot()
        XCTAssertEqual(cert.version, 2) // X.509 v3 is encoded as 2 (zero-based)
    }

    func test_parsesSubject() throws {
        let cert = try parseRoot()
        XCTAssertEqual(cert.subject.value(forOid: oidCountry), "DE")
        XCTAssertEqual(cert.subject.value(forOid: oidOrganization), "EUDI Test Org")
        XCTAssertEqual(cert.subject.value(forOid: oidCommonName), "PKIXBridge Test Root CA")
    }

    func test_parsesIssuer() throws {
        let cert = try parseRoot()
        XCTAssertEqual(cert.issuer.value(forOid: oidCountry), "DE")
        XCTAssertEqual(cert.issuer.value(forOid: oidCommonName), "PKIXBridge Test Root CA")
    }

    func test_isSelfSigned() throws {
        let cert = try parseRoot()
        XCTAssertEqual(cert.subject, cert.issuer)
    }

    func test_serialNumber20Bytes() throws {
        let cert = try parseRoot()
        // openssl req issues a 20-byte random serial by default
        XCTAssertEqual(cert.serialNumber.count, 20)
    }

    func test_signatureAlgorithmIsSha256WithRsa() throws {
        let cert = try parseRoot()
        XCTAssertEqual(cert.signatureAlgorithmOid, "1.2.840.113549.1.1.11")
    }

    func test_validityParses() throws {
        let cert = try parseRoot()
        XCTAssertLessThan(cert.notBefore, cert.notAfter)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        XCTAssertGreaterThan(cal.component(.year, from: cert.notAfter), 2050)
    }

    // MARK: - Extension discovery

    func test_findsAllExpectedExtensions() throws {
        let cert = try parseRoot()
        let expected = [
            X509Oids.keyUsage,
            X509Oids.basicConstraints,
            X509Oids.subjectAltName,
            X509Oids.subjectKeyIdentifier,
            X509Oids.authorityKeyIdentifier,
            X509Oids.certificatePolicies,
            X509Oids.crlDistributionPoints,
            X509Oids.authorityInformationAccess,
        ]
        for oid in expected {
            XCTAssertNotNil(cert.findExtension(oid: oid), "Missing extension \(oid)")
        }
    }

    // MARK: - Extension decoders

    func test_decodeKeyUsage() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.keyUsage)!
        XCTAssertTrue(ext.critical)
        let ku = try X509ExtensionDecoder.decodeKeyUsage(ext.valueBytes)
        XCTAssertTrue(ku.digitalSignature)
        XCTAssertTrue(ku.keyCertSign)
        XCTAssertTrue(ku.crlSign)
        XCTAssertFalse(ku.keyEncipherment)
        XCTAssertFalse(ku.dataEncipherment)
        XCTAssertFalse(ku.keyAgreement)
    }

    func test_decodeBasicConstraints() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.basicConstraints)!
        XCTAssertTrue(ext.critical)
        let bc = try X509ExtensionDecoder.decodeBasicConstraints(ext.valueBytes)
        XCTAssertTrue(bc.isCa)
        XCTAssertEqual(bc.pathLenConstraint, 3)
    }

    func test_decodeSubjectAltName() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.subjectAltName)!
        let names = try X509ExtensionDecoder.decodeSubjectAltName(ext.valueBytes)
        XCTAssertTrue(names.contains(.dnsName("test.eudi.example")))
        XCTAssertTrue(names.contains(.uri("https://test.eudi.example")))
        XCTAssertTrue(names.contains(.rfc822Name("test@eudi.example")))
        XCTAssertEqual(names.count, 3)
    }

    func test_decodeSubjectKeyIdentifier() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.subjectKeyIdentifier)!
        let ski = try X509ExtensionDecoder.decodeSubjectKeyIdentifier(ext.valueBytes)
        XCTAssertEqual(ski.count, 20) // SHA-1 hash length
    }

    func test_decodeAuthorityKeyIdentifier_selfSignedMatchesSki() throws {
        let cert = try parseRoot()
        let akiExt = cert.findExtension(oid: X509Oids.authorityKeyIdentifier)!
        let aki = try X509ExtensionDecoder.decodeAuthorityKeyIdentifier(akiExt.valueBytes)
        XCTAssertNotNil(aki.keyIdentifier)
        let skiExt = cert.findExtension(oid: X509Oids.subjectKeyIdentifier)!
        let ski = try X509ExtensionDecoder.decodeSubjectKeyIdentifier(skiExt.valueBytes)
        XCTAssertEqual(aki.keyIdentifier, ski) // self-signed identity
    }

    func test_decodeCertificatePolicies() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.certificatePolicies)!
        let policies = try X509ExtensionDecoder.decodeCertificatePolicies(ext.valueBytes)
        XCTAssertEqual(policies, ["1.2.3.4.5.6"])
    }

    func test_decodeCrlDistributionPoints() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.crlDistributionPoints)!
        let uris = try X509ExtensionDecoder.decodeCrlDistributionPoints(ext.valueBytes)
        XCTAssertEqual(uris, ["http://crl.eudi.example/test.crl"])
    }

    func test_decodeAuthorityInformationAccess() throws {
        let cert = try parseRoot()
        let ext = cert.findExtension(oid: X509Oids.authorityInformationAccess)!
        let aia = try X509ExtensionDecoder.decodeAuthorityInformationAccess(ext.valueBytes)
        XCTAssertEqual(aia.ocspUri, "http://ocsp.eudi.example")
        XCTAssertEqual(aia.caIssuersUri, "http://ca.eudi.example/ca.cer")
    }
}
