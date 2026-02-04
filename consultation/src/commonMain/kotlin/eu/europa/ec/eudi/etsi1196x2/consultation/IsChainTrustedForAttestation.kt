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

/**
 * An interface for attestation identifiers.
 * @see MDoc
 * @see SDJwtVc
 */
public interface AttestationIdentifier

/**
 * ISO/IEC 18013-5 encoded attestation
 * @param docType the document type of the attestation
 */
public data class MDoc(val docType: String) : AttestationIdentifier

/**
 * SD JWT VC encoded attestation
 * @param vct the vct claim of the attestation
 */
public data class SDJwtVc(val vct: String) : AttestationIdentifier

/**
 * A predicate for attestation identifiers.
 */
public fun interface AttestationIdentifierPredicate {

    /**
     * Tests if the given attestation identifier matches the predicate.
     * @param identifier the attestation identifier to test.
     * @return true if the identifier matches the predicate, false otherwise.
     */
    public fun test(identifier: AttestationIdentifier): Boolean

    /**
     * Combines the current predicate with another one, by checking if either one matches.
     * @param other the other predicate to combine with the current one.
     * @return a new predicate that combines the current predicate with the other one.
     */
    public infix fun or(other: AttestationIdentifierPredicate): AttestationIdentifierPredicate =
        AttestationIdentifierPredicate { this.test(it) || other.test(it) }

    public companion object {
        /**
         * A predicate that always returns false.
         */
        public val None: AttestationIdentifierPredicate = AttestationIdentifierPredicate { false }
    }
}

/**
 * An [AttestationIdentifierPredicate]
 * that matches attestations with the same identifier as the current one.
 */
public val AttestationIdentifier.match: AttestationIdentifierPredicate
    get() = AttestationIdentifierPredicate { this == it }

/**
 * A way of classifying attestations
 *
 * ```kotlin
 * val isPidInMdoc = MDoc(docType = "eu.europa.ec.eudi.pid.1").match
 * val isPidInJwtVc = SDJwtVc(vct = "urn:eudi:pid:1").match
 * val isMdl = MDoc(docType = "org.iso.18013.5.1.mDL").match
 *
 * val classifications = AttestationClassifications(
 *    pids = isPidInMdoc or isPidInJwtVc,
 *    eaAs = mapOf("mdl" to isMdl)
 * )
 * ```
 *
 * @param pids a predicate for PIDs
 * @param pubEAAs a predicate for public EAA identifiers
 * @param qEAAs a predicate for qualified EAA identifiers
 * @param eaAs a map of use cases to predicates for EAA identifiers
 */
public data class AttestationClassifications(
    val pids: AttestationIdentifierPredicate = AttestationIdentifierPredicate.None,
    val pubEAAs: AttestationIdentifierPredicate = AttestationIdentifierPredicate.None,
    val qEAAs: AttestationIdentifierPredicate = AttestationIdentifierPredicate.None,
    val eaAs: Map<String, AttestationIdentifierPredicate> = emptyMap(),
) {
    public fun <T : Any> classifyAndMap(
        ifPid: () -> T,
        ifPubEaa: () -> T,
        ifQEaa: () -> T,
        ifEaa: (String) -> T,
    ): (AttestationIdentifier) -> T? = { identifier ->
        when {
            pids.test(identifier) -> ifPid()
            pubEAAs.test(identifier) -> ifPubEaa()
            qEAAs.test(identifier) -> ifQEaa()
            else -> {
                eaAs.firstNotNullOfOrNull { (useCase, predicate) ->
                    if (predicate.test(identifier)) ifEaa(useCase) else null
                }
            }
        }
    }
}

/**
 * A specialization of [IsChainTrustedForContext] for attestations
 * @param isChainTrustedForContext the function used to validate a certificate chain in a [VerificationContext].
 * @param classifications the way of classifying attestations
 * @param CHAIN the type of the certificate chain to be validated
 * @param TRUST_ANCHOR the type of the trust anchor to be used for validation
 */
public class IsChainTrustedForAttestation<in CHAIN : Any, TRUST_ANCHOR : Any>(
    private val isChainTrustedForContext: suspend (CHAIN, VerificationContext) -> CertificationChainValidation<TRUST_ANCHOR>?,
    classifications: AttestationClassifications,
) {

    private val issuanceAndRevocationContextOf: (AttestationIdentifier) -> Pair<VerificationContext, VerificationContext>? =
        classifications.classifyAndMap(
            ifPid = { VerificationContext.PID to VerificationContext.PIDStatus },
            ifPubEaa = { VerificationContext.PubEAA to VerificationContext.PubEAAStatus },
            ifQEaa = { VerificationContext.QEAA to VerificationContext.QEAAStatus },
            ifEaa = { useCase -> VerificationContext.EAA(useCase) to VerificationContext.EAAStatus(useCase) },
        )

    /**
     * Validates a certificate chain for issuance of an attestation.
     *
     * @param chain the certificate chain to be validated
     * @param identifier the attestation identifier
     * @return the result of the validation
     */
    public suspend fun issuance(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        issuanceAndRevocationContextOf(identifier)?.let { (issuance, _) ->
            isChainTrustedForContext(chain, issuance)
        }

    /**
     * Validates a certificate chain for revocation of an attestation.
     *
     * @param chain the certificate chain to be validated
     * @param identifier the attestation identifier
     * @return the result of the validation
     */
    public suspend fun revocation(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        issuanceAndRevocationContextOf(identifier)?.let { (_, revocation) ->
            isChainTrustedForContext(chain, revocation)
        }
}
