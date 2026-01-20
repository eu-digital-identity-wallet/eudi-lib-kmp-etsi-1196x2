/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi119602

/**
 * [ETSI 119 602](https://www.etsi.org/deliver/etsi_ts/119600_119699/119602/01.01.01_60/ts_119602v010101p.pdf)
 * [JSON Schema](https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities/-/blob/main/1960201_json_schema/1960201_json_schema.json?ref_type=heads)
 */
public object ETSI19602 {
    public const val VERSION: String = "1.1.1"

    public const val LOTE_VERSION: Int = 1
    public const val INITIAL_SEQUENCE_NUMBER: Int = 1
    public const val COUNTRY_CODE_EU: String = "EU"
    public const val COUNTRY_CODE_SIZE: Int = 2

    public const val LOTE: String = "LoTE"
    public const val LIST_AND_SCHEME_INFORMATION: String = "ListAndSchemeInformation"
    public const val LOTE_VERSION_IDENTIFIER: String = "LoTEVersionIdentifier"
    public const val LOTE_SEQUENCE_NUMBER: String = "LoTESequenceNumber"
    public const val LOTE_TYPE: String = "LoTEType"
    public const val SCHEME_OPERATOR_NAME: String = "SchemeOperatorName"
    public const val SCHEME_OPERATOR_ADDRESS: String = "SchemeOperatorAddress"
    public const val SCHEME_OPERATOR_POSTAL_ADDRESS: String = "SchemeOperatorPostalAddress"
    public const val SCHEME_OPERATOR_ELECTRONIC_ADDRESS: String = "SchemeOperatorElectronicAddress"
    public const val SCHEME_NAME: String = "SchemeName"

    public const val SCHEME_INFORMATION_URI: String = "SchemeInformationURI"
    public const val STATUS_DETERMINATION_APPROACH: String = "StatusDeterminationApproach"
    public const val SCHEME_TYPE_COMMUNITY_RULES: String = "SchemeTypeCommunityRules"
    public const val SCHEME_TERRITORY: String = "SchemeTerritory"
    public const val POLICY_OR_LEGAL_NOTICE: String = "PolicyOrLegalNotice"
    public const val HISTORICAL_INFORMATION_PERIOD: String = "HistoricalInformationPeriod"
    public const val POINTER_TO_OTHER_LOTE: String = "PointerToOtherLoTE"
    public const val LIST_ISSUE_DATE_TIME: String = "ListIssueDateTime"
    public const val NEXT_UPDATE: String = "NextUpdate"
    public const val DISTRIBUTION_POINTS: String = "DistributionPoints"
    public const val SCHEME_EXTENSIONS: String = "SchemeExtensions"

    public const val LANG: String = "lang"
    public const val STRING_VALUE: String = "value"
    public const val URI_VALUE: String = "uriValue"
    public const val MIME_TYPE: String = "MimeType"

    public const val POSTAL_ADDRESS_STREET_ADDRESS: String = "StreetAddress"
    public const val POSTAL_ADDRESS_LOCALITY: String = "Locality"
    public const val POSTAL_ADDRESS_STATE_OR_PROVINCE: String = "StateOrProvince"
    public const val POSTAL_ADDRESS_POSTAL_CODE: String = "PostalCode"
    public const val POSTAL_ADDRESS_COUNTRY: String = "Country"

    public const val LOTE_POLICY: String = "LoTEPolicy"
    public const val LOTE_LEGAL_NOTICE: String = "LoTELegalNotice"

    public const val LOTE_LOCATION: String = "LoTELocation"
    public const val LOTE_QUALIFIERS: String = "LoTEQualifiers"

    public const val SERVICE_DIGITAL_IDENTITIES: String = "ServiceDigitalIdentities"

    public const val TRUSTED_ENTITIES_LIST: String = "TrustedEntitiesList"
    public const val TRUSTED_ENTITY_INFORMATION: String = "TrustedEntityInformation"
    public const val TE_NAME: String = "TEName"
    public const val TE_TRADE_NAME: String = "TETradeName"
    public const val TE_ADDRESS: String = "TEAddress"
    public const val TE_INFORMATION_URI: String = "TEInformationURI"
    public const val TE_POSTAL_ADDRESS: String = "TEPostalAddress"
    public const val TE_ELECTRONIC_ADDRESS: String = "TEElectronicAddress"
    public const val TRUSTED_ENTITY_SERVICES: String = "TrustedEntityServices"

    public const val SERVICE_INFORMATION: String = "ServiceInformation"
    public const val SERVICE_HISTORY: String = "ServiceHistory"
    public const val SERVICE_NAME: String = "ServiceName"
    public const val SERVICE_DIGITAL_IDENTITY: String = "ServiceDigitalIdentity"
    public const val SERVICE_TYPE_IDENTIFIER: String = "ServiceTypeIdentifier"
    public const val SERVICE_STATUS: String = "ServiceStatus"
    public const val STATUS_STARTING_TIME: String = "StatusStartingTime"
    public const val SCHEME_SERVICE_DEFINITION_URI: String = "SchemeServiceDefinitionURI"
    public const val SERVICE_SUPPLY_POINTS: String = "ServiceSupplyPoints"
    public const val SERVICE_DEFINITION_URI: String = "ServiceDefinitionURI"
    public const val SERVICE_INFORMATION_EXTENSIONS: String = "ServiceInformationExtensions"

