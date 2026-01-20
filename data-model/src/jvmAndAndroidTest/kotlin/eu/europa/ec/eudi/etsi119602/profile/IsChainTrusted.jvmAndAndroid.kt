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
package eu.europa.ec.eudi.etsi119602.profile

import eu.europa.ec.eudi.etsi119602.*
import java.security.InvalidAlgorithmParameterException
import java.security.Provider
import java.security.cert.*

//
// JVM Implementation
//

class IsChainTrustedJvm(
    private val certificateFactory: () -> CertificateFactory,
    private val certPathValidator: () -> CertPathValidator,
    private val getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
    private val customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
) : IsChainTrusted {

    constructor(
        getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        { CertificateFactory.getInstance(X_509) },
        { CertPathValidator.getInstance(PKIX) },
        getTrustAnchorsByVerification,
        customization,
    )

    constructor(
        provider: Provider,
        getTrustAnchorsByVerification: GetTrustAnchorsByVerification,
        customization: PKIXParameters.() -> Unit = DEFAULT_CUSTOMIZATION,
    ) : this(
        { CertificateFactory.getInstance(X_509, provider) },
        { CertPathValidator.getInstance(PKIX, provider) },
        getTrustAnchorsByVerification,
        customization,
    )

    override suspend fun invoke(
        chain: List<X509Certificate>,
        signatureVerification: IsChainTrusted.SignatureVerification,
    ): IsChainTrusted.Outcome {
        require(chain.isNotEmpty()) { "Chain must not be empty" }
        val trustAnchors = getTrustAnchorsByVerification(signatureVerification)
        return validate(chain, trustAnchors)
    }

    private fun validate(chain: List<X509Certificate>, anchors: Set<TrustAnchor>): IsChainTrusted.Outcome =
        try {
            val pkixParameters =
                PKIXParameters(anchors.toSet()).apply(customization)
            val certPath = certificateFactory().generateCertPath(chain)
            certPathValidator().validate(certPath, pkixParameters)
            IsChainTrusted.Outcome.Trusted
        } catch (e: CertPathValidatorException) {
            IsChainTrusted.Outcome.Untrusted(e)
        } catch (e: InvalidAlgorithmParameterException) {
            IsChainTrusted.Outcome.Untrusted(e)
        }

    companion object {
        val DEFAULT_CUSTOMIZATION: PKIXParameters.() -> Unit = { isRevocationEnabled = false }
        private const val X_509 = "X.509"
        private const val PKIX = "PKIX"
    }
}

fun interface GetTrustAnchorsByVerification {
    suspend operator fun invoke(signatureVerification: IsChainTrusted.SignatureVerification): Set<TrustAnchor>

    operator fun plus(other: GetTrustAnchorsByVerification): GetTrustAnchorsByVerification =
        GetTrustAnchorsByVerification { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    companion object {
        fun usingLoTE(
            getListByProfile: GetListByProfile,
            createTrustAnchor: CreateTrustAnchor = CreateTrustAnchor.Default,
        ): GetTrustAnchorsByVerification =
            GetTrustAnchorsByVerification { signatureVerification ->
                suspend fun EUListOfTrustedEntitiesProfile.getList(): ListOfTrustedEntities =
                    getListByProfile(this.listAndSchemeInformation.type).also { it.ensureProfile() }

                val profile = signatureVerification.profile
                val serviceType = signatureVerification.serviceType()
                val listOfTrustedEntities = profile.getList()
                val certificates = listOfTrustedEntities.certificatesOf(serviceType)
                val trustAnchorFactory = createTrustAnchor(profile, serviceType)
                certificates.map(trustAnchorFactory).toSet()
            }
    }
}

fun interface CreateTrustAnchor {
    operator fun invoke(profile: EUListOfTrustedEntitiesProfile, serviceType: URI): (X509Certificate) -> TrustAnchor

    companion object {
        val Default: CreateTrustAnchor = CreateTrustAnchor { _, _ -> { TrustAnchor(it, null) } }
    }
}

fun interface GetListByProfile {
    suspend operator fun invoke(loteType: LoTEType): ListOfTrustedEntities
}

data class LoTERemoteRegistry(
    val locations: Map<LoTEType, Pair<URI, X509Certificate>>,
)

data class LoteRepoEntity(
    val loteType: LoTEType,
    val version: Int,
    val content: String,
    val listIssueDateTime: LoTEDateTime,
    val nextUpdate: LoTEDateTime,
)
