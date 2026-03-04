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
package eu.europa.ec.eudi.etsi1196x2.consultation

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintValidator
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificatePolicyConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateBasicConstraintsConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriodConstraint

/**
 * Factory functions for creating LoTE-specific certificate constraints.
 *
 * This object provides factory methods for creating certificate constraint validators
 * tailored to each provider list type defined in ETSI TS 119 602:
 * - PID Providers (Annex D): End-entity certificates with id-etsi-qct-pid QCStatement
 * - Wallet Providers (Annex E): End-entity certificates with id-etsi-qct-wal QCStatement
 * - WRPAC Providers (Annex F): CA certificates issuing WRPAC to Relying Parties
 * - WRPRC Providers (Annex G): CA certificates signing WRPRC JWT attestations
 *
 * @see eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateConstraintValidator
 */
public object LoTECertificateConstraints {

    /**
     * Creates constraints for PID Provider certificates (LoTE end-entity).
     *
     * Per ETSI TS 119 602 Annex D:
     * - Certificate type: End-entity ONLY (cA=FALSE)
     * - QCStatement: id-etsi-qct-pid (0.4.0.1949.1.1) REQUIRED
     * - Key Usage: digitalSignature REQUIRED
     * - Validity: Must be valid at validation time
     * - Certificate Policy: ETSI TS 119 412-6
     *
     * @param getBasicConstraints function to extract basic constraints from a certificate
     * @param getQcStatements function to extract QCStatements from a certificate
     * @param getKeyUsage function to extract key usage from a certificate
     * @param getValidityPeriod function to extract validity period from a certificate
     * @param getCertificatePolicies function to extract certificate policies from a certificate
     *
     * @return a validator configured for PID Provider certificates
     */
    public fun <CERT : Any> pidProviderConstraints(
        getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        getCertificatePolicies: suspend (CERT) -> List<String>,
    ): CertificateConstraintValidator<CERT> = CertificateConstraintValidator.of(
        EvaluateBasicConstraintsConstraint.requireEndEntity(getBasicConstraints),
        QCStatementConstraint.forPidProvider(getQcStatements),
        KeyUsageConstraint.requireDigitalSignature(getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
        CertificatePolicyConstraint.requirePolicy("0.4.0.1949.1.1", getCertificatePolicies),
    )

    /**
     * Creates constraints for Wallet Provider certificates (LoTE end-entity).
     *
     * Per ETSI TS 119 602 Annex E:
     * - Certificate type: End-entity ONLY (cA=FALSE)
     * - QCStatement: id-etsi-qct-wal (0.4.0.1949.1.2) REQUIRED
     * - Key Usage: digitalSignature REQUIRED
     * - Validity: Must be valid at validation time
     * - Certificate Policy: ETSI TS 119 412-6
     *
     * @param getBasicConstraints function to extract basic constraints from a certificate
     * @param getQcStatements function to extract QCStatements from a certificate
     * @param getKeyUsage function to extract key usage from a certificate
     * @param getValidityPeriod function to extract validity period from a certificate
     * @param getCertificatePolicies function to extract certificate policies from a certificate
     *
     * @return a validator configured for Wallet Provider certificates
     */
    public fun <CERT : Any> walletProviderConstraints(
        getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
        getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        getCertificatePolicies: suspend (CERT) -> List<String>,
    ): CertificateConstraintValidator<CERT> = CertificateConstraintValidator.of(
        EvaluateBasicConstraintsConstraint.requireEndEntity(getBasicConstraints),
        QCStatementConstraint.forWalletProvider(getQcStatements),
        KeyUsageConstraint.requireDigitalSignature(getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
        CertificatePolicyConstraint.requirePolicy("0.4.0.1949.1.2", getCertificatePolicies),
    )

    /**
     * Creates constraints for WRPAC Provider certificates (LoTE CA).
     *
     * Per ETSI TS 119 602 Annex F:
     * - Certificate type: CA certificate (cA=TRUE)
     * - QCStatement: NOT required
     * - Key Usage: keyCertSign REQUIRED
     * - Validity: Must be valid at validation time
     * - Certificate Policy: ETSI TS 119 411-8 (Access Certificate Policy)
     *
     * Note: WRPAC Providers are CAs that issue WRPAC (end-entity) certificates to Wallet Relying Parties.
     * The LoTE contains the WRPAC Provider's CA certificate, not the WRPAC itself.
     *
     * @param getBasicConstraints function to extract basic constraints from a certificate
     * @param getKeyUsage function to extract key usage from a certificate
     * @param getValidityPeriod function to extract validity period from a certificate
     * @param getCertificatePolicies function to extract certificate policies from a certificate
     * @param maxPathLen optional maximum path length constraint (default: null, no constraint)
     *
     * @return a validator configured for WRPAC Provider certificates
     */
    public fun <CERT : Any> wrpacProviderConstraints(
        getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        getCertificatePolicies: suspend (CERT) -> List<String>,
        maxPathLen: Int? = null,
    ): CertificateConstraintValidator<CERT> = CertificateConstraintValidator.of(
        EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, getBasicConstraints),
        KeyUsageConstraint.requireKeyCertSign(getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
        CertificatePolicyConstraint.requirePolicy("0.4.0.1949.2.1", getCertificatePolicies),
    )

    /**
     * Creates constraints for WRPRC Provider certificates (LoTE CA).
     *
     * Per ETSI TS 119 602 Annex G:
     * - Certificate type: CA certificate (cA=TRUE)
     * - QCStatement: NOT required
     * - Key Usage: keyCertSign REQUIRED
     * - Validity: Must be valid at validation time
     * - Certificate Policy: ETSI TS 119 411-8 (or equivalent)
     *
     * Note: WRPRC Providers are CAs that sign WRPRC JWT attestations.
     * The LoTE contains the WRPRC Provider's CA certificate.
     * WRPRC validation involves both PKIX (certificate chain) and JWT signature verification.
     *
     * @param getBasicConstraints function to extract basic constraints from a certificate
     * @param getKeyUsage function to extract key usage from a certificate
     * @param getValidityPeriod function to extract validity period from a certificate
     * @param getCertificatePolicies function to extract certificate policies from a certificate
     * @param maxPathLen optional maximum path length constraint (default: null, no constraint)
     *
     * @return a validator configured for WRPRC Provider certificates
     */
    public fun <CERT : Any> wrprcProviderConstraints(
        getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
        getKeyUsage: suspend (CERT) -> KeyUsageBits?,
        getValidityPeriod: suspend (CERT) -> ValidityPeriod,
        getCertificatePolicies: suspend (CERT) -> List<String>,
        maxPathLen: Int? = null,
    ): CertificateConstraintValidator<CERT> = CertificateConstraintValidator.of(
        EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, getBasicConstraints),
        KeyUsageConstraint.requireKeyCertSign(getKeyUsage),
        ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
        CertificatePolicyConstraint.requirePolicy("0.4.0.1949.2.1", getCertificatePolicies),
    )

    /**
     * OID constants for LoTE certificate policies and QCStatements.
     */
    public object OIDs {
        // QCStatement OIDs (ETSI EN 319 412-5)
        public const val ID_ETSI_QCT_PID: String = "0.4.0.1949.1.1"
        public const val ID_ETSI_QCT_WAL: String = "0.4.0.1949.1.2"

        // Certificate Policy OIDs
        public const val ETSI_TS_119_412_6_PID: String = "0.4.0.1949.1.1"
        public const val ETSI_TS_119_412_6_WAL: String = "0.4.0.1949.1.2"
        public const val ETSI_TS_119_411_8: String = "0.4.0.1949.2.1"
    }
}
