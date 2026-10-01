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

import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXConfiguration
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationMethod
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationMethodAnyPreferCRL
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationMethodAnyPreferOCSP
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationMethodCRL
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXRevocationMethodOCSP
import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import kotlin.coroutines.resume

/**
 * Revocation mechanism(s) `Security.framework` may use when checking a chain on iOS.
 *
 * Typed Kotlin view of the native `PKIXRevocationMethod`, which cinterop exposes as a plain
 * [PKIXRevocationMethod] integer with `PKIXRevocationMethod*` constants.
 * Whatever the method, a definitive revocation response is required.
 */
public enum class IosRevocationMethod(public val native: PKIXRevocationMethod) {
    /** OCSP only (the default). */
    OCSP(PKIXRevocationMethodOCSP),

    /** CRL only, fetched from the certificate's CRL Distribution Points. */
    CRL(PKIXRevocationMethodCRL),

    /** OCSP or CRL, whichever is advertised; OCSP is tried first when both are. */
    ANY_PREFER_OCSP(PKIXRevocationMethodAnyPreferOCSP),

    /** OCSP or CRL, whichever is advertised; CRL is tried first when both are. */
    ANY_PREFER_CRL(PKIXRevocationMethodAnyPreferCRL),
}

/**
 * iOS implementation of [ValidateCertificateChainUsingPKIX] backed by Apple's
 * `Security.framework` via the [PKIXValidator] bridge (no third-party crypto).
 *
 * The native bridge exposes a callback-based API; this class adapts it to a Kotlin
 * coroutine via [suspendCancellableCoroutine]. A failed evaluation is reported as
 * [CertificationChainValidation.NotTrusted] (a normal outcome, not an exception).
 *
 * @param nativeValidator the Objective-C bridge to Security.framework
 */
public class ValidateCertificateChainUsingPKIXIos(
    private val nativeValidator: PKIXValidator,
) : ValidateCertificateChainUsingPKIX<List<NSData>, NSData> {

    override suspend fun invoke(
        chain: List<NSData>,
        trustAnchors: NonEmptyList<NSData>,
    ): CertificationChainValidation<NSData> {
        val leaf = requireNotNull(chain.firstOrNull()) { "Chain cannot be empty" }
        val intermediates = chain.drop(1)

        // The native bridge evaluates synchronously and invokes the callback inline, so run it
        // on a background dispatcher to avoid blocking the caller's thread.
        return withContext(Dispatchers.Default) {
            suspendCancellableCoroutine { cont ->
                nativeValidator.validateCertificateChainWithLeafCertificate(
                    leafCertificate = leaf,
                    intermediateCertificates = intermediates,
                    trustAnchors = trustAnchors.list,
                    completion = { matchedAnchorDer, error ->
                        when {
                            error != null -> cont.resume(
                                CertificationChainValidation.NotTrusted(
                                    IllegalStateException(
                                        error.localizedDescription ?: "PKIX validation failed",
                                    ),
                                ),
                            )

                            matchedAnchorDer != null -> {
                                val trustedAnchor = trustAnchors.list.firstOrNull {
                                    it.contentEqualsData(matchedAnchorDer)
                                }
                                if (trustedAnchor != null) {
                                    cont.resume(CertificationChainValidation.Trusted(trustedAnchor))
                                } else {
                                    cont.resume(
                                        CertificationChainValidation.NotTrusted(
                                            IllegalStateException("Validated chain matched no provided trust anchor"),
                                        ),
                                    )
                                }
                            }

                            else -> cont.resume(
                                CertificationChainValidation.NotTrusted(
                                    IllegalStateException("Unknown validation error"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    public companion object {
        /**
         * Creates an instance using a native validator built from [configuration].
         *
         * Revocation checking is controlled by the configuration's `isRevocationEnabled` switch and
         * `revocationMethod`. The default is OCSP; see [IosRevocationMethod] for CRL and mixed modes.
         * Whatever the method, `Security.framework` requires a definitive revocation response, so a
         * chain whose status cannot be established is reported as [CertificationChainValidation.NotTrusted].
         *
         * @param configuration revocation and policy configuration (default: revocation enabled, OCSP)
         */
        public operator fun invoke(
            configuration: PKIXConfiguration = PKIXConfiguration(),
        ): ValidateCertificateChainUsingPKIXIos =
            ValidateCertificateChainUsingPKIXIos(nativeValidator = PKIXValidator(configuration = configuration))

        public operator fun invoke(
            isRevocationEnabled: Boolean,
        ): ValidateCertificateChainUsingPKIXIos = invoke(configuration = PKIXConfiguration(isRevocationEnabled = isRevocationEnabled))

        /**
         * Creates an instance using [revocationMethod] for revocation checking.
         *
         * @param revocationMethod the revocation mechanism(s) `Security.framework` may use
         * @param isRevocationEnabled whether revocation checking is performed at all (default: `true`)
         */
        public operator fun invoke(
            revocationMethod: IosRevocationMethod,
            isRevocationEnabled: Boolean = true,
        ): ValidateCertificateChainUsingPKIXIos =
            invoke(
                configuration = PKIXConfiguration(
                    isRevocationEnabled = isRevocationEnabled,
                    revocationMethod = revocationMethod.native,
                ),
            )
    }
}
