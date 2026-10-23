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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ProfileBuilder
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.PublicKeyAlgorithmOptions
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.RFC5280
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.authorityInformationAccessIfCAIssued
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.certificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.endEntity
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.extensionCriticality
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.keyUsageOneOfETSI319412Types
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.mandatoryQcType
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.policyIsPresent
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.positiveSerialNumber
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.publicKey
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.subjectKeyIdentifier
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.validAt
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.version3
import kotlin.time.Instant

public fun walletProviderSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    endEntity()
    version3()
    mandatoryQcType(innerIdentifier = ETSI119412Part6.ID_ETSI_QCT_WAL)
    // TS 119 412-6, WAL-5.1-01, PID-4.4.1-01: One of Type : A, B, C, F
    // Same as PID Provider
    keyUsageOneOfETSI319412Types()
    walletProviderExplicitExtensionCriticality()
    validAt(at)
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
    // (TS 119 412-6, WAL-5.1-01, PID-4.2 and PID-4.3)
    // Same as PID Provider
    pidProviderIssuerAndSubject()

    // Subject Key Identifier required (TS 119 412-6,WAL-5.1-01, PID-4.4.2-01)
    // Same as PID Provider
    subjectKeyIdentifier()
}

internal fun ProfileBuilder.walletProviderExplicitExtensionCriticality() {
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
