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

import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXJvm
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

@SensitiveApi
fun getTrustAnchorsProvisioner(
    loadLoTE: LoadLoTE<String>,
    svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>>,
): ProvisionTrustAnchorsFromLoTEs<List<X509Certificate>, VerificationContext, TrustAnchor, X509Certificate> =
    ProvisionTrustAnchorsFromLoTEs.eudiwJvm(
        loadLoTEAndPointers = LoadLoTEAndPointers(
            constraints = LoadLoTEAndPointers.Constraints.DoNotLoadOtherPointers,
            verifyJwtSignature = NotValidating,
            loadLoTE = loadLoTE,
        ),
        svcTypePerCtx = svcTypePerCtx,
        pkix = ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = false }),
    )
