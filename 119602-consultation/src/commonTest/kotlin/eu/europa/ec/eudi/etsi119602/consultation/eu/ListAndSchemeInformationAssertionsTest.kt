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
package eu.europa.ec.eudi.etsi119602.consultation.eu

import eu.europa.ec.eudi.etsi119602.datamodel.ListAndSchemeInformation
import eu.europa.ec.eudi.etsi119602.datamodel.MultilanguageString
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class ListAndSchemeInformationAssertionsTest {

    private val issueDate = Instant.parse("2026-01-01T00:00:00Z")

    private fun listAndSchemeInformation(nextUpdate: Instant): ListAndSchemeInformation =
        ListAndSchemeInformation.implicit(
            schemeOperatorName = listOf(MultilanguageString.en("Test Scheme Operator")),
            listIssueDateTime = issueDate,
            nextUpdate = nextUpdate,
        )

    @Test
    fun `next update within the limit should pass`() {
        val list = listAndSchemeInformation(Instant.parse("2026-04-01T00:00:00Z")) // 3 months after issue
        with(ListAndSchemeInformationAssertions) {
            list.ensureNextUpdateIsWithinMonths(6)
        }
    }

    @Test
    fun `next update exactly at the limit should pass`() {
        val list = listAndSchemeInformation(Instant.parse("2026-07-01T00:00:00Z")) // 6 months after issue
        with(ListAndSchemeInformationAssertions) {
            list.ensureNextUpdateIsWithinMonths(6)
        }
    }

    @Test
    fun `next update beyond the limit should fail`() {
        val list = listAndSchemeInformation(Instant.parse("2026-08-01T00:00:00Z")) // 7 months after issue
        with(ListAndSchemeInformationAssertions) {
            val e = assertFailsWith<IllegalStateException> {
                list.ensureNextUpdateIsWithinMonths(6)
            }
            assertTrue(e.message?.contains("must be within 6 months") == true, "Unexpected message: ${e.message}")
        }
    }
}
