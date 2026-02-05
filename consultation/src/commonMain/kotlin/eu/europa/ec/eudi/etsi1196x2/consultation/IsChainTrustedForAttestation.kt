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

        /**
         * Creates a predicate that for attestations of type [AI] where the given regex
         * matches the content of the identifier.
         *
         * @param regex the regex to match the content against.
         * @param contentToMatch a function that extracts the content from the identifier.
         * @return a predicate that matches attestations of type [AI]
         * where the given regex matches the content of the identifier.
         * @param AI the type of the attestation identifier to match.
         */
        public inline fun <reified AI : AttestationIdentifier> matchingRegex(
            regex: Regex,
            crossinline contentToMatch: (AI) -> String,
        ): AttestationIdentifierPredicate =
            AttestationIdentifierPredicate { identifier ->
                identifier is AI && regex.matches(contentToMatch(identifier))
            }

        /**
         * Creates a predicate that matches an attestation identifier exactly.
         *
         * @param identifier the identifier to match.
         * @return a predicate that matches the given identifier exactly.
         */
        public fun equalsTo(identifier: AttestationIdentifier): AttestationIdentifierPredicate =
            AttestationIdentifierPredicate { it == identifier }

        /**
         * Creates a predicate that matches any of the given identifiers.
         *
         * @param identifiers the identifiers to match.
         * @return a predicate that matches any of the given identifiers.
         */
        public fun any(identifiers: Set<AttestationIdentifier>): AttestationIdentifierPredicate =
            AttestationIdentifierPredicate { it in identifiers }

        /**
         * Creates a predicate that matches [MDoc] attestations having a document type matching the given regex.
         *
         * @param regex the regex to match the document type against.
         *
         * @return a predicate that matches [MDoc] attestations with a document type matching the given regex.
         */
        public fun mdocMatching(regex: Regex): AttestationIdentifierPredicate =
            matchingRegex<MDoc>(regex, MDoc::docType)

        /**
         * Creates a predicate that matches [SDJwtVc] attestations having a vct claim matching the given regex.
         * @param regex the regex to match the vct claim against.
         * @return a predicate that matches [SDJwtVc] attestations with a vct claim matching the given regex.
         */
        public fun sdJwtVcMatching(regex: Regex): AttestationIdentifierPredicate =
            matchingRegex<SDJwtVc>(regex, SDJwtVc::vct)
    }
}

/**
 * An [AttestationIdentifierPredicate]
 * that matches attestations with the same identifier as the current one.
 */
public val AttestationIdentifier.predicate: AttestationIdentifierPredicate
    get() = AttestationIdentifierPredicate.equalsTo(this)

/**
 * A way of classifying attestations
 *
 * ```kotlin
 * val pidInMDoc = MDoc(docType = "eu.europa.ec.eudi.pid.1")
 * val pidInJwtVc = SDJwtVc(vct = "urn:eudi:pid:1")
 * val mdl = MDoc(docType = "org.iso.18013.5.1.mDL")
 * val isPidInMdoc = pidInMDoc.predicate
 * val isPidInJwtVc = pidInJwtVc.predicate
 * val isMdl = mdl.predicate
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
    /**
     * Creates a function that classifies an [AttestationIdentifier] into one of the given categories,
     * and then uses the given mappings functions to map the identifier to a result of type [T].
     *
     * @param ifPid the mapping function to use for PIDs
     * @param ifPubEaa the mapping function to use for public EAA identifiers
     * @param ifQEaa the mapping function to use for qualified EAA identifiers
     * @param ifEaa the mapping function to use for EAA identifiers with a specific use case
     *
     * @return a function that classifies an [AttestationIdentifier] into one of the given categories,
     * and then provides a result of type [T] based on the classification.
     * If the [AttestationIdentifier] is not classified, returns null.
     *
     * @param T the type of the result of the mapping function
     */
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
