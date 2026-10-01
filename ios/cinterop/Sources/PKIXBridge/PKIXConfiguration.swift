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

/// Revocation mechanism(s) `SecTrust` may use when `PKIXConfiguration.isRevocationEnabled` is `true`.
///
/// Each case maps onto the `kSecRevocation*` flags accepted by `SecPolicyCreateRevocation`.
/// `kSecRevocationRequirePositiveResponse` is always set
@objc public enum PKIXRevocationMethod: Int {

    /// Online Certificate Status Protocol only (`kSecRevocationOCSPMethod`).
    /// This is the default.
    @objc(PKIXRevocationMethodOCSP)
    case ocsp = 0

    /// Certificate Revocation Lists only (`kSecRevocationCRLMethod`), fetched from the
    /// certificate's CRL Distribution Points extension.
    @objc(PKIXRevocationMethodCRL)
    case crl = 1

    /// Either OCSP or CRL, whichever the certificate advertises; OCSP is tried first when both
    /// are available (`kSecRevocationOCSPMethod | kSecRevocationCRLMethod`).
    @objc(PKIXRevocationMethodAnyPreferOCSP)
    case anyPreferOCSP = 2

    /// Either OCSP or CRL, but CRL is tried first when both are available
    /// (`kSecRevocationOCSPMethod | kSecRevocationCRLMethod | kSecRevocationPreferCRL`).
    @objc(PKIXRevocationMethodAnyPreferCRL)
    case anyPreferCRL = 3
}

extension PKIXRevocationMethod {

    /// The `SecPolicyCreateRevocation` flags for this method, always including
    /// `kSecRevocationRequirePositiveResponse`.
    var secRevocationFlags: CFOptionFlags {
        let methodFlags: CFOptionFlags
        switch self {
        case .ocsp:
            methodFlags = CFOptionFlags(kSecRevocationOCSPMethod)
        case .crl:
            methodFlags = CFOptionFlags(kSecRevocationCRLMethod)
        case .anyPreferOCSP:
            methodFlags = CFOptionFlags(kSecRevocationOCSPMethod | kSecRevocationCRLMethod)
        case .anyPreferCRL:
            methodFlags = CFOptionFlags(kSecRevocationOCSPMethod | kSecRevocationCRLMethod | kSecRevocationPreferCRL)
        }
        return methodFlags | CFOptionFlags(kSecRevocationRequirePositiveResponse)
    }
}

@objc public final class PKIXConfiguration: NSObject {

    /// Whether revocation status is checked during trust evaluation.
    @objc public let isRevocationEnabled: Bool

    /// Which revocation mechanism(s) to use. Ignored when `isRevocationEnabled` is `false`.
    @objc public let revocationMethod: PKIXRevocationMethod

    @objc public init(isRevocationEnabled: Bool, revocationMethod: PKIXRevocationMethod) {
        self.isRevocationEnabled = isRevocationEnabled
        self.revocationMethod = revocationMethod
        super.init()
    }

    @objc public convenience init(isRevocationEnabled: Bool) {
        self.init(isRevocationEnabled: isRevocationEnabled, revocationMethod: .ocsp)
    }

    @objc public override convenience init() {
        self.init(isRevocationEnabled: true, revocationMethod: .ocsp)
    }
}
