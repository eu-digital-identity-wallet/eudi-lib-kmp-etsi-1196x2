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

import eu.europa.ec.eudi.etsi1196x2.consultation.X509CertificateConstraintExtractors
import java.security.cert.X509Certificate

/**
 * Helper functions for creating LoTE-specific validators for [X509Certificate].
 *
 * These validators are configured according to the requirements specified in
 * ETSI TS 119 602 for each provider list type.
 *
 */
public val EULoTECertificateConstraintsJvm: EULoTECertificateConstraints<X509Certificate> =
    EULoTECertificateConstraints(
        getBasicConstraints = X509CertificateConstraintExtractors::getBasicConstraints,
        getQcStatements = X509CertificateConstraintExtractors::getQcStatements,
        getKeyUsage = X509CertificateConstraintExtractors::getKeyUsage,
        getValidityPeriod = X509CertificateConstraintExtractors::getValidityPeriod,
        getCertificatePolicies = X509CertificateConstraintExtractors::getCertificatePolicies,
    )
