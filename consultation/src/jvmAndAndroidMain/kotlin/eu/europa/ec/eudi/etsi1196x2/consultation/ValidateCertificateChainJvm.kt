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

import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm.Companion.DEFAULT_CUSTOMIZATION
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainJvm.Companion.DEFAULT_DISPATCHER
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidAlgorithmParameterException
import java.security.Provider
import java.security.cert.*

/**
 * A JVM-specific implementation of [ValidateCertificateChain]
 *
 * @param dispatcher the coroutine dispatcher to use for validating certificate chains.
 *        Defaults to [ValidateCertificateChainJvm.DEFAULT_DISPATCHER]
 * @param certificateFactory the certificate factory to use for validating certificate chains.
 *        Defaults to [JvmSecurity.DefaultX509Factory]
 * @param certPathValidator the certification path validator to use for validating certificate chains.
 *        Defaults to [JvmSecurity.DefaultPKIXValidator]
 * @param customization customization for PKIX parameters.
 *        Defaults to [ValidateCertificateChainJvm.DEFAULT_CUSTOMIZATION]
 */
public class ValidateCertificateChainJvm(
    dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
    private val certificateFactory: CertificateFactory,
    private val certPathValidator: CertPathValidator,
    private val customization: PKIXParameters.() -> Unit,
) : ValidateCertificateChain<List<X509Certificate>, TrustAnchor> {

    /**
     * Alternative constructor that uses the default [Provider]s
     * for certificate factory and certification path validator.
     * @param customization customization for PKIX parameters. Defaults to [DEFAULT_CUSTOMIZATION]
     * @param dispatcher the coroutine dispatcher to use for validating certificate chains. Defaults to [DEFAULT_DISPATCHER]
     */
    public constructor(
        dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        dispatcher,
        JvmSecurity.DefaultX509Factory,
        JvmSecurity.DefaultPKIXValidator,
        customization,
    )

    /**
     * Alternative constructor that uses the default [Provider]s
     * @param customization customization for PKIX parameters. Defaults to [DEFAULT_CUSTOMIZATION]
     * @param dispatcher the coroutine dispatcher to use for validating certificate chains. Defaults to [DEFAULT_DISPATCHER]
     * @param provider the provider to use for certificate factory and certification path validator.
     */
    public constructor(
        dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
        provider: Provider,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        dispatcher,
        JvmSecurity.x509CertFactory(provider),
        JvmSecurity.pkixCertValidator(provider),
        customization,
    )

    /**
     * Alternative constructor that uses the default [Provider]s
     * @param customization customization for PKIX parameters. Defaults to [DEFAULT_CUSTOMIZATION]
     * @param dispatcher the coroutine dispatcher to use for validating certificate chains. Defaults to [DEFAULT_DISPATCHER]
     * @param provider the provider name to use for certificate factory and certification path validator.
     */
    public constructor(
        dispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
        provider: String,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        dispatcher,
        JvmSecurity.x509CertFactory(provider),
        JvmSecurity.pkixCertValidator(provider),
        customization,
    )

    private val coroutineContext = CoroutineName(name = "ValidateCertificateChainJvm") + dispatcher

    override suspend fun invoke(
        chain: List<X509Certificate>,
        trustAnchors: NonEmptyList<TrustAnchor>,
    ): CertificationChainValidation<TrustAnchor> =
        withContext(coroutineContext) {
            require(chain.isNotEmpty()) { "Chain must not be empty" }
            try {
                val pkixParameters = PKIXParameters(trustAnchors.list.toSet()).apply(customization)
                val certPath = certificateFactory.generateCertPath(chain)
                val result = certPathValidator.validate(certPath, pkixParameters)
                result.trusted()
            } catch (e: CertPathValidatorException) {
                e.notTrusted()
            } catch (e: InvalidAlgorithmParameterException) {
                e.notTrusted()
            }
        }

    public companion object {

        /**
         * Default customization for PKIX parameters.
         * Does nothing.
         */
        public val DEFAULT_CUSTOMIZATION: PKIXParameters.() -> Unit = { }

        /**
         * Default coroutine dispatcher for validating certificate chains.
         * Points to [Dispatchers.IO]
         */
        public val DEFAULT_DISPATCHER: CoroutineDispatcher = Dispatchers.IO

        /**
         * Default implementation of [ValidateCertificateChainJvm] for JVM and Android platforms.
         * Use as [DEFAULT_DISPATCHER] and [DEFAULT_CUSTOMIZATION] and
         * [JvmSecurity.DefaultX509Factory] and
         * [JvmSecurity.DefaultPKIXValidator]
         */
        public val Default: ValidateCertificateChainJvm get() = ValidateCertificateChainJvm(
            customization = DEFAULT_CUSTOMIZATION,
            dispatcher = DEFAULT_DISPATCHER,
        )

        private fun CertPathValidatorResult.trusted(): CertificationChainValidation.Trusted<TrustAnchor> {
            check(this is PKIXCertPathValidatorResult) { "Unexpected result type: ${this::class}" }
            return CertificationChainValidation.Trusted(trustAnchor)
        }

        private fun Throwable.notTrusted(): CertificationChainValidation.NotTrusted =
            CertificationChainValidation.NotTrusted(this)
    }
}
