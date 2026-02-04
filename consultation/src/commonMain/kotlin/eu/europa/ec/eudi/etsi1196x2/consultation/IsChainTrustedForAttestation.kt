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

import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationClassification.*
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationIdentifierPredicate.Companion.isMdoc
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationIdentifierPredicate.Companion.isSdJwtVc

public sealed interface AttestationIdentifier {
    public data class MDoc(val docType: String) : AttestationIdentifier
    public data class SDJwtVc(val vct: String) : AttestationIdentifier
}

public fun interface AttestationIdentifierPredicate {

    public operator fun invoke(identifier: AttestationIdentifier): Boolean

    public infix fun or(other: AttestationIdentifierPredicate): AttestationIdentifierPredicate =
        AttestationIdentifierPredicate { this(it) || other(it) }

    public companion object {
        public fun isSdJwtVc(vct: String): AttestationIdentifierPredicate =
            AttestationIdentifierPredicate { it is AttestationIdentifier.SDJwtVc && it.vct == vct }

        public fun isMdoc(docType: String): AttestationIdentifierPredicate =
            AttestationIdentifierPredicate { it is AttestationIdentifier.MDoc && it.docType == docType }
    }
}

public sealed interface AttestationClassification {

    public data class PIDs(val predicate: AttestationIdentifierPredicate) : AttestationClassification
    public data class PubEAAs(val predicate: AttestationIdentifierPredicate) : AttestationClassification
    public data class QEAAs(val predicate: AttestationIdentifierPredicate) : AttestationClassification
    public data class EAAs(val predicatePerUseCase: Map<String, AttestationIdentifierPredicate>) :
        AttestationClassification

    public operator fun contains(identifier: AttestationIdentifier): Boolean {
        return when (this) {
            is PIDs -> predicate(identifier)
            is PubEAAs -> predicate(identifier)
            is QEAAs -> predicate(identifier)
            is EAAs -> predicatePerUseCase.values.any { it(identifier) }
        }
    }

    public companion object {
        public const val PID_DOCTYPE: String = "eu.europa.ec.eudi.pid.1"
        public const val PID_VCT: String = "urn:eudi:pid:1"
        public const val MDL_DOCTYPE: String = "org.iso.18013.5.1.mDL"
        public val PIDS: PIDs = PIDs(isSdJwtVc(PID_VCT) or isMdoc(PID_DOCTYPE))
        public val MDLs: EAAs = EAAs(
            mapOf("MDL" to isMdoc(MDL_DOCTYPE)),
        )
    }
}

public class IsChainTrustedForAttestation<in CHAIN : Any, TRUST_ANCHOR : Any>(
    private val isChainTrustedForContext: suspend (CHAIN, VerificationContext) -> CertificationChainValidation<TRUST_ANCHOR>?,
    private val attestationClassifications: List<AttestationClassification>,
) {

    public suspend fun issuance(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        issuanceAndRevocationContextOf(identifier)?.let { (issuance, _) ->
            isChainTrustedForContext(chain, issuance)
        }

    public suspend fun revocation(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        issuanceAndRevocationContextOf(identifier)?.let { (_, revocation) ->
            isChainTrustedForContext(chain, revocation)
        }

    private fun AttestationClassification.issuanceAndRevocationContextOf(
        identifier: AttestationIdentifier,
    ): Pair<VerificationContext, VerificationContext>? {
        fun <T> takeIf(condition: Boolean, value: () -> T): T? = if (condition) value() else null
        return when (this) {
            is PIDs ->
                takeIf(identifier in this) {
                    VerificationContext.PID to VerificationContext.PIDStatus
                }

            is PubEAAs ->
                takeIf(identifier in this) {
                    VerificationContext.PubEAA to VerificationContext.PubEAAStatus
                }

            is QEAAs ->
                takeIf(identifier in this) {
                    VerificationContext.QEAA to VerificationContext.QEAAStatus
                }

            is EAAs ->
                predicatePerUseCase.firstNotNullOfOrNull { (useCase, predicate) ->
                    takeIf(predicate(identifier)) {
                        VerificationContext.EAA(useCase) to VerificationContext.EAAStatus(useCase)
                    }
                }
        }
    }

    private fun issuanceAndRevocationContextOf(
        identifier: AttestationIdentifier,
    ): Pair<VerificationContext, VerificationContext>? =
        attestationClassifications.firstNotNullOfOrNull { classification ->
            classification.issuanceAndRevocationContextOf(identifier)
        }

    public companion object {
        public operator fun <CHAIN : Any, TRUST_ANCHOR : Any> invoke(
            isChainTrustedForContext: IsChainTrustedForContext<CHAIN, TRUST_ANCHOR>,
            attestationClassificationsBuilder: MutableList<AttestationClassification>.() -> Unit = {},
        ): IsChainTrustedForAttestation<CHAIN, TRUST_ANCHOR> {
            val attestationClassifications =
                buildList(attestationClassificationsBuilder)
            return IsChainTrustedForAttestation(isChainTrustedForContext::invoke, attestationClassifications)
        }
    }
}
