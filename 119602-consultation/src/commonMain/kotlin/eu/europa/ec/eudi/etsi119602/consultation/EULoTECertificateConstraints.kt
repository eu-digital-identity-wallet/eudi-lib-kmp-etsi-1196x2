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
package eu.europa.ec.eudi.etsi119602.consultation

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificatePolicyConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateBasicConstraintsConstraint
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.EvaluateMultipleCertificateConstraints
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
 */
public interface EULoTECertificateConstraints<CERT : Any> {

    /**
     * Creates a validator for PID Provider certificates (LoTE end-entity).
     *
     * Per ETSI TS 119 602 Annex D:
     * - End-entity certificate (cA=FALSE)
     * - QCStatement: id-etsi-qct-pid REQUIRED
     * - Key Usage: digitalSignature REQUIRED
     * - Certificate Policy: ETSI TS 119 412-6
     *
     * @return a validator configured for PID Provider certificates
     */
    public fun pidProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT>

    /**
     * Creates a validator for Wallet Provider certificates (LoTE end-entity).
     *
     * Per ETSI TS 119 602 Annex E:
     * - End-entity certificate (cA=FALSE)
     * - QCStatement: id-etsi-qct-wal REQUIRED
     * - Key Usage: digitalSignature REQUIRED
     * - Certificate Policy: ETSI TS 119 412-6
     *
     * @return a validator configured for Wallet Provider certificates
     */
    public fun walletProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT>

    /**
     * Creates a validator for WRPAC Provider certificates (LoTE CA).
     *
     * Per ETSI TS 119 602 Annex F:
     * - CA certificate (cA=TRUE)
     * - Key Usage: keyCertSign REQUIRED
     * - Certificate Policy: ETSI TS 119 411-8
     *
     * @return a validator configured for WRPAC Provider certificates
     */
    public fun wrpacProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT>

    /**
     * Creates a validator for WRPRC Provider certificates (LoTE CA).
     *
     * Per ETSI TS 119 602 Annex G:
     * - CA certificate (cA=TRUE)
     * - Key Usage: keyCertSign REQUIRED
     * - Certificate Policy: ETSI TS 119 411-8 (or equivalent)
     *
     * @return a validator configured for WRPRC Provider certificates
     *
     */
    public fun wrprcProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT>

    public companion object {

        public operator fun <CERT : Any> invoke(
            getBasicConstraints: suspend (CERT) -> BasicConstraintsInfo,
            getQcStatements: suspend (CERT) -> List<QCStatementInfo>,
            getKeyUsage: suspend (CERT) -> KeyUsageBits?,
            getValidityPeriod: suspend (CERT) -> ValidityPeriod,
            getCertificatePolicies: suspend (CERT) -> List<String>,
        ): EULoTECertificateConstraints<CERT> =

            object : EULoTECertificateConstraints<CERT> {
                override fun pidProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    pidProviderConstraints(getBasicConstraints, getQcStatements, getKeyUsage, getValidityPeriod, getCertificatePolicies)

                override fun walletProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    walletProviderConstraints(getBasicConstraints, getQcStatements, getKeyUsage, getValidityPeriod, getCertificatePolicies)

                override fun wrpacProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    wrpacProviderConstraints(getBasicConstraints, getKeyUsage, getValidityPeriod, getCertificatePolicies)

                override fun wrprcProviderCertificateConstraintsEvaluator(): EvaluateMultipleCertificateConstraints<CERT> =
                    wrprcProviderConstraints(getBasicConstraints, getKeyUsage, getValidityPeriod, getCertificatePolicies)
            }

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
        ): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints.of(
            EvaluateBasicConstraintsConstraint.requireEndEntity(getBasicConstraints),
            QCStatementConstraint.forPidProvider(getQcStatements),
            KeyUsageConstraint.requireDigitalSignature(getKeyUsage),
            ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
            CertificatePolicyConstraint.requirePolicy(OIDs.POLICY_PID_PROVIDER, getCertificatePolicies),
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
        ): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints.of(
            EvaluateBasicConstraintsConstraint.requireEndEntity(getBasicConstraints),
            QCStatementConstraint.forWalletProvider(getQcStatements),
            KeyUsageConstraint.requireDigitalSignature(getKeyUsage),
            ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
            CertificatePolicyConstraint.requirePolicy(OIDs.POLICY_WALLET_PROVIDER, getCertificatePolicies),
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
        ): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints.of(
            EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, getBasicConstraints),
            KeyUsageConstraint.requireKeyCertSign(getKeyUsage),
            ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
            CertificatePolicyConstraint.requirePolicy(OIDs.POLICY_WRPAC_PROVIDER, getCertificatePolicies),
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
        ): EvaluateMultipleCertificateConstraints<CERT> = EvaluateMultipleCertificateConstraints.of(
            EvaluateBasicConstraintsConstraint.requireCa(maxPathLen, getBasicConstraints),
            KeyUsageConstraint.requireKeyCertSign(getKeyUsage),
            ValidityPeriodConstraint.validateAtCurrentTime(getValidityPeriod),
            CertificatePolicyConstraint.requirePolicy(OIDs.POLICY_WRPRC_PROVIDER, getCertificatePolicies),
        )
    }

    /**
     * OID constants for LoTE certificate policies and QCStatements.
     */
    public object OIDs {
        /** QCStatement OID for PID Providers (ETSI TS 119 412-6) */
        public const val ID_ETSI_QCT_PID: String = "0.4.0.1949.1.1"

        /** QCStatement OID for Wallet Providers (ETSI TS 119 412-6) */
        public const val ID_ETSI_QCT_WAL: String = "0.4.0.1949.1.2"

        /** Certificate Policy OID for PID Provider certificates (ETSI TS 119 412-6) */
        public const val POLICY_PID_PROVIDER: String = "0.4.0.1949.1.1"

        /** Certificate Policy OID for Wallet Provider certificates (ETSI TS 119 412-6) */
        public const val POLICY_WALLET_PROVIDER: String = "0.4.0.1949.1.2"

        /** Certificate Policy OID for WRPAC Provider certificates (ETSI TS 119 411-8) */
        public const val POLICY_WRPAC_PROVIDER: String = "0.4.0.1949.2.1"

        /** Certificate Policy OID for WRPRC Provider certificates (ETSI TS 119 411-8) */
        public const val POLICY_WRPRC_PROVIDER: String = "0.4.0.1949.2.1"
    }
}
