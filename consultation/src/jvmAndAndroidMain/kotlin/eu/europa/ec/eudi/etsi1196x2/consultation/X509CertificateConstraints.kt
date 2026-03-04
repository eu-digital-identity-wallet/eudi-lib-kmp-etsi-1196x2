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
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import java.security.cert.X509Certificate
import kotlin.time.toKotlinInstant

/**
 * JVM/Android implementations of certificate constraint extractors for [X509Certificate].
 *
 * This object provides platform-specific functions to extract certificate information
 * required by the constraint validators defined in commonMain.
 */
public object X509CertificateConstraintExtractors {

    /**
     * Extracts basic constraints information from an X509Certificate.
     *
     * @param cert the certificate to extract information from
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.BasicConstraintsInfo] with isCa and pathLenConstraint
     */
    public val getBasicConstraints: suspend (X509Certificate) -> BasicConstraintsInfo = { cert ->
        withContext(Dispatchers.IO) {
            val basicConstraints = cert.basicConstraints
            BasicConstraintsInfo(
                isCa = basicConstraints >= 0,
                pathLenConstraint = basicConstraints.takeIf { it >= 0 },
            )
        }
    }

    /**
     * Extracts QCStatement information from an X509Certificate.
     *
     * QCStatements are encoded in the certificate extension with OID 1.3.6.1.5.5.7.1.3.
     * This function parses the DER-encoded extension value to extract QC type OIDs.
     *
     * @param cert the certificate to extract information from
     * @return list of [eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo] or empty list if no QCStatements present
     */
    public val getQcStatements: suspend (X509Certificate) -> List<QCStatementInfo> = { cert ->
        withContext(Dispatchers.IO) {
            // OID for QCStatements extension (id-pe-qcStatements)
            val qcStatementsExtension = cert.getExtensionValue("1.3.6.1.5.5.7.1.3")
            qcStatementsExtension?.parseQcStatements().orEmpty()
        }
    }

    /**
     * Extracts key usage information from an X509Certificate.
     *
     * @param cert the certificate to extract information from
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.KeyUsageBits] or null if keyUsage extension is not present
     */
    public val getKeyUsage: suspend (X509Certificate) -> KeyUsageBits? = { cert ->
        withContext(Dispatchers.IO) {
            cert.keyUsage?.let { keyUsage ->
                KeyUsageBits(
                    digitalSignature = keyUsage.getOrElse(0) { false },
                    nonRepudiation = keyUsage.getOrElse(1) { false },
                    keyEncipherment = keyUsage.getOrElse(2) { false },
                    dataEncipherment = keyUsage.getOrElse(3) { false },
                    keyAgreement = keyUsage.getOrElse(4) { false },
                    keyCertSign = keyUsage.getOrElse(5) { false },
                    crlSign = keyUsage.getOrElse(6) { false },
                    encipherOnly = keyUsage.getOrElse(7) { false },
                    decipherOnly = keyUsage.getOrElse(8) { false },
                )
            }
        }
    }

    /**
     * Extracts validity period information from an X509Certificate.
     *
     * @param cert the certificate to extract information from
     * @return [eu.europa.ec.eudi.etsi1196x2.consultation.certs.ValidityPeriod] with notBefore and notAfter timestamps
     */
    public val getValidityPeriod: suspend (X509Certificate) -> ValidityPeriod = { cert ->
        withContext(Dispatchers.IO) {
            ValidityPeriod(
                notBefore = cert.notBefore.toInstant().toKotlinInstant(),
                notAfter = cert.notAfter.toInstant().toKotlinInstant(),
            )
        }
    }