    public const val X509_CERTIFICATES: String = "X509Certificates"
    public const val X509_SUBJECT_NAMES: String = "X509SubjectNames"
    public const val PUBLIC_KEY_VALUES: String = "PublicKeyValues"
    public const val X509_SKIS: String = "X509SKIs"
    public const val OTHER_IDS: String = "OtherIds"

    public const val PKI_OB_ENCODING: String = "encoding"
    public const val PKI_OB_SPEC_REF: String = "specRef"
    public const val PKI_OB_VAL: String = "val"

    public const val SERVICE_SUPPLY_POINT_URI_TYPE: String = "ServiceType"
    public const val SERVICE_SUPPLY_POINT_URI_VALUE: String = "uriValue"

    public const val LOTE_TYPE_URI: String = "http://uri.etsi.org/19602/LoTEType"

    //
    // PID Provider's LoTE
    // A LoTE implementation of a list of providers of person identity data, which are notified by Member States
    //
    public const val EU_PID_PROVIDERS_LOTE: String = "EUPIDProvidersList"
    public const val EU_PID_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/PIDProvidersList/StatusDetn/EU"
    public const val EU_PID_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/PIDProviders/schemerules/EU"
    public const val EU_PID_PROVIDERS_SVC_TYPE_ISSUANCE: String = "http://uri.etsi.org/19602/SvcType/PID/Issuance"
    public const val EU_PID_PROVIDERS_SVC_TYPE_REVOCATION: String = "http://uri.etsi.org/19602/SvcType/PID/Revocation"

    //
    // EU Wallet Provider's LoTE
    // A LoTE implementation of a list of wallet providers, which are notified by Member States
    //
    public const val EU_WALLET_PROVIDERS_LOTE: String = "EUWalletProvidersList"
    public const val EU_WALLET_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/WalletProvidersList/StatusDetn/EU"
    public const val EU_WALLET_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/WalletProvidersList/schemerules/EU"
    public const val EU_WALLET_PROVIDERS_SVC_TYPE_ISSUANCE: String = "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance"
    public const val EU_WALLET_PROVIDERS_SVC_TYPE_REVOCATION: String = "http://uri.etsi.org/19602/SvcType/WalletSolution/Revocation"

    //
    // EU WRPAC
    // A LoTE implementation of a list of providers of wallet relying party access certificates,
    // which are notified by Member States
    //
    public const val EU_WRPAC_PROVIDERS_LOTE: String = "EUWRPACProvidersList"
    public const val EU_WRPAC_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/WRPACProvidersList/StatusDetn/EU"
    public const val EU_WRPAC_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/WRPACProvidersList/schemerules/EU"
    public const val EU_WRPAC_PROVIDERS_SVC_TYPE_ISSUANCE: String = "http://uri.etsi.org/19602/SvcType/WRPAC/Issuance"
    public const val EU_WRPAC_PROVIDERS_SVC_TYPE_REVOCATION: String = "http://uri.etsi.org/19602/SvcType/WRPAC/Revocation"

    //
    // EU WRPRC
    // A LoTE implementation of a list of providers of wallet relying party registration certificates,
    // which are notified by Member States
    //
    public const val EU_WRPRC_PROVIDERS_LOTE: String = "EUWRPRCProvidersList"
    public const val EU_WRPRC_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/WRPRCrovidersList/StatusDetn/EU"
    public const val EU_WRPRC_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/WRPRCProvidersList/schemerules/EU"
    public const val EU_WRPRC_PROVIDERS_SVC_TYPE_ISSUANCE: String = "http://uri.etsi.org/19602/SvcType/WRPRC/Issuance"
    public const val EU_WRPRC_PROVIDERS_SVC_TYPE_REVOCATION: String = "http://uri.etsi.org/19602/SvcType/WRPRC/Revocation"

    //
    // EU PUB EAA providers
    // A LoTE implementation of a list of public sector bodies issuing electronic attestation of attribute,
    // which are notified by Member States
    //
    public const val EU_PUB_EAA_PROVIDERS_LOTE: String = "EUPubEAAProvidersList"
    public const val EU_PUB_EAA_PROVIDERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/PubEAAProvidersList/StatusDetn/EU"
    public const val EU_PUB_EAA_PROVIDERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/PubEAAProvidersList/schemerules/EU"
    public const val EU_PUB_EAA_PROVIDERS_SVC_TYPE_ISSUANCE: String = "http://uri.etsi.org/19602/SvcType/PubEAA/Issuance"
    public const val EU_PUB_EAA_PROVIDERS_SVC_TYPE_REVOCATION: String = "http://uri.etsi.org/19602/SvcType/PubEAA/Revocation"

    //
    // EU RegistrarsAndRegisters
    // A LoTE implementation of a list of registrars and registers, which are notified by Member States
    //
    public const val EU_REGISTRARS_AND_REGISTERS_LOTE: String = "EURegistrarsAndRegistersList"
    public const val EU_REGISTRARS_AND_REGISTERS_STATUS_DETERMINATION_APPROACH: String = "http://uri.etsi.org/19602/RegistrarsAndRegistersList/StatusDetn/EU"
    public const val EU_REGISTRARS_AND_REGISTERS_SCHEME_COMMUNITY_RULES: String = "http://uri.etsi.org/19602/RegistrarsAndRegistersList/schemerules/EU"
    public const val EU_REGISTRARS_AND_REGISTERS_SVC_TYPE_REGISTER: String = "http://uri.etsi.org/19602/SvcType/Register"

    //
    // Language codes
    //
    public const val LANG_ENGLISH: String = "en"
}
