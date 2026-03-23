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

import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412Part6
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.*
import kotlin.time.Instant

/**
 * PID Provider Signing/Sealing Certificate Profile
 * Per ETSI TS 119 412-6:
 */
public fun pidSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    // X.509 v3 required (for extensions)
    version3()
    endEntity()
    mandatoryQcStatement(qcType = ETSI119412Part6.ID_ETSI_QCT_PID, requireCompliance = true)
    keyUsageDigitalSignature()
    validAt(at)
    // Per EN 319 412-2 §4.3.3: certificatePolicies extension shall be present (TSP-defined OID)
    policyIsPresent()
    authorityInformationAccessIfCAIssued()

    // Serial number must be positive (RFC 5280)
    positiveSerialNumber()

    // Public key requirements (TS 119 312)
    publicKey(
        options = PublicKeyAlgorithmOptions.of(
            PublicKeyAlgorithmOptions.AlgorithmRequirement.RSA_2048,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.EC_256,
            PublicKeyAlgorithmOptions.AlgorithmRequirement.ECDSA_256,
        ),
    )
    // (TS 119 412-6, PID-4.2-01)
    issuerForPIDProvider()
    // (TS 119 412-6, PID-4.3-01, PID-4.3-02)
    subjectForPIDProvider()
}

/**
 * ETSI TS 119 412-6, PID-4.2-01
 */
internal fun ProfileBuilder.issuerForPIDProvider() {
    issuer { issuer -> validateIssuerLegalOrNaturalPerson(issuer) }
}
internal fun ProfileBuilder.subjectForPIDProvider() {
    issuer { issuer -> validateSubjectLegalOrNaturalPerson(issuer) }
}

internal fun validateIssuerLegalOrNaturalPerson(issuer: DistinguishedName?): CertificateConstraintEvaluation =
    if (issuer == null) {
        CertificateConstraintEvaluation(listOf(CertificateConstraintsEvaluations.missingIssuerDistinguishedName))
    } else {
        // organization identifier is required for legal persons
        val isLegalPerson = issuer[DistinguishedName.X500OIDs.ORG_IDENTIFIER] != null
        if (isLegalPerson) {
            CertificateConstraintsEvaluations.issuerLegalPersonAttributes(issuer)
        } else {
            CertificateConstraintsEvaluations.issuerNaturalPersonAttributes(issuer)
        }
    }

/**
 * ETSI TS 119 412-6, PID-4.3-01, PID-4.3-02
 */
internal fun validateSubjectLegalOrNaturalPerson(subject: DistinguishedName?): CertificateConstraintEvaluation =
    if (subject == null) {
        CertificateConstraintEvaluation(listOf(CertificateConstraintsEvaluations.missingSubjectDistinguishedName))
    } else {
        // organization identifier is required for legal persons
        val isLegalPerson = subject[DistinguishedName.X500OIDs.ORG_IDENTIFIER] != null
        if (isLegalPerson) {
            CertificateConstraintsEvaluations.subjectLegalPersonAttributes(subject)
        } else {
            CertificateConstraintsEvaluations.subjectNaturalPersonAttributes(subject)
        }
    }
