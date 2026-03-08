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

import eu.europa.ec.eudi.etsi119602.x509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.ComposeChainTrust
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrustJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

suspend fun composeChainTrust(
    loadLoTE: LoadLoTE<String>,
    loteLocationsSupported: SupportedLists<String>,
    svcTypePerCtx: SupportedLists<LotEMata<VerificationContext, X509Certificate>>,
    provider: String? = null,
): ComposeChainTrust<List<X509Certificate>, VerificationContext, TrustAnchor> {
    val provisionTrustAnchorsFromLoTEs = ProvisionTrustAnchorsFromLoTEs(
        loadLoTEAndPointers = LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.LoadOtherPointers(
                otherLoTEParallelism = 1,
                maxDepth = 1,
                maxLists = 2,
            ),
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,

        ),
        createTrustAnchors = { serviceDigitalIdentity ->
            serviceDigitalIdentity.x509Certificates.orEmpty()
                .map { TrustAnchor(it.x509Certificate(provider), null) }
        },
        extractCertificate = { it.trustedCert },
        getCertInfo = { "Info[subject=${it.subjectX500Principal}-sn=${it.serialNumber}]" },
        directTrust = ValidateCertificateChainUsingDirectTrustJvm,
        pkix = ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = false }),
        continueOnProblem = ContinueOnProblem.AlwaysIfDownloaded,
        svcTypePerCtx = svcTypePerCtx,
    )
    return provisionTrustAnchorsFromLoTEs.invoke(loteLocationsSupported)
}
