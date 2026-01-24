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
package eu.europa.ec.eudi.etsi119602.consultation

/**
 * An abstraction for validating a certificate chain, given a set of trust anchors
 *
 * @param CHAIN type representing a certificate chain
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public fun interface ValidateCertificateChain<in CHAIN : Any, TRUST_ANCHOR : Any> {

    /**
     * Validates a certificate chain against a set of trust anchors
     * @param chain the certificate chain to validate
     * @param trustAnchors the set of trust anchors
     * @return the outcome of the validation
     */
    public suspend operator fun invoke(chain: CHAIN, trustAnchors: Set<TRUST_ANCHOR>): CertificationChainValidation<TRUST_ANCHOR>
}

/**
 * Represents the outcome of the validation
 * - [Trusted] if the chain is trusted, with the trust anchor
 * - [NotTrusted] if the chain is not trusted, with the cause of the failure
 * @param TRUST_ANCHOR type representing a trust anchor
 */
public sealed interface CertificationChainValidation<out TRUST_ANCHOR : Any> {
    public data class Trusted<out TRUST_ANCHOR : Any>(val trustAnchor: TRUST_ANCHOR) : CertificationChainValidation<TRUST_ANCHOR>
    public data class NotTrusted(val cause: Throwable) : CertificationChainValidation<Nothing>
}
