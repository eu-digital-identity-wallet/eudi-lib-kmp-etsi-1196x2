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

import eu.europa.ec.eudi.etsi1196x2.consultation.certs.ETSI319412
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.QCStatementInfo
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.qualified.QCStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QcTypeParsingTest {

    @Test
    fun `valid QcType with one OID is returned`() {
        val statements = CertificateOperationsJvm.getQcStatements(
            certificateWithQcStatements(qcTypeStatement(listOf(PID_QC_TYPE))),
        )

        assertEquals(listOf(QCStatementInfo.QcType(PID_QC_TYPE)), statements)
    }

    @Test
    fun `QcType with multiple OIDs is excluded while sibling statements remain`() {
        val statements = CertificateOperationsJvm.getQcStatements(
            certificateWithQcStatements(
                qcTypeStatement(listOf(PID_QC_TYPE, WALLET_QC_TYPE)),
                QCStatement(ASN1ObjectIdentifier(ETSI319412.QC_COMPLIANCE)),
            ),
        )

        assertEquals(listOf(QCStatementInfo.OtherQcStatement(ETSI319412.QC_COMPLIANCE)), statements)
    }

    @Test
    fun `QcType with no OIDs is excluded`() {
        val statements = CertificateOperationsJvm.getQcStatements(
            certificateWithQcStatements(qcTypeStatement(emptyList())),
        )

        assertTrue(statements.isEmpty())
    }

    private fun certificateWithQcStatements(vararg statements: QCStatement) =
        with(CertOps) {
            val issuerName = X500Name("CN=QcType Test Issuer")
            val (issuerKeyPair, issuer) = genTrustAnchor(SIGN_ALG, issuerName)
            val (_, endEntity) =
                genEndEntity(
                    issuer,
                    issuerKeyPair.private,
                    SIGN_ALG,
                    X500Name("CN=QcType Test"),
                    statements.toList(),
                )
            endEntity.toX509Certificate()
        }

    private fun qcTypeStatement(typeOids: List<String>): QCStatement {
        val typeIdentifiers = ASN1EncodableVector()
        typeOids.forEach { typeIdentifiers.add(ASN1ObjectIdentifier(it)) }
        return QCStatement(ASN1ObjectIdentifier(ETSI319412.QC_TYPE), DERSequence(typeIdentifiers))
    }

    private companion object {
        const val SIGN_ALG = "SHA256withECDSA"
        const val PID_QC_TYPE = "0.4.0.194126.1.1"
        const val WALLET_QC_TYPE = "0.4.0.194126.1.2"
    }
}
