/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.DIGIT
import eu.europa.ec.eudi.etsi119602.JwtUtil
import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IsChainTrustedTest {

    fun isChainTrusted(): IsChainTrusted {
        val getTrustAnchorsByVerification =
            GetTrustAnchorsByVerification.usingLoTE(DownloadFromDIGIT)
        return IsChainTrustedJvm(getTrustAnchorsByVerification = getTrustAnchorsByVerification)
    }

    @Test
    fun test() = runTest {
        val isChainTrusted = isChainTrusted()
    }
}

private object DownloadFromDIGIT : GetListByProfile {
    override suspend fun invoke(loteType: URI): ListOfTrustedEntities {
        val (_, jwt) = DIGIT.fetchLists { it.listAndSchemeInformation.type == loteType }.first()
        return JwtUtil.loteOfJwt(jwt)
    }
}
