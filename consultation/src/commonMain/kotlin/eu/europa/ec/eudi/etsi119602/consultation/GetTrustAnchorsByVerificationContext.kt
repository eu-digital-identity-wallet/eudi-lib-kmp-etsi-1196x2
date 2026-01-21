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

public fun interface GetTrustAnchorsByVerificationContext<out TRUST_ANCHOR : Any> {
    public suspend operator fun invoke(verificationContext: IsChainTrusted.VerificationContext): Set<TRUST_ANCHOR>

    public operator fun plus(other: GetTrustAnchorsByVerificationContext<@UnsafeVariance TRUST_ANCHOR>): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
        GetTrustAnchorsByVerificationContext { signatureVerification ->
            this.invoke(signatureVerification) + other.invoke(signatureVerification)
        }

    public companion object {
        public fun <TRUST_ANCHOR : Any> usingLoTE(
            getListByProfile: GetListByProfile,
            createTrustAnchor: CreateTrustAnchor<TRUST_ANCHOR>,
        ): GetTrustAnchorsByVerificationContext<TRUST_ANCHOR> =
            GetTrustAnchorsByVerificationContext { signatureVerification ->
                val profile = signatureVerification.profile
                val serviceType = signatureVerification.serviceType()
                val listOfTrustedEntities = getListByProfile(profile)
                checkNotNull(listOfTrustedEntities) { "Unable to find List of Trusted Entities for ${profile.listAndSchemeInformation.type}" }
                val trustAnchorFactory = createTrustAnchor(profile, serviceType)
                buildSet {
                    listOfTrustedEntities.entities?.forEach { entity ->
                        entity.services.forEach { service ->
                            val srvInformation = service.information
                            if (srvInformation.typeIdentifier == serviceType) {
                                srvInformation.digitalIdentity.x509Certificates?.forEach { pkiObj ->
                                    add(trustAnchorFactory(pkiObj))
                                }
                            }
                        }
                    }
                }
            }
    }
}
