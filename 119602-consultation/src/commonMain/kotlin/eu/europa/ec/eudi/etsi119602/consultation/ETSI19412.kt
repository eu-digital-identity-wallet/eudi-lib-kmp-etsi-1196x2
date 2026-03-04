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
 * OID constants for LoTE certificate policies and QCStatements.
 */
public object ETSI19412 {
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
