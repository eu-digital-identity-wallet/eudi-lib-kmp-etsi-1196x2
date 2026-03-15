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

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationIdentifier
import eu.europa.ec.eudi.etsi1196x2.consultation.CertificationChainValidation
import eu.europa.ec.eudi.etsi1196x2.consultation.IsChainTrustedForAttestation
import eu.europa.ec.eudi.sdjwt.DefaultSdJwtOps
import eu.europa.ec.eudi.sdjwt.JwtAndClaims
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.SdJwtVcSpec
import eu.europa.ec.eudi.sdjwt.SdJwtVcVerifier
import eu.europa.ec.eudi.sdjwt.vc.CheckWithTokenStatusList
import eu.europa.ec.eudi.sdjwt.vc.IssuerVerificationMethod
import eu.europa.ec.eudi.sdjwt.vc.SdJwtVcVerifier
import eu.europa.ec.eudi.sdjwt.vc.TypeMetadataPolicy
import eu.europa.ec.eudi.sdjwt.vc.X509CertificateTrust
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

@Suppress("unused")
object IntegrationWithSdJwtVc {
    fun <CHAIN : Any> IsChainTrustedForAttestation<CHAIN, *>.sdJwtVcIssuerTrust(): X509CertificateTrust<CHAIN> =
        X509CertificateTrust { chain, claims ->
            val vct =
                claims[SdJwtVcSpec.VCT]
                    ?.takeIf { it is JsonPrimitive && it.jsonPrimitive.isString }
                    ?.jsonPrimitive?.contentOrNull
                    ?.let { AttestationIdentifier.SDJwtVc(it) }

            val validation = vct?.let { issuance(chain, it) }
            when (validation) {
                is CertificationChainValidation.NotTrusted -> false
                is CertificationChainValidation.Trusted<*> -> {
                    if (validation.trustAnchor is TrustAnchor) {
                        println("SD-JWT-VC signed by: ${(validation.trustAnchor as TrustAnchor).trustedCert.subjectX500Principal}")
                    }
                    true
                }
                null -> false
            }
        }

    fun <CHAIN : Any, TRUST_ANCHOR : Any> sdJwtVcVerificationMethod(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<CHAIN, TRUST_ANCHOR>,
    ): IssuerVerificationMethod.UsingX5c<CHAIN> =
        IssuerVerificationMethod.usingX5c(
            x509CertificateTrust = isChainTrustedForAttestation.sdJwtVcIssuerTrust(),
        )

    fun defaultSdJwtVcVerifier(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<List<X509Certificate>, TrustAnchor>,
        typeMetadataPolicy: TypeMetadataPolicy,
        checkStatus: CheckWithTokenStatusList?,
    ): SdJwtVcVerifier<JwtAndClaims> =
        DefaultSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = sdJwtVcVerificationMethod(isChainTrustedForAttestation),
            typeMetadataPolicy = typeMetadataPolicy,
            checkStatus = checkStatus,
        )

    fun nimbusSdJwtVcVerifier(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<List<X509Certificate>, TrustAnchor>,
        typeMetadataPolicy: TypeMetadataPolicy,
        checkStatus: CheckWithTokenStatusList?,
    ): SdJwtVcVerifier<SignedJWT> =
        NimbusSdJwtOps.SdJwtVcVerifier(
            issuerVerificationMethod = sdJwtVcVerificationMethod(isChainTrustedForAttestation),
            typeMetadataPolicy = typeMetadataPolicy,
            checkStatus = checkStatus,
        )
}
