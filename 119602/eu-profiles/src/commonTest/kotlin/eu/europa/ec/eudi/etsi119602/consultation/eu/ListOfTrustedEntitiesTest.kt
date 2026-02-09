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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.PKIObject
import eu.europa.ec.eudi.etsi119602.consultation.usingLoTE
import eu.europa.ec.eudi.etsi1196x2.consultation.GetTrustAnchorsForSupportedQueries
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail

class ListOfTrustedEntitiesTest {

    @Test
    fun testDIGIT() = runTest {
        val lotePerProfile =
            DIGIT.fetchLists().map { (profile, jwt) ->
                profile to JwtUtil.loteOfJwt(jwt)
            }.toList().toMap()

        val trust: GetTrustAnchorsForSupportedQueries<VerificationContext, PKIObject> =
            GetTrustAnchorsForSupportedQueries.usingLoTE(lotePerProfile)

        listOf(
            VerificationContext.PID,
            VerificationContext.PIDStatus,
            VerificationContext.WalletInstanceAttestation,
            // VerificationContext.WalletUnitAttestation,
            VerificationContext.WalletUnitAttestationStatus,
            VerificationContext.WalletRelyingPartyAccessCertificate,
            VerificationContext.EAA("mdl"),
            VerificationContext.EAAStatus("mdl"),

        ).forEach { ctx ->

            val outcome = trust(ctx)
            when (outcome) {
                is GetTrustAnchorsForSupportedQueries.Outcome.Found<*> -> println("$ctx : ${outcome.trustAnchors.list.size} ")
                GetTrustAnchorsForSupportedQueries.Outcome.MisconfiguredSource -> println("$ctx : None ")
                GetTrustAnchorsForSupportedQueries.Outcome.QueryNotSupported -> fail("Query not supported: $ctx")
            }
        }
    }
}
