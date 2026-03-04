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

public interface CertificateOperations<CERT : Any> {
    public suspend fun getBasicConstraints(certificate: CERT): BasicConstraintsInfo
    public suspend fun getQcStatements(certificate: CERT): List<QCStatementInfo>
    public suspend fun getKeyUsage(certificate: CERT): KeyUsageBits?
    public suspend fun getValidityPeriod(certificate: CERT): ValidityPeriod
    public suspend fun getCertificatePolicies(certificate: CERT): List<String>
    public suspend fun isSelfSigned(certificate: CERT): Boolean
    public suspend fun getAiaExtension(certificate: CERT): AuthorityInformationAccess?
}
