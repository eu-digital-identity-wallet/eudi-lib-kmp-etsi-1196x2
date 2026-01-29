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
package eu.europa.ec.eudi.etsi1196x2.consultation

import java.security.Provider
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant

public object JvmSecurity {

    private const val X_509 = "X.509"
    public val DefaultX509Factory: CertificateFactory
        get() = CertificateFactory.getInstance(X_509)

    public fun x509CertFactory(provider: Provider): CertificateFactory =
        CertificateFactory.getInstance(X_509, provider)

    public fun x509CertFactory(provider: String): CertificateFactory =
        CertificateFactory.getInstance(X_509, provider)

    private const val PKIX = "PKIX"
    public val DefaultPKIXValidator: CertPathValidator
        get() = CertPathValidator.getInstance(PKIX)

    public fun pkixCertValidator(provider: Provider): CertPathValidator =
        CertPathValidator.getInstance(PKIX, provider)

    public fun pkixCertValidator(provider: String): CertPathValidator =
        CertPathValidator.getInstance(PKIX, provider)

    public fun withRevocationEnabled(enabled: Boolean): PKIXParameters.() -> Unit = {
        isRevocationEnabled = enabled
    }

    public fun withValidationDate(at: Instant): PKIXParameters.() -> Unit = {
        date = Date.from(at.toJavaInstant())
    }

    /**
     * Default trust anchor creator that creates a trust anchor with no name constraints.
     */
    public val DefaultTrustAnchorCreator: TrustAnchorCreator<X509Certificate, TrustAnchor> =
        trustAnchorCreator()

    public fun trustAnchorCreator(
        nameConstraints: ByteArray? = null,
    ): TrustAnchorCreator<X509Certificate, TrustAnchor> =
        TrustAnchorCreator { TrustAnchor(it, nameConstraints) }
}
