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
package eu.europa.ec.eudi.etsi119602

import java.security.NoSuchProviderException
import java.security.Provider
import java.security.cert.*

@Throws(CertificateException::class, NoSuchProviderException::class)
public fun PkiOb.x509Certificate(provider: String? = null): X509Certificate {
    val factory = provider
        ?.let { CertificateFactory.getInstance(X_509, it) }
        ?: CertificateFactory.getInstance(X_509)
    return factory.x509CertificateOf(this)
}

@Throws(CertificateException::class, NoSuchProviderException::class)
public fun PkiOb.x509Certificate(provider: Provider): X509Certificate =
    CertificateFactory.getInstance(X_509, provider).x509CertificateOf(this)

private fun CertificateFactory.x509CertificateOf(pkiOb: PkiOb): X509Certificate =
    generateCertificate(pkiOb.value.inputStream()) as X509Certificate

private const val X_509 = "X.509"
private const val PKIX = "PKIX"

public class IsTrustAnchorOfChain private constructor(
    private val chain: List<X509Certificate>,
    private val certificateFactory: CertificateFactory,
    private val certPathValidator: CertPathValidator,
) : suspend (ServiceDigitalIdentity) -> Boolean {
    public constructor(chain: List<X509Certificate>) : this(
        chain,
        CertificateFactory.getInstance(X_509),
        CertPathValidator.getInstance(PKIX),
    )

    public constructor(chain: List<X509Certificate>, provider: String) : this(
        chain,
        CertificateFactory.getInstance(X_509, provider),
        CertPathValidator.getInstance(PKIX, provider),
    )

    public constructor(chain: List<X509Certificate>, provider: Provider) : this(
        chain,
        CertificateFactory.getInstance(X_509, provider),
        CertPathValidator.getInstance(PKIX, provider),
    )

    override suspend operator fun invoke(serviceDigitalIdentity: ServiceDigitalIdentity): Boolean {
        val certPath = certificateFactory.generateCertPath(chain)
        val pkixParameters = run {
            val anchors =
                serviceDigitalIdentity.x509Certificates.orEmpty().map { pkiObj ->
                    val anchorCert = certificateFactory.x509CertificateOf(pkiObj)
                    TrustAnchor(anchorCert, null)
                }
            PKIXParameters(anchors.toSet())
        }
        return try {
            certPathValidator.validate(certPath, pkixParameters) != null
        } catch (_: Throwable) {
            false
        }
    }
}
