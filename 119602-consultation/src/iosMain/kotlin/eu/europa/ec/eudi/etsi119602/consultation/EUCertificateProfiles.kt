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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.consultation.eu.pidSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.walletProviderSigningCertificateProfile
import eu.europa.ec.eudi.etsi119602.consultation.eu.wrpAccessCertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Swift-friendly factories for the EU end-entity [CertificateProfile]s, for use as
 * [TrustList.certificateProfile] when building a custom [TrustList].
 *
 * Each profile comes in two forms:
 * - without arguments: the certificate's validity is checked at the time of validation (the usual choice);
 * - with `at:`: the validity is checked at a fixed point in time (e.g. when re-verifying an old credential).
 *   Swift's `Date` bridges to [NSDate] automatically.
 *
 * ```swift
 * let profile   = EUCertificateProfiles.shared.pidSigning()
 * let profileAt = EUCertificateProfiles.shared.pidSigning(at: someDate)
 * ```
 */
public object EUCertificateProfiles {

    /** PID Provider signing certificate profile, validity checked at validation time. */
    public fun pidSigning(): CertificateProfile = pidSigningCertificateProfile(at = null)

    /** PID Provider signing certificate profile, validity checked at [at]. */
    public fun pidSigning(at: NSDate): CertificateProfile = pidSigningCertificateProfile(at = at.toInstant())

    /** Wallet Provider signing certificate profile, validity checked at validation time. */
    public fun walletProviderSigning(): CertificateProfile = walletProviderSigningCertificateProfile(at = null)

    /** Wallet Provider signing certificate profile, validity checked at [at]. */
    public fun walletProviderSigning(at: NSDate): CertificateProfile = walletProviderSigningCertificateProfile(at = at.toInstant())

    /** Wallet Relying Party Access Certificate (WRPAC) profile, validity checked at validation time. */
    public fun wrpAccess(): CertificateProfile = wrpAccessCertificateProfile(at = null)

    /** Wallet Relying Party Access Certificate (WRPAC) profile, validity checked at [at]. */
    public fun wrpAccess(at: NSDate): CertificateProfile = wrpAccessCertificateProfile(at = at.toInstant())
}

internal fun NSDate.toInstant(): Instant {
    val interval = timeIntervalSince1970
    val seconds = floor(interval)
    val nanos = ((interval - seconds) * 1_000_000_000).roundToInt()
    return Instant.fromEpochSeconds(seconds.toLong(), nanos)
}
