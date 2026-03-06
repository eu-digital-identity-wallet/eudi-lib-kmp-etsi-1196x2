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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementConstraint

/**
 * A LoTE profile aimed at supporting the publication by the European Commission of a list of
 * wallet providers according to CIR 2024/2980 i.2 Article 5(2)
 */
public val EUPIDProvidersList: EUListOfTrustedEntitiesProfile =
    EUListOfTrustedEntitiesProfile(
        listAndSchemeInformation =
        EUListAndSchemeInformationProfile(
            type = LoTEType.of(ETSI19602.EU_PID_PROVIDERS_LOTE),
            statusDeterminationApproach = ETSI19602.EU_PID_PROVIDERS_STATUS_DETERMINATION_APPROACH,
            schemeCommunityRules = listOf(MultiLanguageURI.en(URIValue(ETSI19602.EU_PID_PROVIDERS_SCHEME_COMMUNITY_RULES))),
            schemeTerritory = CountryCode.EU,
            maxMonthsUntilNextUpdate = 6,
            historicalInformationPeriod = ValueRequirement.Absent,
        ),
        trustedEntities =
        EUTrustedEntitiesProfile(
            serviceTypeIdentifiers = ServiceTypeIdentifiers.IssuanceAndRevocation(
                issuance = ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE,
                revocation = ETSI19602.EU_PID_PROVIDERS_SVC_TYPE_REVOCATION,
            ),
            mustContainX509Certificates = true,
            serviceStatuses = emptySet(),
            chainValidationAlgorithm = ChainValidationAlgorithm.Direct,
            hasConstraints = object : CertificateConstraints {
                override fun <CERT : Any> CertificateOperations<CERT>.evaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    pidProviderCertificateConstraintsEvaluator()
            },
        ),
    )

/**
 * Creates constraints for PID Provider certificates (LoTE end-entity).
 *
 * Per ETSI TS 119 602 Annex D and ETSI TS 119 412-6:
 * - Certificate type: End-entity ONLY (cA=FALSE)
 * - QCStatement: id-etsi-qct-pid (0.4.0.194126.1.1) REQUIRED in the QCStatement extension
 * - Key Usage: digitalSignature REQUIRED
 * - Validity: Must be valid at validation time
 * - Certificate Policy: NOT mandated by ETSI TS 119 412-6 (TSP-defined)
 * - AIA: Required if CA-issued, not required if self-signed
 *
 * **Note on Certificate Policy OIDs:** ETSI TS 119 412-6 does NOT mandate specific certificate policy OIDs
 * for PID providers. The OIDs `id-etsi-qct-pid` and `id-etsi-qct-wal` are **QCStatement type OIDs** (QcType)
 * that MUST appear in the QCStatement extension, NOT in the certificatePolicies extension.
 * TSPs MAY define their own certificate policy OIDs, but this is not required by the specification.
 *
 * @return a validator configured for PID Provider certificates
 */
public fun <CERT : Any> CertificateOperations<CERT>.pidProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT> =
    EvaluateMultipleCertificateConstraints.of(
        EvaluateBasicConstraintsConstraint.requireEndEntity(::getBasicConstraints),
        QCStatementConstraint(
            requiredQcType = ETSI119412.ID_ETSI_QCT_PID,
            requireCompliance = true,
            ::getQcStatements,
        ),
        KeyUsageConstraint.requireDigitalSignature(::getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(::getValidityPeriod),
        // Note: ETSI TS 119 412-6 does not mandate specific certificate policies for PID providers
        // The id-etsi-qct-pid OID is a QCStatement type (QcType), not a certificate policy OID
        EvaluateAuthorityInformationAccessConstraint.requireForCaIssued(::isSelfSigned, ::getAiaExtension),
    )
