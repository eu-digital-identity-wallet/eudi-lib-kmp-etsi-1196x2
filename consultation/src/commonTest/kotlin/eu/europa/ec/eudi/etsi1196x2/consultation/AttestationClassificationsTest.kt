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

import kotlin.test.*

class AttestationClassificationsTest {

    private val pidMDoc = MDoc(docType = "eu.europa.ec.eudi.pid.1")
    private val pidSdJwt = SDJwtVc(vct = "urn:eudi:pid:1")
    private val mdl = MDoc(docType = "org.iso.18013.5.1.mDL")
    private val loyaltyCard = SDJwtVc(vct = "https://example.com/loyalty")

    // Test classify method - basic functionality

    @Test
    fun `classify returns null when no predicates match`() {
        val classifications = AttestationClassifications()

        assertNull(classifications.classify(pidMDoc))
        assertNull(classifications.classify(mdl))
        assertNull(classifications.classify(loyaltyCard))
    }

    @Test
    fun `classify returns PID match`() {
        val classifications = AttestationClassifications(
            pids = pidMDoc.predicate,
        )

        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidMDoc))
        assertNull(classifications.classify(mdl))
    }

    @Test
    fun `classify returns PubEAA match`() {
        val classifications = AttestationClassifications(
            pubEAAs = loyaltyCard.predicate,
        )

        assertEquals(AttestationClassifications.Match.PubEAA, classifications.classify(loyaltyCard))
        assertNull(classifications.classify(pidMDoc))
    }

    @Test
    fun `classify returns QEAA match`() {
        val classifications = AttestationClassifications(
            qEAAs = mdl.predicate,
        )

        assertEquals(AttestationClassifications.Match.QEAA, classifications.classify(mdl))
        assertNull(classifications.classify(pidMDoc))
    }

    @Test
    fun `classify returns EAA match with use case`() {
        val classifications = AttestationClassifications(
            eaAs = mapOf("mdl" to mdl.predicate),
        )

        assertEquals(AttestationClassifications.Match.EAA("mdl"), classifications.classify(mdl))
        assertNull(classifications.classify(pidMDoc))
    }

    @Test
    fun `classify works with combined predicates using or`() {
        val classifications = AttestationClassifications(
            pids = pidMDoc.predicate or pidSdJwt.predicate,
        )

        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidMDoc))
        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidSdJwt))
        assertNull(classifications.classify(mdl))
    }

    @Test
    fun `classify works with regex predicates`() {
        val classifications = AttestationClassifications(
            pids = AttestationIdentifierPredicate.mdocMatching(Regex("eu\\.europa\\.ec\\.eudi\\.pid\\..*")),
            eaAs = mapOf("mdl" to AttestationIdentifierPredicate.mdocMatching(Regex("org\\.iso\\.18013\\..*"))),
        )

        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidMDoc))
        assertEquals(AttestationClassifications.Match.EAA("mdl"), classifications.classify(mdl))
        assertNull(classifications.classify(pidSdJwt))
    }

    @Test
    fun `classify works with any predicate`() {
        val classifications = AttestationClassifications(
            pids = AttestationIdentifierPredicate.any(setOf(pidMDoc, pidSdJwt)),
        )

        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidMDoc))
        assertEquals(AttestationClassifications.Match.PID, classifications.classify(pidSdJwt))
        assertNull(classifications.classify(mdl))
    }

    // Test classify method - at-most-one match guarantee

    @Test
    fun `classify throws when identifier matches PID and PubEAA`() {
        val classifications = AttestationClassifications(
            pids = mdl.predicate,
            pubEAAs = mdl.predicate,
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    @Test
    fun `classify throws when identifier matches PID and QEAA`() {
        val classifications = AttestationClassifications(
            pids = mdl.predicate,
            qEAAs = mdl.predicate,
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    @Test
    fun `classify throws when identifier matches PubEAA and QEAA`() {
        val classifications = AttestationClassifications(
            pubEAAs = mdl.predicate,
            qEAAs = mdl.predicate,
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    @Test
    fun `classify throws when identifier matches QEAA and EAA`() {
        val classifications = AttestationClassifications(
            qEAAs = mdl.predicate,
            eaAs = mapOf("mdl" to mdl.predicate),
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    @Test
    fun `classify throws when identifier matches multiple EAA use cases`() {
        val classifications = AttestationClassifications(
            eaAs = mapOf(
                "mdl" to mdl.predicate,
                "driving_license" to mdl.predicate,
            ),
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    @Test
    fun `classify throws when identifier matches all categories`() {
        val classifications = AttestationClassifications(
            pids = mdl.predicate,
            pubEAAs = mdl.predicate,
            qEAAs = mdl.predicate,
            eaAs = mapOf("mdl" to mdl.predicate),
        )

        assertFailsWith<IllegalStateException> {
            classifications.classify(mdl)
        }
    }

    // Test match method - diagnostic functionality

    @Test
    fun `match returns empty list when no predicates match`() {
        val classifications = AttestationClassifications()

        assertContentEquals(emptyList(), classifications.match(pidMDoc))
    }

    @Test
    fun `match returns single match when exactly one predicate matches`() {
        val classifications = AttestationClassifications(
            pids = pidMDoc.predicate,
        )

        assertContentEquals(
            listOf(AttestationClassifications.Match.PID),
            classifications.match(pidMDoc),
        )
    }

    @Test
    fun `match returns all matches for misconfigured predicates`() {
        val classifications = AttestationClassifications(
            pids = mdl.predicate,
            pubEAAs = mdl.predicate,
            qEAAs = mdl.predicate,
        )

        val matches = classifications.match(mdl)
        assertEquals(3, matches.size)
        assertContentEquals(
            listOf(
                AttestationClassifications.Match.PID,
                AttestationClassifications.Match.PubEAA,
                AttestationClassifications.Match.QEAA,
            ),
            matches,
        )
    }

    @Test
    fun `match returns multiple EAA matches when misconfigured`() {
        val classifications = AttestationClassifications(
            eaAs = mapOf(
                "mdl" to mdl.predicate,
                "driving_license" to mdl.predicate,
                "vehicle" to mdl.predicate,
            ),
        )

        val matches = classifications.match(mdl)
        assertEquals(3, matches.size)
        assertContentEquals(
            listOf(
                AttestationClassifications.Match.EAA("mdl"),
                AttestationClassifications.Match.EAA("driving_license"),
                AttestationClassifications.Match.EAA("vehicle"),
            ),
            matches,
        )
    }
}
