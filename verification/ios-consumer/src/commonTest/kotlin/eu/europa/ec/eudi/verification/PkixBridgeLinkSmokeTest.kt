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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package eu.europa.ec.eudi.verification

import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXCertificateInspector
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXConfiguration
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXValidator
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Link/construct smoke test for the documented iOS consumer setup.
 *
 * This test deliberately *references* the PKIXBridge-backed classes so that the
 * Kotlin/Native linker must resolve the `PKIXBridge` symbols — the exact failure
 * described in https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/issues/176.
 *
 * If the documented `linkerOpts` recipe stops working (slice layout, symbol set, Swift
 * ABI shims, …), the `linkDebugTestIos*` tasks fail before this test is even reached.
 */
class PkixBridgeLinkSmokeTest {

    @Test
    fun pkixBridgeSymbols_resolveAndConstruct() {
        val configuration = PKIXConfiguration()
        assertNotNull(configuration)

        val validator = PKIXValidator(configuration)
        assertNotNull(validator)

        assertNotNull(PKIXValidator())
        assertNotNull(PKIXCertificateInspector())
    }
}