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

public data class Attestation(val docType: String? = null, val vct: List<String>? = null) {
    init {
        require(docType != null || vct != null) { "Attestation must have at least a docType or vct" }
    }

    public companion object {
        public val PID: Attestation
            get() =
                Attestation("eu.europa.ec.eudi.pid.1", listOf("urn:eudi:pid:1"))

        public val MDL: Attestation
            get() =
                Attestation("org.iso.18013.5.1.mDL")
    }
}

public sealed class AttestationClassification(private val attestations: List<Attestation>) {
    init {
        require(attestations.isNotEmpty()) { "Attestation classification must have at least one attestation" }
    }

    public data class PIDs(val attestations: List<Attestation>) : AttestationClassification(attestations)

    public data class PubEAAs(val attestations: List<Attestation>) : AttestationClassification(attestations)

    public data class QEAAs(val attestations: List<Attestation>) : AttestationClassification(attestations)

    public data class EAAs(val useCase: String, val attestations: List<Attestation>) :
        AttestationClassification(attestations)

    public operator fun contains(attestation: Attestation): Boolean {
        return (attestation in attestations) || attestations.any { it.docType == attestation.docType || it.vct == attestation.vct }
    }

    public fun issuanceAndRevocationContexts(): Pair<VerificationContext, VerificationContext>? =
        when (this) {
            is PIDs -> VerificationContext.PID to VerificationContext.PIDStatus
            is PubEAAs -> VerificationContext.PubEAA to VerificationContext.PubEAAStatus
            is QEAAs -> VerificationContext.QEAA to VerificationContext.QEAAStatus
            is EAAs -> VerificationContext.EAA(useCase) to VerificationContext.EAA(useCase)
        }

    public companion object {
        public val PIDS: PIDs = PIDs(listOf(Attestation.PID))
        public val MDLs: EAAs = EAAs("MDL", listOf(Attestation.MDL))
    }
}

public class IsChainTrustedForAttestation<in CHAIN : Any, TRUST_ANCHOR : Any>(
    private val isChainTrustedForContext: suspend (CHAIN, VerificationContext) -> CertificationChainValidation<TRUST_ANCHOR>?,
    private val attestationClassifications: List<AttestationClassification>,
) {

    public suspend fun mdocIssuance(chain: CHAIN, docType: String): CertificationChainValidation<TRUST_ANCHOR>? =
        mDocContexts(docType)?.let { (issuance, _) ->
            isChainTrustedForContext(chain, issuance)
        }

    public suspend fun mdocRevocation(chain: CHAIN, docType: String): CertificationChainValidation<TRUST_ANCHOR>? =
        mDocContexts(docType)?.let { (_, revocation) ->
            isChainTrustedForContext(chain, revocation)
        }

    public suspend fun sdJwtVcIssuance(chain: CHAIN, vct: List<String>): CertificationChainValidation<TRUST_ANCHOR>? =
        sdJwtVcContexts(vct)?.let { (issuance, _) ->
            isChainTrustedForContext(chain, issuance)
        }

    public suspend fun sdJwtVcRevocation(chain: CHAIN, vct: List<String>): CertificationChainValidation<TRUST_ANCHOR>? =
        sdJwtVcContexts(vct)?.let { (_, revocation) ->
            isChainTrustedForContext(chain, revocation)
        }

    private fun mDocContexts(docType: String): Pair<VerificationContext, VerificationContext>? =
        attestationClassifications.firstOrNull { Attestation(docType = docType) in it }?.issuanceAndRevocationContexts()

    private fun sdJwtVcContexts(vct: List<String>): Pair<VerificationContext, VerificationContext>? =
        attestationClassifications.firstOrNull { Attestation(vct = vct) in it }?.issuanceAndRevocationContexts()

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
