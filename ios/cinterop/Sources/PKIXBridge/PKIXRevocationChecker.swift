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

/// Revocation status of one certificate.
@objc public enum PKIXRevocationStatus: Int {

    /// The certificate is not revoked.
    @objc(PKIXRevocationStatusGood)
    case good = 0

    /// The certificate is revoked.
    @objc(PKIXRevocationStatusRevoked)
    case revoked = 1

    /// The status could not be established, e.g. no revocation source, a failed download or stale data.
    @objc(PKIXRevocationStatusUnknown)
    case unknown = 2
}

/// Result of a `PKIXRevocationChecker`.
@objc public final class PKIXRevocationResult: NSObject {

    /// The revocation status of the certificate.
    @objc public let status: PKIXRevocationStatus

    /// Why the certificate is revoked or its status is unknown; `nil` when it is good.
    @objc public let reason: String?

    @objc public init(status: PKIXRevocationStatus, reason: String?) {
        self.status = status
        self.reason = reason
        super.init()
    }
}

/// Checks the revocation status of one DER-encoded certificate issued by `issuer`.
/// Called on a background thread and may block.
@objc public protocol PKIXRevocationChecker {

    @objc(revocationStatusOfCertificate:issuer:)
    func revocationStatus(of certificate: NSData, issuer: NSData) -> PKIXRevocationResult
}

internal enum PKIXRevocation {

    /// Checks every certificate of `chain` (leaf first, trust anchor last) except the trust anchor
    /// (RFC 5280 6.1) with `checkers` in order: good accepts the certificate, revoked rejects the
    /// chain and unknown moves on to the next checker. Fails closed when no checker reports good.
    static func check(chain: [SecCertificate], with checkers: [PKIXRevocationChecker]) throws {
        for (certificate, issuer) in zip(chain, chain.dropFirst()) {
            try check(
                certificate: SecCertificateCopyData(certificate) as NSData,
                issuer: SecCertificateCopyData(issuer) as NSData,
                with: checkers
            )
        }
    }

    private static func check(certificate: NSData, issuer: NSData, with checkers: [PKIXRevocationChecker]) throws {
        var reasons: [String] = []
        for checker in checkers {
            let result = checker.revocationStatus(of: certificate, issuer: issuer)
            switch result.status {
            case .good:
                return
            case .revoked:
                throw PKIXBridgeError.revocationCheckFailed(reason: result.reason ?? "Certificate is revoked").asNSError()
            case .unknown:
                reasons.append(result.reason ?? "Revocation status unknown")
            }
        }
        throw PKIXBridgeError.revocationCheckFailed(reason: reasons.joined(separator: "; ")).asNSError()
    }
}

/// Checks one certificate over OCSP with SecTrust, using its issuer as the only trust anchor.
internal final class OCSPRevocationChecker: NSObject, PKIXRevocationChecker {

    func revocationStatus(of certificate: NSData, issuer: NSData) -> PKIXRevocationResult {
        guard let cert = SecCertificateCreateWithData(nil, certificate),
              let issuerCert = SecCertificateCreateWithData(nil, issuer),
              let revocation = SecPolicyCreateRevocation(PKIXRevocationMethod.ocsp.secRevocationFlags) else {
            return PKIXRevocationResult(status: .unknown, reason: "OCSP check could not be set up")
        }
        var trust: SecTrust?
        guard SecTrustCreateWithCertificates([cert, issuerCert] as CFArray, [SecPolicyCreateBasicX509(), revocation] as CFArray, &trust) == errSecSuccess,
              let trust,
              SecTrustSetAnchorCertificates(trust, [issuerCert] as CFArray) == errSecSuccess,
              SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess else {
            return PKIXRevocationResult(status: .unknown, reason: "OCSP check could not be set up")
        }
        var error: CFError?
        if SecTrustEvaluateWithError(trust, &error) {
            return PKIXRevocationResult(status: .good, reason: nil)
        }
        let reason = error.map { CFErrorCopyDescription($0) as String } ?? "OCSP status unavailable"
        let isRevoked = error.map { CFErrorGetCode($0) == Int(errSecCertificateRevoked) } ?? false
        return PKIXRevocationResult(status: isRevoked ? .revoked : .unknown, reason: reason)
    }
}
