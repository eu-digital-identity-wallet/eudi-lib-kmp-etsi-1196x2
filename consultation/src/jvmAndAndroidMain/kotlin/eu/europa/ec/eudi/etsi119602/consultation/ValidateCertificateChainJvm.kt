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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    public constructor(
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        JvmSecurity.X509_CERT_FACTORY,
        JvmSecurity.PKIX_CERT_VALIDATOR,
        customization,
    )

    public constructor(
        provider: Provider,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        JvmSecurity.x509CertFactory(provider),
        JvmSecurity.pkixCertValidator(provider),
        customization,
    )

    public constructor(
        provider: String,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        JvmSecurity.x509CertFactory(provider),
        JvmSecurity.pkixCertValidator(provider),
        customization,
    )

    override suspend fun invoke(
        chain: List<X509Certificate>,
        trustAnchors: Set<TrustAnchor>,
    ): ValidateCertificateChain.Outcome<TrustAnchor> =
        withContext(CoroutineName(name = "ValidateCertificateChainJvm") + Dispatchers.Default) {
            require(chain.isNotEmpty()) { "Chain must not be empty" }
            try {
                val pkixParameters = PKIXParameters(trustAnchors).apply(customization)
                val certPath = certificateFactory.generateCertPath(chain)
                val validationResult = certPathValidator.validate(certPath, pkixParameters)
                validationResult.trusted()
            } catch (e: CertPathValidatorException) {
                e.notTrusted()
            } catch (e: InvalidAlgorithmParameterException) {
                e.notTrusted()
            }
        }

    private fun CertPathValidatorResult.trusted(): ValidateCertificateChain.Outcome.Trusted<TrustAnchor> {
        check(this is PKIXCertPathValidatorResult) { "Unexpected result type: ${this::class}" }
        return ValidateCertificateChain.Outcome.Trusted(trustAnchor)
    }
    private fun Throwable.notTrusted(): ValidateCertificateChain.Outcome.NotTrusted =
        cause?.notTrusted() ?: ValidateCertificateChain.Outcome.NotTrusted(this)

    public companion object {
        internal val DEFAULT_CUSTOMIZATION: PKIXParameters.() -> Unit = { }
    }
}
