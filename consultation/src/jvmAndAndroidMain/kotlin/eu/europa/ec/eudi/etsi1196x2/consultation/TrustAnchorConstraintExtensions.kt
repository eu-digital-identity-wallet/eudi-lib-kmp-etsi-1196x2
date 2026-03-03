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

import kotlinx.coroutines.runBlocking
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
public fun TrustAnchor.validateAsPidProvider(): List<ConstraintValidationResult.Invalid> = runBlocking {
    val validator = LoTEX509CertificateValidators.pidProviderValidator()
    validator.validate(trustedCert)
}

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
public fun TrustAnchor.validateAsWalletProvider(): List<ConstraintValidationResult.Invalid> = runBlocking {
    val validator = LoTEX509CertificateValidators.walletProviderValidator()
    validator.validate(trustedCert)
}

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
public fun TrustAnchor.validateAsWrpacProvider(): List<ConstraintValidationResult.Invalid> = runBlocking {
    val validator = LoTEX509CertificateValidators.wrpacProviderValidator()
    validator.validate(trustedCert)
}

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
public fun TrustAnchor.validateAsWrprcProvider(): List<ConstraintValidationResult.Invalid> = runBlocking {
    val validator = LoTEX509CertificateValidators.wrprcProviderValidator()
    validator.validate(trustedCert)
}

/**
 * Validates a [TrustAnchor] against a custom validator.
 *
 * @param validator the validator to use
 * @return list of validation failures (empty if valid)
 */
public fun TrustAnchor.validateWith(
    validator: CertificateConstraintValidator<X509Certificate>,
): List<ConstraintValidationResult.Invalid> = runBlocking {
    validator.validate(trustedCert)
}

/**
 * Checks if a [TrustAnchor] is valid as a PID Provider certificate.
 *
 * @return true if valid, false otherwise
 */
public fun TrustAnchor.isValidAsPidProvider(): Boolean = validateAsPidProvider().isEmpty()

/**
 * Checks if a [TrustAnchor] is valid as a Wallet Provider certificate.
 *
 * @return true if valid, false otherwise
 */
public fun TrustAnchor.isValidAsWalletProvider(): Boolean = validateAsWalletProvider().isEmpty()

/**
 * Checks if a [TrustAnchor] is valid as a WRPAC Provider certificate.
 *
 * @return true if valid, false otherwise
 */
public fun TrustAnchor.isValidAsWrpacProvider(): Boolean = validateAsWrpacProvider().isEmpty()

/**
 * Checks if a [TrustAnchor] is valid as a WRPRC Provider certificate.
 *
 * @return true if valid, false otherwise
 */
public fun TrustAnchor.isValidAsWrprcProvider(): Boolean = validateAsWrprcProvider().isEmpty()

/**
 * Gets the trusted certificate from a [TrustAnchor].
 *
 * This is a convenience property for accessing the underlying X509Certificate.
 */
public val TrustAnchor.trustedCert: X509Certificate
    get() = trustedCert as? X509Certificate
        ?: throw IllegalArgumentException("TrustAnchor must contain an X509Certificate")
