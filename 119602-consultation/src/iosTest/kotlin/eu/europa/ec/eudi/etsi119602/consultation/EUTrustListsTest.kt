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

import eu.europa.ec.eudi.etsi119602.consultation.eu.EUMDLProvidersListSpec
import eu.europa.ec.eudi.etsi119602.consultation.eu.ServiceDigitalIdentityCertificateType
import eu.europa.ec.eudi.etsi119602.datamodel.ETSI19602
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [EUTrustLists] must reproduce [SupportedLists.eu] for the standard EU lists, so iOS callers get the
 * same service types, end-entity profiles and certificate types as the JVM/Android default.
 */
class EUTrustListsTest {

    private val url = "https://example.test/lote"
    private val eu = SupportedLists.eu()

    @Test
    fun pid_matchesEuBaseline() = assertMatchesEu(EUTrustLists.pid(url), eu.pidProviders, VerificationContext.PID, VerificationContext.PIDStatus)

    @Test
    fun walletProviders_matchesEuBaseline() = assertMatchesEu(
        EUTrustLists.walletProviders(url),
        eu.walletProviders,
        VerificationContext.WalletProviderAttestation,
        VerificationContext.WalletOrKeyStorageStatus,
    )

    @Test
    fun wrpac_matchesEuBaseline() = assertMatchesEu(
        EUTrustLists.wrpac(url),
        eu.wrpacProviders,
        VerificationContext.WalletRelyingPartyAccessCertificate,
        revocation = null,
    )

    @Test
    fun wrprc_matchesEuBaseline() = assertMatchesEu(
        EUTrustLists.wrprc(url),
        eu.wrprcProviders,
        VerificationContext.WalletRelyingPartyRegistrationCertificate,
        VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
    )

    @Test
    fun endEntityProfiles_arePreservedForProfiledLists() {
        // Guards against the silent downgrade of building these lists with a null profile.
        assertNotNull(EUTrustLists.pid(url).certificateProfile, "PID must enforce the PID signing certificate profile")
        assertNotNull(EUTrustLists.walletProviders(url).certificateProfile, "Wallet providers must enforce their profile")
        assertNotNull(EUTrustLists.wrpac(url).certificateProfile, "WRPAC must enforce the access certificate profile")
    }

    @Test
    fun pubEaa_isBuiltFromThePubEaaProfile() {
        val list = EUTrustLists.pubEaa(url)
        assertEquals(url, list.url)
        assertEquals(ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_ISSUANCE, list.issuanceService)
        assertEquals(ETSI19602.EU_PUB_EAA_PROVIDERS_SVC_TYPE_REVOCATION, list.revocationService)
        assertNull(list.certificateProfile)
        assertEquals(ServiceDigitalIdentityCertificateType.EndEntityOrCA, list.serviceDigitalIdentityCertificateType)
    }

    @Test
    fun mdl_isBuiltFromTheMdlProfile_underTheGivenUseCase() {
        val eaa = EUTrustLists.mdl(url, useCase = "mdl")
        assertEquals("mdl", eaa.useCase)
        assertEquals(url, eaa.trustList.url)
        assertEquals(EUMDLProvidersListSpec.SVC_TYPE_ISSUANCE, eaa.trustList.issuanceService)
        assertEquals(EUMDLProvidersListSpec.SVC_TYPE_REVOCATION, eaa.trustList.revocationService)
        assertNull(eaa.trustList.certificateProfile)
        assertEquals(ServiceDigitalIdentityCertificateType.EndEntityOrCA, eaa.trustList.serviceDigitalIdentityCertificateType)
    }

    private fun assertMatchesEu(
        actual: TrustList,
        expected: LotEMeta<VerificationContext>?,
        issuance: VerificationContext,
        revocation: VerificationContext?,
    ) {
        val meta = assertNotNull(expected)
        val issuanceSvc = assertNotNull(meta.svcTypePerCtx[issuance])
        assertEquals(url, actual.url)
        assertEquals(issuanceSvc.svcTypeIdentifier.value, actual.issuanceService)
        assertEquals(revocation?.let { meta.svcTypePerCtx[it]?.svcTypeIdentifier?.value }, actual.revocationService)
        assertEquals(meta.serviceDigitalIdentityCertificateType, actual.serviceDigitalIdentityCertificateType)
        // Profiles are rebuilt on every eu() call, so compare presence rather than identity.
        assertEquals(issuanceSvc.endEntityProfile != null, actual.certificateProfile != null)
        assertEquals(meta.svcTypePerCtx.keys, setOfNotNull(issuance, revocation))
    }
}