    /**
     * Extracts certificate policy OIDs from an X509Certificate.
     *
     * Certificate policies are encoded in the certificate extension with OID 2.5.29.32.
     * This function parses the DER-encoded extension value to extract policy OIDs.
     *
     * @param cert the certificate to extract information from
     * @return list of certificate policy OIDs or empty list if no policies present
     */
    public val getCertificatePolicies: suspend (X509Certificate) -> List<String> = { cert ->
        withContext(Dispatchers.IO) {
            // OID for Certificate Policies extension
            val certPoliciesExtension = cert.getExtensionValue("2.5.29.32")
            certPoliciesExtension?.parseCertificatePolicies().orEmpty()
        }
    }

    /**
     * Helper function to parse QCStatements from DER-encoded extension value.
     *
     * The QCStatements extension has the following ASN.1 structure (ETSI EN 319 412-5):
     * ```
     * QCStatements ::= SEQUENCE OF QCStatement
     * QCStatement ::= SEQUENCE {
     *   statementId   OBJECT IDENTIFIER,
     *   statementInfo ANY DEFINED BY statementId OPTIONAL
     * }
     * ```
     *
     * The extension value is wrapped in an OCTET STRING, so we need to unwrap it first.
     *
     * @param derValue the DER-encoded extension value
     * @return list of [QCStatementInfo] or empty list if parsing fails
     *
     * @see [ETSI EN 319 412-5](https://www.etsi.org/deliver/etsi_en/319400_319499/31941205/)
     */
    private fun ByteArray.parseQcStatements(): List<QCStatementInfo> = try {
        ASN1InputStream(this).use { asn1InputStream ->
            val obj = asn1InputStream.readObject()
            // The extension value is wrapped in an OCTET STRING
            val octetString = obj as? DEROctetString ?: return emptyList()
            val qcStatements = ASN1Sequence.getInstance(octetString.octets)

            qcStatements.mapNotNull { qcStatementObj ->
                val qcStatement = qcStatementObj as? ASN1Sequence ?: return@mapNotNull null
                if (qcStatement.size() < 1) return@mapNotNull null

                // First element is the statementId (OID)
                val statementIdObj = qcStatement.getObjectAt(0) as? ASN1ObjectIdentifier ?: return@mapNotNull null
                val statementId = statementIdObj.id

                // Second element (optional) is statementInfo - we check for QC compliance
                // Per ETSI EN 319 412-5, QCStatements can have a compliance bit
                // For simplicity, we assume compliant if present
                val qcCompliance = qcStatement.size() > 1

                QCStatementInfo(
                    qcType = statementId,
                    qcCompliance = qcCompliance,
                )
            }
        }
    } catch (e: Exception) {
        // Log error if needed
        emptyList()
    }

    /**
     * Helper function to parse Certificate Policies from DER-encoded extension value.
     *
     * The Certificate Policies extension has the following ASN.1 structure (RFC 5280):
     * ```
     * CertificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
     * PolicyInformation ::= SEQUENCE {
     *   policyIdentifier   CertPolicyId,
     *   policyQualifiers   SEQUENCE SIZE (1..MAX) OF PolicyQualifierInfo OPTIONAL
     * }
     * CertPolicyId ::= OBJECT IDENTIFIER
     * ```
     *
     * The extension value is wrapped in an OCTET STRING, so we need to unwrap it first.
     *
     * @param derValue the DER-encoded extension value
     * @return list of policy OIDs or empty list if parsing fails
     *
     * @see [RFC 5280 Section 4.2.1.4](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.4)
     */
    private fun ByteArray.parseCertificatePolicies(): List<String> = try {
        ASN1InputStream(this).use { asn1InputStream ->
            val obj = asn1InputStream.readObject()
            // The extension value is wrapped in an OCTET STRING
            val octetString = obj as? DEROctetString ?: return emptyList()
            val certPolicies = ASN1Sequence.getInstance(octetString.octets)

            certPolicies.mapNotNull { policyInfoObj ->
                val policyInfo = policyInfoObj as? ASN1Sequence ?: return@mapNotNull null
                if (policyInfo.size() < 1) return@mapNotNull null

                // First element is the policyIdentifier (OID)
                val policyIdObj = policyInfo.getObjectAt(0) as? ASN1ObjectIdentifier ?: return@mapNotNull null
                policyIdObj.id
            }
        }
    } catch (e: Exception) {
        // Log error if needed
        emptyList()
    }
}

