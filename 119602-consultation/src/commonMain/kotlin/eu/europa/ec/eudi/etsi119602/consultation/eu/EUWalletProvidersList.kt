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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.*
import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

/**
 * A LoTE profile aimed at supporting the publication by the European Commission of a list of
 * Wallet providers according to CIR 2024/2980 Article 5(2).
 *
 * **Important:** Per ETSI TS 119 602 Annex E, the ServiceDigitalIdentity may contain either:
 * - End-entity certificates (Direct Trust validation)
 * - CA certificates (PKIX validation)
 *
 * This profile supports **both** validation methods as specified in LoTE-Certificate-Validation.md v4.5.
 */
public val EUWalletProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_WALLET_PROVIDERS_LOTE),
            statusDeterminationApproach = ETSI19602.EU_WALLET_PROVIDERS_STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(
                MultiLanguageURI.en(URIValue(ETSI19602.EU_WALLET_PROVIDERS_SCHEME_COMMUNITY_RULES)),
            ),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE,
                revocation = ETSI19602.EU_WALLET_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            serviceDigitalIdentityMustHaveCertificates = true,
            serviceStatuses = emptySet(),
            serviceDigitalIdentityCertificateType = ServiceDigitalIdentityCertificateType.EndEntityOrCA,

        ),
        endEntityCertificateProfile = walletProviderCertificateProfile(at = null),

    )

/**
 * Creates constraints for Wallet Provider end-entity certificates in LoTE.
 *
 * Per ETSI TS 119 602 Annex E and ETSI TS 119 412-6:
 * - Certificate type: End-entity ONLY (cA=FALSE)
 * - QCStatement: id-etsi-qct-wal (0.4.0.194126.1.2) REQUIRED in the QCStatement extension
 * - Key Usage: digitalSignature REQUIRED
 * - Validity: Must be valid at validation time
 * - Certificate Policy: Presence REQUIRED per EN 319 412-2 §4.3.3 (TSP-defined OID, not validated)
 * - AIA: Required if CA-issued, not required if self-signed
 *
 * **Note on Certificate Policy OIDs:** Per EN 319 412-2 §4.3.3, the certificatePolicies extension
 * shall be present and shall contain at least one certificate policy OID that reflects the practices
 * and procedures undertaken by the CA. However, ETSI TS 119 412-6 does NOT mandate specific policy
 * OID values for Wallet providers - these are TSP-defined. The validator checks for the presence of
 * the certificatePolicies extension but does not validate specific OID values.
 *
 * **Note on QCStatement vs Certificate Policy:** The OID `id-etsi-qct-wal` is a **QCStatement type
 * OID** (QcType) that MUST appear in the QCStatement extension, NOT in the certificatePolicies extension.
 *
 * @return a validator configured for Wallet Provider end-entity certificates
 */
public fun walletProviderCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    requireEndEntityCertificate()
    requireQcStatement(ETSI119412.ID_ETSI_QCT_WAL, true)
    requireDigitalSignature()
    requireValidAt(at)
    requirePolicyPresence()
    requireAiaForCaIssued()
}

/**
 * Creates constraints for Wallet Provider CA certificates in LoTE.
 *
 * When the LoTE contains a CA certificate (for PKIX validation), different constraints apply:
 * - Certificate type: CA (cA=TRUE)
 * - QCStatement: NOT required (QCStatements are for end-entity certificates only)
 * - Key Usage: keyCertSign REQUIRED (for issuing certificates)
 * - Validity: Must be valid at validation time
 * - Certificate Policy: NOT required for CA certificates
 * - AIA: NOT required (this is a trust anchor)
 *
 * @return a validator configured for Wallet Provider CA certificates
 */
public fun walletProviderCACertificateProfile(at: Instant? = null, maxPathLen: Int? = null): CertificateProfile =
    certificateProfile {
        requireCaCertificate(maxPathLen)
        requireKeyCertSign()
        requireValidAt(at)
    }
