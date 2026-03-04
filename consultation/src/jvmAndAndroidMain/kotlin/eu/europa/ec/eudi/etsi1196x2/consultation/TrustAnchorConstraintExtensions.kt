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

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintEvaluation
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateCertificateConstraint
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * Extension functions for validating [TrustAnchor] certificates against LoTE constraints.
 *
 * These functions provide convenient ways to validate trust anchors (typically from LoTE)
 * against the certificate requirements specified in ETSI TS 119 602 for different provider types.
 *
 * @see LoTEX509CertificateValidators
 */

/**
 * Validates a [TrustAnchor] as a PID Provider certificate.
 *
 * Per ETSI TS 119 602 Annex D, PID Provider certificates must be:
 * - End-entity certificates (cA=FALSE)
 * - Contain QCStatement with id-etsi-qct-pid
 * - Have digitalSignature key usage
 * - Be valid at the current time
 * - Contain the appropriate certificate policy
 *
 * @return list of validation failures (empty if valid)
 */
public suspend fun TrustAnchor.validateAsPidProvider(): CertificateConstraintEvaluation =
    validateWith(LoTEX509CertificateValidators.pidProviderCertificateConstraintsEvaluator())

/**
 * Validates a [TrustAnchor] as a Wallet Provider certificate.
 *
 * Per ETSI TS 119 602 Annex E, Wallet Provider certificates must be:
 * - End-entity certificates (cA=FALSE)
 * - Contain QCStatement with id-etsi-qct-wal
 * - Have digitalSignature key usage
 * - Be valid at the current time
 * - Contain the appropriate certificate policy
 *
 * @return list of validation failures (empty if valid)
 */
public suspend fun TrustAnchor.validateAsWalletProvider(): CertificateConstraintEvaluation =
    validateWith(LoTEX509CertificateValidators.walletProviderCertificateConstraintsEvaluator())

/**
 * Validates a [TrustAnchor] as a WRPAC Provider certificate.
 *
 * Per ETSI TS 119 602 Annex F, WRPAC Provider certificates must be:
 * - CA certificates (cA=TRUE)
 * - Have keyCertSign key usage
 * - Be valid at the current time
 * - Contain the appropriate certificate policy (ETSI TS 119 411-8)
 *
 * @return list of validation failures (empty if valid)
 */
public suspend fun TrustAnchor.validateAsWrpacProvider(): CertificateConstraintEvaluation =
    validateWith(LoTEX509CertificateValidators.wrpacProviderCertificateConstraintsEvaluator())

/**
 * Validates a [TrustAnchor] as a WRPRC Provider certificate.
 *
 * Per ETSI TS 119 602 Annex G, WRPRC Provider certificates must be:
 * - CA certificates (cA=TRUE)
 * - Have keyCertSign key usage
 * - Be valid at the current time
 * - Contain the appropriate certificate policy
 *
 * @return list of validation failures (empty if valid)
 */
public suspend fun TrustAnchor.validateAsWrprcProvider(): CertificateConstraintEvaluation =
    validateWith(LoTEX509CertificateValidators.wrprcProviderCertificateConstraintsEvaluator())

private suspend fun TrustAnchor.validateWith(
    evaluateCertificateConstraint: EvaluateCertificateConstraint<X509Certificate>,
): CertificateConstraintEvaluation = evaluateCertificateConstraint(trustedCert)
