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
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EUCertificateProfilesTest {

    private val date = NSDate.dateWithTimeIntervalSince1970(1_700_000_000.25)

    @Test
    fun nsDate_convertsToInstant_withSubSecondPrecision() {
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_250), date.toInstant())
    }

    @Test
    fun nsDate_beforeEpoch_convertsToInstant() {
        val before = NSDate.dateWithTimeIntervalSince1970(-1.5)
        assertEquals(Instant.fromEpochMilliseconds(-1_500), before.toInstant())
    }

    @Test
    fun factories_buildTheSameProfilesAsTheKotlinFunctions() {
        // Profiles hold constraint lambdas, so compare their shape (number of constraints).
        assertEquals(pidSigningCertificateProfile().requirements.size, EUCertificateProfiles.pidSigning().requirements.size)
        assertEquals(pidSigningCertificateProfile().requirements.size, EUCertificateProfiles.pidSigning(at = date).requirements.size)
        assertEquals(
            walletProviderSigningCertificateProfile().requirements.size,
            EUCertificateProfiles.walletProviderSigning().requirements.size,
        )
        assertEquals(
            walletProviderSigningCertificateProfile().requirements.size,
            EUCertificateProfiles.walletProviderSigning(at = date).requirements.size,
        )
        assertEquals(wrpAccessCertificateProfile().requirements.size, EUCertificateProfiles.wrpAccess().requirements.size)
        assertEquals(wrpAccessCertificateProfile().requirements.size, EUCertificateProfiles.wrpAccess(at = date).requirements.size)
    }
}
