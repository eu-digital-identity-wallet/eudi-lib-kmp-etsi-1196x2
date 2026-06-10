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

internal enum PKIXBridgeError: Error {
    case notImplemented
    case invalidCertificate(reason: String)
    case emptyChain
    case noTrustAnchorMatched
    case trustEvaluationFailed(underlying: Error?)
    case asn1ParseFailure(reason: String)

    static let domain: String = "eu.europa.ec.eudi.etsi1196x2.consultation.pkix"

    var code: Int {
        switch self {
        case .notImplemented: return -1
        case .invalidCertificate: return 1
        case .emptyChain: return 2
        case .noTrustAnchorMatched: return 3
        case .trustEvaluationFailed: return 4
        case .asn1ParseFailure: return 5
        }
    }

    var localizedDescription: String {
        switch self {
        case .notImplemented:
            return "Operation not implemented"
        case .invalidCertificate(let reason):
            return "Invalid certificate: \(reason)"
        case .emptyChain:
            return "Certificate chain is empty"
        case .noTrustAnchorMatched:
            return "Validated chain did not match any provided trust anchor"
        case .trustEvaluationFailed(let underlying):
            if let underlying = underlying {
                return "Trust evaluation failed: \(underlying.localizedDescription)"
            }
            return "Trust evaluation failed"
        case .asn1ParseFailure(let reason):
            return "ASN.1 parse failure: \(reason)"
        }
    }

    func asNSError() -> NSError {
        NSError(
            domain: Self.domain,
            code: code,
            userInfo: [NSLocalizedDescriptionKey: localizedDescription]
        )
    }
}
