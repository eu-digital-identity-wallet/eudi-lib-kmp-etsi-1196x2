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

/**
 * Source: ETSI TS 119 411-8 V1.1.1
 */
public object ETSI119411Part8 {

    /**
     * Certificate Policy OID for WRPAC issued to natural persons (NCP).
     *
     * NCP-n-eudiwrp: Normalized certificate policy for wallet-relying party access certificates
     * issued to natural persons.
     *
     * ASN.1 declaration:
     * ```
     * ncp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) ncp-natural(1) }
     * ```
     */
    public const val NCP_N_EUDIWRP: String = "0.4.0.194118.1.1"

    /**
     * Certificate Policy OID for WRPAC issued to legal persons (NCP).
     *
     * NCP-l-eudiwrp: Normalized certificate policy for wallet-relying party access certificates
     * issued to legal persons.
     *
     * ASN.1 declaration:
     * ```
     * ncp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) ncp-legal(2) }
     * ```
     */
    public const val NCP_L_EUDIWRP: String = "0.4.0.194118.1.2"

    /**
     * Certificate Policy OID for WRPAC issued to natural persons (QCP).
     *
     * QCP-n-eudiwrp: Qualified certificate policy for wallet-relying party access certificates
     * issued to natural persons.
     *
     * ASN.1 declaration:
     * ```
     * qcp-n-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) qcp-natural(3) }
     * ```
     */
    public const val QCP_N_EUDIWRP: String = "0.4.0.194118.1.3"

    /**
     * Certificate Policy OID for WRPAC issued to legal persons (QCP).
     *
     * QCP-l-eudiwrp: Qualified certificate policy for wallet-relying party access certificates
     * issued to legal persons.
     *
     * ASN.1 declaration:
     * ```
     * qcp-l-eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrp(194118) policy-identifiers(1) qcp-legal(4) }
     * ```
     */
    public const val QCP_L_EUDIWRP: String = "0.4.0.194118.1.4"

    //
    // Supplementary OIDs from ETSI TS 119 411-8
    //

    /**
     * Base OID for EUDI WRPAC-related identifiers.
     *
     * eudiwrp OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0) 194118 }
     */
    public const val OID_EUDIWRP_BASE: String = "0.4.0.194118"

    /**
     * Base OID for WRPAC policy identifiers.
     *
     * policy-identifiers OBJECT IDENTIFIER ::= { eudiwrp 1 }
     */
    public const val OID_POLICY_IDENTIFIERS_BASE: String = "0.4.0.194118.1"

    /**
     * OID arc for WRPAC extension identifiers (reserved for future use).
     *
     * extensions OBJECT IDENTIFIER ::= { eudiwrp 2 }
     */
    public const val OID_EXTENSIONS_BASE: String = "0.4.0.194118.2"

    /**
     * OID arc for WRPAC attribute identifiers (reserved for future use).
     *
     * attributes OBJECT IDENTIFIER ::= { eudiwrp 3 }
     */
    public const val OID_ATTRIBUTES_BASE: String = "0.4.0.194118.3"
}
