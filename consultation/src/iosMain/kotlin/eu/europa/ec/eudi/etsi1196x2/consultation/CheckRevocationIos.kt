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
package eu.europa.ec.eudi.etsi1196x2.consultation

import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationCheckerProtocol
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationResult
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationStatusGood
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationStatusRevoked
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationStatusUnknown
import platform.Foundation.NSData
import platform.darwin.NSObject

/**
 * Revocation status of one certificate, as reported by [CheckRevocationIos].
 */
public sealed interface IosRevocationStatus {
    /**
     * The certificate is not revoked
     */
    public data object Good : IosRevocationStatus

    /**
     * The certificate is revoked
     *
     * @param reason why the certificate is revoked
     */
    public data class Revoked(val reason: String) : IosRevocationStatus

    /**
     * The status could not be established, e.g. no revocation source, a failed download or stale data
     *
     * @param reason why the status is unknown
     */
    public data class Unknown(val reason: String) : IosRevocationStatus
}

/**
 * Checks the revocation status of one DER-encoded certificate issued by `issuer`.
 *
 * Called on a background thread and may block. Exceptions are reported as [IosRevocationStatus.Unknown].
 */
public fun interface CheckRevocationIos {
    public operator fun invoke(certificate: NSData, issuer: NSData): IosRevocationStatus
}

/**
 * Adapts a [CheckRevocationIos] to the native `PKIXRevocationChecker` protocol.
 */
internal class NativeRevocationChecker(
    private val checkRevocation: CheckRevocationIos,
) : NSObject(),
    PKIXRevocationCheckerProtocol {

    override fun revocationStatusOfCertificate(certificate: NSData, issuer: NSData): PKIXRevocationResult {
        val status = try {
            checkRevocation(certificate, issuer)
        } catch (e: Exception) {
            IosRevocationStatus.Unknown(e.message ?: "Revocation check failed")
        }
        return when (status) {
            IosRevocationStatus.Good -> PKIXRevocationResult(status = PKIXRevocationStatusGood, reason = null)
            is IosRevocationStatus.Revoked -> PKIXRevocationResult(status = PKIXRevocationStatusRevoked, reason = status.reason)
            is IosRevocationStatus.Unknown -> PKIXRevocationResult(status = PKIXRevocationStatusUnknown, reason = status.reason)
        }
    }
}
