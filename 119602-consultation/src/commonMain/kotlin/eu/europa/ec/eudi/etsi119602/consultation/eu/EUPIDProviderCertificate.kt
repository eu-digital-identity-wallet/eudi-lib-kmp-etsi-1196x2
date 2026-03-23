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
    // (TS 119 412-6, PID-4.3-01, PID-4.3-02)
    issuerAndSubjectForPIDProvider()

    // Subject Key Identifier required (TS 119 412-6, PID-4.4.2-01)
    subjectKeyIdentifier()
}

/**
 * ETSI TS 119 412-6, PID-4.2-01
 */
internal fun ProfileBuilder.issuerAndSubjectForPIDProvider() {
    combine(
        CertificateOperationsAlgebra.CheckSelfSigned,
        CertificateOperationsAlgebra.GetIssuer,
        CertificateOperationsAlgebra.GetSubject,
    ) { (isSelfSigned, issuer, subject) ->

        val issuerAndSubjectPresent = CertificateConstraintEvaluation {
            if (issuer == null) {
                add(CertificateConstraintsEvaluations.missingDN("Issuer"))
            }
            if (subject == null) {
                add(CertificateConstraintsEvaluations.missingDN("Subject"))
            }
        }
        if (!issuerAndSubjectPresent.isMet()) {
            return@combine issuerAndSubjectPresent
        }

        checkNotNull(issuer) { "Cannot happen" }
        checkNotNull(subject) { "Cannot happen" }

        if (isSelfSigned) {
            check(issuer == subject) { "Self-signed certificate must have the same issuer and subject" }
            validateLegalOrNaturalPerson("Subject", subject)
        } else {
            fun CertificateConstraintEvaluation.vs() = when (this) {
                CertificateConstraintEvaluation.Met -> emptyList()
                is CertificateConstraintEvaluation.Violated -> violations
            }
            CertificateConstraintEvaluation {
                addAll(validateLegalOrNaturalPerson("Issuer", issuer).vs())
                addAll(validateLegalOrNaturalPerson("Subject", subject).vs())
            }
        }
    }
}

/**
 * ETSI TS 119 412-6, PID-4.3-01, PID-4.3-02
 */
internal fun validateLegalOrNaturalPerson(attribute: String, dn: DistinguishedName?): CertificateConstraintEvaluation =
    if (dn == null) {
        CertificateConstraintEvaluation(listOf(CertificateConstraintsEvaluations.missingDN(attribute)))
    } else {
        // organization identifier is required for legal persons
        val isLegalPerson = dn[DistinguishedName.X500OIDs.ORG_IDENTIFIER] != null
        if (isLegalPerson) {
            CertificateConstraintsEvaluations.legalPersonDN(attribute, dn)
        } else {
            CertificateConstraintsEvaluations.naturalPersonDN(attribute, dn)
        }
    }
