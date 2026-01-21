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

import java.security.InvalidAlgorithmParameterException
import java.security.Provider
import java.security.cert.*

/**
 * A JVM-specific implementation of [ValidateCertificateChain]
 */
public class ValidateCertificateChainJvm(
    public val certificateFactory: CertificateFactory,
    private val certPathValidator: CertPathValidator,
    private val customization: PKIXParameters.() -> Unit,
) : ValidateCertificateChain<List<X509Certificate>, TrustAnchor> {

    public constructor(customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION) :
        this(X509_CERT_FACTORY, PKIX_CERT_VALIDATOR, customization)

    public constructor(provider: Provider, customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION) :
        this(x509CertFactory(provider), pkixCertValidator(provider), customization)

    override suspend fun invoke(
        chain: List<X509Certificate>,
        trustAnchors: Set<TrustAnchor>,
    ): ValidateCertificateChain.Outcome {
        require(chain.isNotEmpty()) { "Chain must not be empty" }
        return try {
            val pkixParameters =
                PKIXParameters(trustAnchors).apply(customization)
            val certPath = certificateFactory.generateCertPath(chain)
            certPathValidator.validate(certPath, pkixParameters)
            ValidateCertificateChain.Outcome.Trusted
        } catch (e: CertPathValidatorException) {
            ValidateCertificateChain.Outcome.Untrusted(e)
        } catch (e: InvalidAlgorithmParameterException) {
            ValidateCertificateChain.Outcome.Untrusted(e)
        }
    }

    public companion object {
        private const val X_509 = "X.509"
        public val X509_CERT_FACTORY: CertificateFactory get() = CertificateFactory.getInstance(X_509)
        public val PKIX_CERT_VALIDATOR: CertPathValidator get() = CertPathValidator.getInstance(PKIX)
        public fun x509CertFactory(provider: Provider): CertificateFactory =
            CertificateFactory.getInstance(X_509, provider)

        private const val PKIX = "PKIX"
        internal fun revocationEnabled(enabled: Boolean): PKIXParameters.() -> Unit = { isRevocationEnabled = enabled }
        internal val DEFAULT_CUSTOMIZATION: PKIXParameters.() -> Unit = revocationEnabled(false)
        public fun pkixCertValidator(provider: Provider): CertPathValidator =
            CertPathValidator.getInstance(PKIX, provider)
    }
}
