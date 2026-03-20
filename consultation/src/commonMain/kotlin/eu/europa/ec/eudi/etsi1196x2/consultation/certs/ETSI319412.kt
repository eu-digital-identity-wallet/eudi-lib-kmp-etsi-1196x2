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
package eu.europa.ec.eudi.etsi1196x2.consultation.certs

/**
 * QCStatement OIDs per ETSI EN 319 412-5 V2.5.1.
 *
 * These OIDs identify the type of QCStatement in qualified certificates.
 *
 * Source: ETSI EN 319 412-5 V2.5.1 Clause 5.2 - QCStatement identifiers
 */
public object ETSI319412 {
    /**
     * QCStatement OID for QcCompliance.
     *
     * id-etsi-qcs-QcCompliance: Claims compliance with the eIDAS Regulation (EU) No 910/2014.
     *
     * ASN.1 declaration:
     * ```
     * id-etsi-qcs-QcCompliance OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   qcs(1862) id-etsi-qcs(1) qcCompliance(1) }
     * ```
     */
    public const val QC_COMPLIANCE: String = "0.4.0.1862.1.1"

    /**
     * QCStatement OID for QcSSCD.
     *
     * id-etsi-qcs-QcSSCD: Claims the private key resides in a Qualified Signature Creation Device (QSCD).
     *
     * ASN.1 declaration:
     * ```
     * id-etsi-qcs-QcSSCD OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   qcs(1862) id-etsi-qcs(1) qcSSCD(4) }
     * ```
     */
    public const val QC_SSCD: String = "0.4.0.1862.1.4"

    /**
     * QCStatement OID for QcType.
     *
     * id-etsi-qcs-QcType: Specifies the type of the certificate (e.g., for electronic signatures,
     * electronic seals, or website authentication).
     *
     * ASN.1 declaration:
     * ```
     * id-etsi-qcs-QcType OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   qcs(1862) id-etsi-qcs(1) qcType(6) }
     * ```
     */
    public const val QC_TYPE: String = "0.4.0.1862.1.6"
}
