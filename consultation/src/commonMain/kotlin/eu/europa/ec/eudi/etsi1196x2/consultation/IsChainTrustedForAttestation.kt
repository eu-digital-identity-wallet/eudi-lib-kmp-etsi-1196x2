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

public sealed interface Attestation {
    public interface FormatType : Attestation
    public data class MDoc(val docType: String) : FormatType
    public data class SDJwtVc(val vct: List<String>) : FormatType {
        public constructor(vct: String) : this(listOf(vct))
        init {
            require(vct.isNotEmpty()) { "Attestation must have at least one VCT" }
        }
    }
    public data class MultiFormat(val formatTypes: Set<FormatType>) : Attestation {
        public constructor(vararg formatTypes: FormatType) : this(formatTypes.toSet())
        init {
            require(formatTypes.size >= 2)
            val duplicates = formatTypes.groupBy { it::class }.filterValues { it.size > 1 }
            require(duplicates.isEmpty()) {
                "Duplicate format types not allowed"
            }
        }
    }

    public fun toSet(): Set<FormatType> = when (this) {
        is FormatType -> setOf(this)
        is MultiFormat -> formatTypes
    }

    public operator fun contains(format: FormatType): Boolean = when (this) {
        is MultiFormat -> format in formatTypes
        else -> this == format
    }

    public companion object {
        public val PIDMdoc: MDoc = MDoc("eu.europa.ec.eudi.pid.1")
        public val PIDSdJwtVC: SDJwtVc = SDJwtVc("urn:eudi:pid:1")
        public val PID: MultiFormat = MultiFormat(PIDMdoc, PIDSdJwtVC)
        public val MDL: MDoc = MDoc("org.iso.18013.5.1.mDL")
    }
}

public sealed class AttestationClassification(private val attestations: Set<Attestation.FormatType>) {
    init {
        require(attestations.isNotEmpty()) { "Attestation classification must have at least one attestation" }
    }

    public data class PIDs(val attestations: Set<Attestation.FormatType>) : AttestationClassification(attestations)

    public data class PubEAAs(val attestations: Set<Attestation.FormatType>) : AttestationClassification(attestations)

    public data class QEAAs(val attestations: Set<Attestation.FormatType>) : AttestationClassification(attestations)

    public data class EAAs(val useCase: String, val attestations: Set<Attestation.FormatType>) :
        AttestationClassification(attestations)

    public operator fun contains(formatType: Attestation.FormatType): Boolean {
        return attestations.any { formatType in it }
    }

    public fun issuanceAndRevocationContexts(): Pair<VerificationContext, VerificationContext>? =
        when (this) {
            is PIDs -> VerificationContext.PID to VerificationContext.PIDStatus
            is PubEAAs -> VerificationContext.PubEAA to VerificationContext.PubEAAStatus
            is QEAAs -> VerificationContext.QEAA to VerificationContext.QEAAStatus
            is EAAs -> VerificationContext.EAA(useCase) to VerificationContext.EAA(useCase)
        }

    public companion object {
        public val PIDS: PIDs = PIDs(Attestation.PID.toSet())
        public val MDLs: EAAs = EAAs("MDL", Attestation.MDL.toSet())
    }
}

public class IsChainTrustedForAttestation<in CHAIN : Any, TRUST_ANCHOR : Any>(
    private val isChainTrustedForContext: suspend (CHAIN, VerificationContext) -> CertificationChainValidation<TRUST_ANCHOR>?,
    private val attestationClassifications: List<AttestationClassification>,
) {

    public suspend fun issuance(
        chain: CHAIN,
        formatType: Attestation.FormatType,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        contexts(formatType)?.let { (issuance, _) ->
            isChainTrustedForContext(chain, issuance)
        }

    public suspend fun revocation(
        chain: CHAIN,
        formatType: Attestation.FormatType,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        contexts(formatType)?.let { (_, revocation) ->
            isChainTrustedForContext(chain, revocation)
        }

    private fun contexts(formatType: Attestation.FormatType): Pair<VerificationContext, VerificationContext>? =
        attestationClassifications.firstOrNull { formatType in it }?.issuanceAndRevocationContexts()

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
