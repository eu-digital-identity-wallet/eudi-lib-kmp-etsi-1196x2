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
 * Object IDs and constants from RFC 5280 - Internet X.509 Public Key Infrastructure.
 *
 * This object contains OID constants for X.509 certificate extensions and access methods
 * as defined in RFC 5280.
 *
 * @see [RFC 5280 - Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://datatracker.ietf.org/doc/html/rfc5280)
 */
public object RFC5280 {

    /**
     * id-at-countryName OBJECT IDENTIFIER ::= { id-at 6 }
     */
    public const val ATTR_COUNTRY_NAME: String = "2.5.4.6"

    /**
     * id-ce-authorityKeyIdentifier OBJECT IDENTIFIER ::= { id-ce 35 }
     *
     * Provides a means of identifying the public key corresponding to the private key
     * used to sign this certificate.
     *
     * @see [RFC 5280 Section 4.2.1.1](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.1)
     */
    public const val EXT_AUTHORITY_KEY_IDENTIFIER: String = "2.5.29.35"

    /**
     * id-ce-subjectKeyIdentifier OBJECT IDENTIFIER ::= { id-ce 14 }
     *
     * Provides a means of identifying certificates that contain a particular public key.
     *
     * @see [RFC 5280 Section 4.2.1.2](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.2)
     */
    public const val EXT_SUBJECT_KEY_IDENTIFIER: String = "2.5.29.14"

    /**
     * id-ce-keyUsage OBJECT IDENTIFIER ::= { id-ce 15 }
     *
     * Defines the purpose (e.g., encipherment, signature) of the key contained in the certificate.
     *
     * @see [RFC 5280 Section 4.2.1.3](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.3)
     */
    public const val EXT_KEY_USAGE: String = "2.5.29.15"

    /**
     * id-ce-certificatePolicies OBJECT IDENTIFIER ::= { id-ce 32 }
     *
     * Indicates the set of certificate policy statements that apply to this certificate.
     *
     * @see [RFC 5280 Section 4.2.1.4](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.4)
     */
    public const val EXT_CERTIFICATE_POLICIES: String = "2.5.29.32"

    /**
     * id-ce-subjectAltName OBJECT IDENTIFIER ::= { id-ce 17 }
     *
     * Allows identities to be bound to the subject of the certificate using various forms.
     *
     * @see [RFC 5280 Section 4.2.1.6](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6)
     */
    public const val EXT_SUBJECT_ALT_NAME: String = "2.5.29.17"

    /**
     * id-ce-issuerAltName OBJECT IDENTIFIER ::= { id-ce 18 }
     *
     * Allows identities to be bound to the issuer of the certificate using various forms.
     *
     * @see [RFC 5280 Section 4.2.1.7](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.7)
     */
    public const val EXT_ISSUER_ALT_NAME: String = "2.5.29.18"

    /**
     * id-ce-basicConstraints OBJECT IDENTIFIER ::= { id-ce 19 }
     *
     * Indicates whether the certified public key may be used to verify certificate signatures.
     *
     * @see [RFC 5280 Section 4.2.1.9](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.9)
     */
    public const val EXT_BASIC_CONSTRAINTS: String = "2.5.29.19"

    /**
     * id-ce-nameConstraints OBJECT IDENTIFIER ::= { id-ce 30 }
     *
     * Indicates a name space within which all subject names in subsequent certificates
     * in a certification path MUST be located.
     *
     * @see [RFC 5280 Section 4.2.1.10](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.10)
     */
    public const val EXT_NAME_CONSTRAINTS: String = "2.5.29.30"

    /**
     * id-ce-cRLDistributionPoints OBJECT IDENTIFIER ::= { id-ce 31 }
     *
     * Identifies how CRL information is obtained.
     *
     * @see [RFC 5280 Section 4.2.1.13](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.13)
     */
    public const val EXT_CRL_DISTRIBUTION_POINTS: String = "2.5.29.31"

    /**
     * id-ce-authorityInfoAccess OBJECT IDENTIFIER ::= { id-pkix 1 1 }
     *
     * Indicates how to access information and services for the certification authority
     * that issued and signed this certificate.
     *
     * @see [RFC 5280 Section 4.2.2.1](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)
     */
    public const val EXT_AUTHORITY_INFO_ACCESS: String = "1.3.6.1.5.5.7.1.1"

    /**
     * id-ce-certificatePolicies OBJECT IDENTIFIER ::= { id-ce 32 }
     */
    public const val CERTIFICATE_POLICIES: String = "2.5.29.32"
}
