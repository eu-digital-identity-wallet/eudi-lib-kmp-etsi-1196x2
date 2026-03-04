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
 * Certificate Policy OIDs per ETSI TS 119 475 Clause 6.1.3.
 *
 * These OIDs identify certificate policies for Wallet Relying Party Registration Certificates (WRPRC).
 *
 * Source: ETSI TS 119 475 V1.1.1 Clause 6.1.3 - Certificate Policy name and identification
 */
public object ETSI119475 {

    /**
     * Certificate Policy OID for WRPRC providers.
     *
     * WRPRC: Certificate policy for wallet-relying party registration certificates
     * issued to EUDIW wallet-relying parties.
     *
     * ASN.1 declaration:
     * ```
     * wrprc OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiwrpa(19475) policy-identifiers(3) wrprc(1) }
     * ```
     */
    public const val WRPRC: String = "0.4.0.1949.3.1"
}
