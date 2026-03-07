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
 * QCStatement OIDs per ETSI TS 119 412-6 Annex A.
 *
 * These OIDs identify the type of QCStatement in certificates issued to PID and Wallet providers.
 *
 * Source: ETSI TS 119 412-6 V1.1.1 Annex A (normative): ASN.1 declarations
 */
public object ETSI119412 {

    /**
     * QCStatement OID for PID Provider certificates (id-etsi-qct-pid).
     *
     * ASN.1 declaration:
     * ```
     * id-etsi-qct-pid OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiw(194126) qct(1) pid(1) }
     * ```
     */
    public const val ID_ETSI_QCT_PID: String = "0.4.0.194126.1.1"

    /**
     * QCStatement OID for Wallet Provider certificates (id-etsi-qct-wal).
     *
     * ASN.1 declaration:
     * ```
     * id-etsi-qct-wal OBJECT IDENTIFIER ::= { itu-t(0) identified-organization(4) etsi(0)
     *   eudiw(194126) qct(1) wal(2) }
     * ```
     */
    public const val ID_ETSI_QCT_WAL: String = "0.4.0.194126.1.2"
}
