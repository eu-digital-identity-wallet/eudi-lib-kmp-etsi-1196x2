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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.keyUsageDigitalSignature
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.mandatoryQcStatement
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.policyIsPresent
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.positiveSerialNumber
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.publicKey
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.subjectKeyIdentifier
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.validAt
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.version3
import kotlin.time.Instant

public fun walletProviderSigningCertificateProfile(at: Instant? = null): CertificateProfile =
    certificateProfile {
        endEntity()
        version3()
        mandatoryQcStatement(qcType = ETSI119412Part6.ID_ETSI_QCT_WAL, requireCompliance = true)
        keyUsageDigitalSignature()
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
        // Subject Key Identifier required (TS 119 412-6,WAL-5.1-01, PID-4.4.2-01)
        subjectKeyIdentifier()
    }

internal fun ProfileBuilder.walletProviderExplicitExtensionCriticality() {
    fun basicConstraintOrKeyUsage(oid: String) =
        oid == RFC5280.EXT_BASIC_CONSTRAINTS || oid == RFC5280.EXT_KEY_USAGE
    extensionCriticality(mustBeCritical = true) { oid ->
        basicConstraintOrKeyUsage(oid)
    }
    extensionCriticality(mustBeCritical = false) { oid ->
        !basicConstraintOrKeyUsage(oid)
    }
}
