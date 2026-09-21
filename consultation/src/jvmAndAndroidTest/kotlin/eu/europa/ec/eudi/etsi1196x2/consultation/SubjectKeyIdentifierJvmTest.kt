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
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubjectKeyIdentifierJvmTest {
    private val certificates = Sample.create()

    @Test
    fun subjectKeyIdentifier_matchesIssuerAuthorityKeyIdentifier() {
        val rootSubjectKeyIdentifier =
            assertNotNull(CertificateOperationsJvm.getSubjectKeyIdentifier(certificates.root))
        val intermediateAuthorityKeyIdentifier =
            assertNotNull(CertificateOperationsJvm.getAuthorityKeyIdentifier(certificates.intermediate)?.keyIdentifier)
        val intermediateSubjectKeyIdentifier =
            assertNotNull(CertificateOperationsJvm.getSubjectKeyIdentifier(certificates.intermediate))
        val endAuthorityKeyIdentifier =
            assertNotNull(CertificateOperationsJvm.getAuthorityKeyIdentifier(certificates.end)?.keyIdentifier)

        assertTrue(rootSubjectKeyIdentifier.contentEquals(intermediateAuthorityKeyIdentifier))
        assertTrue(intermediateSubjectKeyIdentifier.contentEquals(endAuthorityKeyIdentifier))
    }
}
