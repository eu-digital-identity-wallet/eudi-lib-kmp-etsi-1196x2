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

import eu.europa.ec.eudi.etsi119602.ETSI19602
import eu.europa.ec.eudi.etsi119602.URI
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext

public data class SupportedLoTEs<out INFO : Any>(
    val pidProviders: INFO? = null,
    val walletProviders: INFO? = null,
    val wrpacProviders: INFO? = null,
    val wrprcProviders: INFO? = null,
    val eaaProviders: Map<String, INFO> = emptyMap(),
) : Iterable<INFO> {

    override fun iterator(): Iterator<INFO> =
        buildList {
            add(pidProviders)
            add(walletProviders)
            add(wrpacProviders)
            add(wrprcProviders)
            addAll(eaaProviders.values)
        }.filterNotNull().iterator()

    public companion object {

        public val EU: SupportedLoTEs<Map<VerificationContext, URI>> = SupportedLoTEs(
            pidProviders = mapOf(
                VerificationContext.PID to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE,
                VerificationContext.PIDStatus to ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            walletProviders = mapOf(
                VerificationContext.WalletInstanceAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
                VerificationContext.WalletUnitAttestation to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
                VerificationContext.WalletUnitAttestationStatus to ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            wrpacProviders = mapOf(
                VerificationContext.WalletRelyingPartyAccessCertificate to ETSI19602.EU_WRPAC_PROVIDERS_SVC_TYPE_ISSUANCE,
            ),
            wrprcProviders = mapOf(
                VerificationContext.WalletRelyingPartyRegistrationCertificate to ETSI19602.EU_WRPRC_PROVIDERS_SVC_TYPE_ISSUANCE,
            ),
        )

        public fun <L1 : Any, L2 : Any, L3 : Any> combine(
            s1: SupportedLoTEs<L1>,
            s2: SupportedLoTEs<L2>,
            combine: (L1, L2) -> L3,
        ): SupportedLoTEs<L3> {
            val combineNullables = combine.forNullables()
            return SupportedLoTEs(
                pidProviders = combineNullables(s1.pidProviders, s2.pidProviders),
                walletProviders = combineNullables(s1.walletProviders, s2.walletProviders),
                wrpacProviders = combineNullables(s1.wrpacProviders, s2.wrpacProviders),
                wrprcProviders = combineNullables(s1.wrprcProviders, s2.wrprcProviders),
                eaaProviders = s1.eaaProviders.mapNotNull { (useCase, l1) ->
                    val l2 = s2.eaaProviders[useCase]
                    combineNullables(l1, l2)?.let { useCase to it }
                }.toMap(),
            )
        }

        private fun <A : Any, B : Any, C : Any> ((A, B) -> C).forNullables(): (A?, B?) -> C? =
            { a, b -> a?.let { na -> b?.let { nb -> this(na, nb) } } }
    }
}
