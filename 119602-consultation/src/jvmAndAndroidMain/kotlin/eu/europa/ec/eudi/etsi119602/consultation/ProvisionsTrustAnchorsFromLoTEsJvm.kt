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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi119602.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi119602.x509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

public fun ProvisionTrustAnchorsFromLoTEs.Companion.eudiwJvm(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMata<VerificationContext, X509Certificate>>,
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    directTrust: ValidateCertificateChainUsingDirectTrust<List<X509Certificate>, TrustAnchor, X509CertificateIdentify> = ValidateCertificateChainUsingDirectTrustJvm,
    pkix: ValidateCertificateChainUsingPKIX<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainUsingPKIXJvm(),
): ProvisionTrustAnchorsFromLoTEs<List<X509Certificate>, VerificationContext, TrustAnchor, X509Certificate> =
    jvm(
        loadLoTEAndPointers,
        svcTypePerCtx,
        ::defaultCreateTrustAnchors,
        continueOnProblem,
        directTrust,
        pkix,
        ::defaultGetCertificateInfo,
    )

public fun <CTX : Any> ProvisionTrustAnchorsFromLoTEs.Companion.jvm(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMata<CTX, X509Certificate>>,
    createTrustAnchors: (ServiceDigitalIdentity) -> List<TrustAnchor> = ::defaultCreateTrustAnchors,
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    directTrust: ValidateCertificateChainUsingDirectTrust<List<X509Certificate>, TrustAnchor, X509CertificateIdentify> = ValidateCertificateChainUsingDirectTrustJvm,
    pkix: ValidateCertificateChainUsingPKIX<List<X509Certificate>, TrustAnchor> = ValidateCertificateChainUsingPKIXJvm(),
    getCertInfo: suspend (X509Certificate) -> String = ::defaultGetCertificateInfo,
): ProvisionTrustAnchorsFromLoTEs<List<X509Certificate>, CTX, TrustAnchor, X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs(
        loadLoTEAndPointers,
        svcTypePerCtx,
        createTrustAnchors,
        extractCertificate = { trustAnchor -> trustAnchor.trustedCert },
        continueOnProblem = continueOnProblem,
        getCertInfo = getCertInfo,
        directTrust = directTrust,
        pkix = pkix,
    )

public fun defaultCreateTrustAnchors(serviceDigitalIdentity: ServiceDigitalIdentity): List<TrustAnchor> =
    serviceDigitalIdentity.x509Certificates.orEmpty().map {
        TrustAnchor(it.x509Certificate(), null)
    }

internal suspend fun defaultGetCertificateInfo(cert: X509Certificate): String =
    withContext(Dispatchers.IO) { cert.identity().toString() }
