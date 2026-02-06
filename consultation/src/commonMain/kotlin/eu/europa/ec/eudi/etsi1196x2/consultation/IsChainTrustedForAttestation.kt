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
public interface AttestationIdentifier {
    public companion object
}

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
     * Classifies an attestation identifier using the configured predicates.
     * At most one match is expected.
     *
     * @param identifier the attestation identifier to classify.
     * @return the classification of the given identifier, or null if the identifier does not match any predicate.
     * @throws IllegalStateException if the identifier matches multiple predicates.
     */
    @Throws(IllegalStateException::class)
    public fun classify(identifier: AttestationIdentifier): Match? {
        val matches = match(identifier)
        check(matches.size <= 1) {
            "AttestationIdentifier $identifier matches multiple predicates: ${matches.joinToString()}"
        }
        return matches.firstOrNull()
    }

    /**
     * Matches an attestation identifier using the configured predicates.
     * Normally, at most one match is expected.
     * If multiple matches are found, this is a signed of an
     * invalid configuration of the [AttestationClassifications] instance.
     *
     * @param identifier the attestation identifier to classify.
     * @return a list of matches for the given identifier
     */
    public fun match(identifier: AttestationIdentifier): List<Match> =
        buildList {
            if (pids.test(identifier)) add(Match.PID)
            if (pubEAAs.test(identifier)) add(Match.PubEAA)
            if (qEAAs.test(identifier)) add(Match.QEAA)
            eaAs.forEach { (useCase, predicate) ->
                if (predicate.test(identifier)) add(Match.EAA(useCase))
            }
        }

    /**
     * Represents the classification of an attestation identifier
     */
    public sealed interface Match {
        public object PID : Match
        public object PubEAA : Match
        public object QEAA : Match
        public data class EAA(val useCase: String) : Match

        public fun <T> fold(
            ifPid: T,
            ifPubEaa: T,
            ifQEaa: T,
            ifEaa: (String) -> T,
        ): T = when (this) {
            PID -> ifPid
            PubEAA -> ifPubEaa
            QEAA -> ifQEaa
            is EAA -> ifEaa(useCase)
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
    private val isChainTrustedForContext: IsChainTrustedForContextF<CHAIN, TRUST_ANCHOR>,
    private val classifications: AttestationClassifications,
) {

    private fun issuanceOf(identifier: AttestationIdentifier): VerificationContext? =
        classifications.classify(identifier)?.fold(
            ifPid = VerificationContext.PID,
            ifPubEaa = VerificationContext.PubEAA,
            ifQEaa = VerificationContext.QEAA,
            ifEaa = { useCase: String -> VerificationContext.EAA(useCase) },
        )

    private fun revocationContextOf(identifier: AttestationIdentifier): VerificationContext? =
        classifications.classify(identifier)?.fold(
            ifPid = VerificationContext.PIDStatus,
            ifPubEaa = VerificationContext.PubEAAStatus,
            ifQEaa = VerificationContext.QEAAStatus,
            ifEaa = { useCase: String -> VerificationContext.EAAStatus(useCase) },
        )

    /**
     * Validates a certificate chain for issuance of an attestation.
     *
     * @param chain the certificate chain to be validated
     * @param identifier the attestation identifier
     * @return the result of the validation
     *
     * @throws IllegalStateException if the identifier matches multiple classifications
     */
    @Throws(IllegalStateException::class)
    public suspend fun issuance(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        issuanceOf(identifier)?.let { isChainTrustedForContext(chain, it) }

    /**
     * Validates a certificate chain for revocation of an attestation.
     *
     * @param chain the certificate chain to be validated
     * @param identifier the attestation identifier
     * @return the result of the validation
     *
     * @throws IllegalStateException if the identifier matches multiple classifications
     */
    @Throws(IllegalStateException::class)
    public suspend fun revocation(
        chain: CHAIN,
        identifier: AttestationIdentifier,
    ): CertificationChainValidation<TRUST_ANCHOR>? =
        revocationContextOf(identifier)?.let { isChainTrustedForContext(chain, it) }

    public companion object
}
