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
 * Constants from ETSI EN 319 412-1 - Certificate Policy Requirements.
 *
 * This object contains OID constants and format constants as defined in
 * ETSI EN 319 412-1 (Policy requirements for certification authorities issuing public key certificates).
 *
 * Includes:
 * - ETSI-specific extension OIDs (val-assured, qcp, ncp)
 * - Organization identifier format constants (EN 319 412-1 clause 5.1.4)
 *
 * @see [ETSI EN 319 412-1](https://www.etsi.org/deliver/etsi_en/319400_319499/31941201/)
 */
public object ETSI319412Part1 {

    /**
     * id-at-organizationIdentifier OBJECT IDENTIFIER ::= { joint-iso-itu-t(2) ds(5) attributeType(4) 97 }
     *
     * Used to carry the structured organization identifier (e.g., VATBE-0123456789).
     * Defined in X.520 and profiled in ETSI EN 319 412-1.
     */
    public const val ATTR_ORGANIZATION_IDENTIFIER: String = "2.5.4.97"

    //
    // Subject Attribute OIDs (Commonly used with ETSI profiles)
    //

    /** id-at-commonName */
    public const val ATTR_COMMON_NAME: String = "2.5.4.3"

    /** id-at-countryName */
    public const val ATTR_COUNTRY_NAME: String = "2.5.4.6"

    /** id-at-organizationName */
    public const val ATTR_ORGANIZATION_NAME: String = "2.5.4.10"

    //
    // Extension OIDs
    //

    /**
     * id-etsi-valassured-ST-certs - Short-term certificate with validity assurance.
     * Per EN 319 412-1 clause 5.2.
     */
    public const val EXT_ETSI_VAL_ASSURED_ST_CERTS: String = "0.4.0.194121.2.1"

    /** Base OID for ETSI Qualified Certificate Policy identifiers */
    public const val OID_ETSI_QCP_BASE: String = "0.4.0.194121.1"

    /** Base OID for ETSI Non-Qualified Certificate Policy identifiers */
    public const val OID_ETSI_NCP_BASE: String = "0.4.0.194121.1.1"

    //
    // Organization Identifier Constants (EN 319 412-1 Clause 5.1.4)
    //

    /** Legal person identity type reference: Value Added Tax identification */
    public const val ORG_ID_TYPE_VAT: String = "VAT"

    /** Legal person identity type reference: National Trade Register */
    public const val ORG_ID_TYPE_NTR: String = "NTR"

    /** Legal person identity type reference: Payment Service Directive */
    public const val ORG_ID_TYPE_PSD: String = "PSD"

    /** Legal person identity type reference: Legal Entity Identifier */
    public const val ORG_ID_TYPE_LEI: String = "LEI"

    /** Legal person identity type reference: Economic Operator Registration */
    public const val ORG_ID_TYPE_EOR: String = "EOR"

    /** Legal person identity type reference: Excise Number */
    public const val ORG_ID_TYPE_EXC: String = "EXC"

    /** Natural person identity type reference: Passport number */
    public const val NAT_ID_TYPE_PAS: String = "PAS"

    /** Natural person identity type reference: National identity card number */
    public const val NAT_ID_TYPE_IDC: String = "IDC"

    /** Natural person identity type reference: National personal number (civic registration number) */
    public const val NAT_ID_TYPE_PNO: String = "PNO"

    /** Natural person identity type reference: Personal tax reference number */
    public const val NAT_ID_TYPE_TAX: String = "TAX"

    /** Natural person identity type reference: Tax Identification Number (European Commission) */
    public const val NAT_ID_TYPE_TIN: String = "TIN"

    /** Separator character: hyphen-minus (ASCII 0x2D, UTF-8 U+002D) */
    public const val ORG_ID_SEPARATOR: Char = '-'

    /** Length of legal person identity type reference prefix */
    public const val ORG_ID_TYPE_PREFIX_LENGTH: Int = 3

    /** Length of ISO 3166-1 country code */
    public const val ORG_ID_COUNTRY_CODE_LENGTH: Int = 2

    /** Regex pattern for organizationIdentifier format validation */
    public val ORG_ID_PATTERN: Regex = Regex("^([A-Z]{3})[A-Z]{2}-.+$")

    /** Valid legal person identity type references (EN 319 412-1 clause 5.1.4) */
    public val VALID_ORG_ID_TYPES: Set<String> = setOf(
        ORG_ID_TYPE_VAT,
        ORG_ID_TYPE_NTR,
        ORG_ID_TYPE_PSD,
        ORG_ID_TYPE_LEI,
        ORG_ID_TYPE_EOR,
        ORG_ID_TYPE_EXC,
    )

    /** Valid natural person identity type references (EN 319 412-1 clause 5.1.3) */
    public val VALID_NAT_ID_TYPES: Set<String> = setOf(
        NAT_ID_TYPE_PAS,
        NAT_ID_TYPE_IDC,
        NAT_ID_TYPE_PNO,
        NAT_ID_TYPE_TAX,
        NAT_ID_TYPE_TIN,
    )
}
