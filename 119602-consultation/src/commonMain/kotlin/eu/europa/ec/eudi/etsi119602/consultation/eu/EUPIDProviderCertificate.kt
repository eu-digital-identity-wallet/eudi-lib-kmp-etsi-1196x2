package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.consultation.ETSI119412Part6
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.authorityInformationAccessIfCAIssued
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.certificateProfile
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.endEntity
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.keyUsageDigitalSignature
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.mandatoryQcStatement
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.policyIsPresent
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.validAt
import kotlin.time.Instant


/**
 * PID Provider Signing/Sealing Certificate Profile
 * Per ETSI TS 119 412-6:
 */
public fun pidSigningCertificateProfile(at: Instant? = null): CertificateProfile = certificateProfile {
    endEntity()
    mandatoryQcStatement(qcType = ETSI119412Part6.ID_ETSI_QCT_PID, requireCompliance = true)
    keyUsageDigitalSignature()
    validAt(at)
    // Per EN 319 412-2 §4.3.3: certificatePolicies extension shall be present (TSP-defined OID)
    policyIsPresent()
    authorityInformationAccessIfCAIssued()
}