/**
 * Helper functions for creating LoTE-specific validators for [X509Certificate].
 *
 * These validators are configured according to the requirements specified in
 * ETSI TS 119 602 for each provider list type.
 *
 * @see [LoTE-Certificate-Validation.md](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2-consultation/blob/main/docs/LoTE-Certificate-Validation.md)
 */
public object LoTEX509CertificateValidators {

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
     *
     * @see [LoTE-Certificate-Validation.md Section 1.2](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2-consultation/blob/main/docs/LoTE-Certificate-Validation.md#12-lote-servicedigitalidentity-requirements-etsi-ts-119-602-annex-d)
     */
    public fun pidProviderCertificateConstraintsEvaluator(): CertificateConstraintValidator<X509Certificate> =
        LoTECertificateConstraints.pidProviderConstraints(
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
            getQcStatements = X509CertificateConstraintExtractors.getQcStatements,
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
            getValidityPeriod = X509CertificateConstraintExtractors.getValidityPeriod,
            getCertificatePolicies = X509CertificateConstraintExtractors.getCertificatePolicies,
        )

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
     *
     * @see [LoTE-Certificate-Validation.md Section 2.2](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2-consultation/blob/main/docs/LoTE-Certificate-Validation.md#22-lote-servicedigitalidentity-requirements-etsi-ts-119-602-annex-e)
     */
    public fun walletProviderCertificateConstraintsEvaluator(): CertificateConstraintValidator<X509Certificate> =
        LoTECertificateConstraints.walletProviderConstraints(
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
            getQcStatements = X509CertificateConstraintExtractors.getQcStatements,
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
            getValidityPeriod = X509CertificateConstraintExtractors.getValidityPeriod,
            getCertificatePolicies = X509CertificateConstraintExtractors.getCertificatePolicies,
        )

    /**
     * Creates a validator for WRPAC Provider certificates (LoTE CA).
     *
     * Per ETSI TS 119 602 Annex F:
     * - CA certificate (cA=TRUE)
     * - Key Usage: keyCertSign REQUIRED
     * - Certificate Policy: ETSI TS 119 411-8
     *
     * @return a validator configured for WRPAC Provider certificates
     *
     * @see [LoTE-Certificate-Validation.md Section 3.2](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2-consultation/blob/main/docs/LoTE-Certificate-Validation.md#32-lote-servicedigitalidentity-requirements-etsi-ts-119-602-annex-f)
     */
    public fun wrpacProviderCertificateConstraintsEvaluator(): CertificateConstraintValidator<X509Certificate> =
        LoTECertificateConstraints.wrpacProviderConstraints(
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
            getValidityPeriod = X509CertificateConstraintExtractors.getValidityPeriod,
            getCertificatePolicies = X509CertificateConstraintExtractors.getCertificatePolicies,
        )

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
     * @see [LoTE-Certificate-Validation.md Section 4.2](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2-consultation/blob/main/docs/LoTE-Certificate-Validation.md#42-lote-servicedigitalidentity-requirements-etsi-ts-119-602-annex-g)
     */
    public fun wrprcProviderCertificateConstraintsEvaluator(): CertificateConstraintValidator<X509Certificate> =
        LoTECertificateConstraints.wrprcProviderConstraints(
            getBasicConstraints = X509CertificateConstraintExtractors.getBasicConstraints,
            getKeyUsage = X509CertificateConstraintExtractors.getKeyUsage,
            getValidityPeriod = X509CertificateConstraintExtractors.getValidityPeriod,
            getCertificatePolicies = X509CertificateConstraintExtractors.getCertificatePolicies,
        )
}
