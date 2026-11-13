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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintsEvaluations
import kotlin.time.Instant

/**
 * PID Provider Signing/Sealing Certificate Profile
 * Per ETSI TS 119 412-6:
 */
public fun pidSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    // X.509 v3 required (for extensions)
    version3()
    endEntity()
    mandatoryQcType(innerIdentifier = ETSI119412Part6.ID_ETSI_QCT_PID)
    // TS 119 412-6, PID-4.4.1-01:  One of Type : A, B, C, F
    keyUsageOneOfETSI319412Types()
    pidProviderExplicitExtensionCriticality()

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
    pidProviderIssuerAndSubject()

    // Subject Key Identifier required (TS 119 412-6, PID-4.4.2-01)
    subjectKeyIdentifier()
}

/**
 * ETSI TS 119 412-6, PID-4.1-02
 */
internal fun ProfileBuilder.pidProviderExplicitExtensionCriticality() {
    @Suppress("DuplicatedCode")
    fun rfc5280AllowsEitherCriticality(oid: String) = oid == RFC5280.EXT_BASIC_CONSTRAINTS ||
        oid == RFC5280.EXT_KEY_USAGE ||
        oid == RFC5280.EXT_EXTENDED_KEY_USAGE ||
        oid == RFC5280.EXT_SUBJECT_ALT_NAME ||
        oid == RFC5280.EXT_CERTIFICATE_POLICIES
    // basicConstraints, keyUsage, extendedKeyUsage, subjectAltName and certificatePolicies may be critical or
    // non-critical in an end-entity certificate: RFC 5280 recommends a critical keyUsage (§4.2.1.3), and allows
    // either criticality for basicConstraints (§4.2.1.9), extendedKeyUsage (§4.2.1.12), subjectAltName (§4.2.1.6)
    // and certificatePolicies (§4.2.1.4).
    extensionCriticality(mustBeCritical = false) { oid ->
        !rfc5280AllowsEitherCriticality(oid)
    }
}

/**
 * ETSI TS 119 412-6, PID-4.2-01
 */
internal fun ProfileBuilder.pidProviderIssuerAndSubject() {
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
            validateSubjectLegalOrNaturalPerson(subject)
        } else {
            fun CertificateConstraintEvaluation.vs() = when (this) {
                CertificateConstraintEvaluation.Met -> emptyList()
                is CertificateConstraintEvaluation.Violated -> violations
            }
            CertificateConstraintEvaluation {
                addAll(validateIssuerLegalOrNaturalPerson(issuer).vs())
                addAll(validateSubjectLegalOrNaturalPerson(subject).vs())
            }
        }
    }
}

/**
 * ETSI TS 119 412-6, PID-4.2-01
 */
internal fun validateIssuerLegalOrNaturalPerson(dn: DistinguishedName?): CertificateConstraintEvaluation {
    val attribute = "Issuer"
    return if (dn == null) {
        CertificateConstraintEvaluation(listOf(CertificateConstraintsEvaluations.missingDN(attribute)))
    } else {
        // organization is required for legal persons
        val isLegalPerson = dn[DistinguishedName.X500OIDs.ORGANIZATION] != null
        if (isLegalPerson) {
            // organizationIdentifier is conditionally mandatory for issuer DNs
            // (EN 319 412-2 GEN-4.2.3.1-3) and therefore not enforced here.
            CertificateConstraintsEvaluations.legalPersonIssuerDN(dn)
        } else {
            CertificateConstraintsEvaluations.naturalPersonDN(attribute, dn)
        }
    }
}

/**
 * ETSI TS 119 412-6, PID-4.3-01, PID-4.3-02
 */
internal fun validateSubjectLegalOrNaturalPerson(dn: DistinguishedName?): CertificateConstraintEvaluation {
    val attribute = "Subject"
    return if (dn == null) {
        CertificateConstraintEvaluation(listOf(CertificateConstraintsEvaluations.missingDN(attribute)))
    } else {
        // organization is required for legal persons
        val isLegalPerson = dn[DistinguishedName.X500OIDs.ORGANIZATION] != null
        if (isLegalPerson) {
            // organizationIdentifier is unconditionally mandatory for subject DNs
            // (EN 319 412-3 LEG-4.2.1-2).
            CertificateConstraintsEvaluations.legalPersonSubjectDN(dn)
        } else {
            CertificateConstraintsEvaluations.naturalPersonDN(attribute, dn)
        }
    }
}
