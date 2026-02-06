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

import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.cert.CertPathValidatorException
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

data class Certs(
    val end: X509Certificate,
    val intermediate: X509Certificate,
    val root: X509Certificate,
)

object Sample {
    private const val SIGN_ALG = "SHA256withECDSA"

    fun create(): Certs = with(CertOps) {
        //
        // Trust Anchor
        //
        val name: X500Name =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo Root Certificate")
            }.build()
        val (trustKeyPair, trustCertHolder) = genTrustAnchor(SIGN_ALG, name)
        val trustCert = trustCertHolder.toCertificate()

        //
        // CA
        //
        val caSubject =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo Intermediate Certificate")
            }.build()
        val (caKeyPair, caCertHolder) =
            genIntermediateCertificate(
                trustCertHolder,
                trustKeyPair.private,
                SIGN_ALG,
                0,
                caSubject,
            )
        val caCert = caCertHolder.toCertificate()

        //
        // End Entity
        //
        val eeSubject =
            X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.C, "Utopia")
                addRDN(BCStyle.O, "Awesome Organization")
                addRDN(BCStyle.CN, "Demo End-Entity Certificate")
            }.build()
        val (_, eeCertHolder) =
            genEndEntity(caCertHolder, caKeyPair.private, SIGN_ALG, eeSubject)
        val eeCert = eeCertHolder.toCertificate()

        return Certs(eeCert, caCert, trustCert)
    }
}

class ValidateCertificateChainJvmTest {
    private val certs = Sample.create()
    private val intermediate get() = certs.intermediate
    private val eeCertificate get() = certs.end
    private val root get() = certs.root

    @Test
    fun `chain contains end-entity and intermediate certs should succeed`() = runTest {
        // Chain contains end-entity and intermediate certs
        // trust contains the trust anchor cert
        val chain = listOf(eeCertificate, intermediate)
        val trust = listOf(root)
        val trustAnchor = assertTrusted(chain, trust)
        assertEquals(root, trustAnchor.trustedCert)
    }

    @Test
    fun `chain contains end-entity intermediate and root certs should succeed`() = runTest {
        // Chain contains end-entity, intermediate and root certs
        // trust contains the trust anchor cert
        val chain = listOf(eeCertificate, intermediate, root)
        val trust = listOf(root)
        val trustAnchor = assertTrusted(chain, trust)
        assertEquals(root, trustAnchor.trustedCert)
    }

    @Test
    fun `chain contain end-entity cert, trust contains intermediate and root certs then should succeed`() = runTest {
        // Chain contains end-entity
        // trust contains the CA and Trust Anchor certs
        val chain = listOf(eeCertificate)
        val trust = listOf(intermediate, root)
        val trustAnchor = assertTrusted(chain, trust)
        // If the validator finds a trust anchor in the trust set that issued the end-entity, it may stop there.
        // In this case, the intermediate cert is in the trust set and it issued the eeCertificate.
        assertEquals(intermediate, trustAnchor.trustedCert)
    }

    @Test
    fun `cert order in chain should affect validation using default Provider`() = runTest {
        val chain = listOf(intermediate, eeCertificate)
        val trust = listOf(root)
        val validation = validate(chain, trust, provider = null)
        assertIs<CertificationChainValidation.NotTrusted>(validation)
    }

    @Test
    fun `cert order in chain should affect validation using Bouncy Castle Provider`() = runTest {
        val chain = listOf(intermediate, eeCertificate)
        val trust = listOf(root)
        val trustAnchor = assertTrusted(chain, trust, BouncyCastleProvider())
        assertEquals(root, trustAnchor.trustedCert)
    }

    @Test
    fun `validate a partial chain should fail`() = runTest {
        val chain = listOf(eeCertificate)
        val trust = listOf(root)
        val validation = validate(chain, trust)
        assertIs<CertificationChainValidation.NotTrusted>(validation)
        assertIs<CertPathValidatorException>(validation.cause)
    }

    @Test
    fun `when directly trusting the intermediate should succeed `() = runTest {
        val chain = listOf(eeCertificate)
        val trust = listOf(intermediate)
        val trustAnchor = assertTrusted(chain, trust)
        assertEquals(intermediate, trustAnchor.trustedCert)
    }
}

private suspend fun assertTrusted(
    chain: List<X509Certificate>,
    trust: List<X509Certificate>,
    provider: Provider? = null,
): TrustAnchor {
    val validation = validate(chain, trust, provider)
    assertIs<CertificationChainValidation.Trusted<TrustAnchor>>(validation)
    return validation.trustAnchor
}

private suspend fun validate(
    chain: List<X509Certificate>,
    trust: List<X509Certificate>,
    provider: Provider? = BouncyCastleProvider(),
): CertificationChainValidation<TrustAnchor> {
    val customization = JvmSecurity.withRevocationEnabled(false)
    val validateCertificateChain =
        provider
            ?.let { ValidateCertificateChainJvm(provider = provider, customization = customization) }
            ?: ValidateCertificateChainJvm(customization = customization)

    val trustAnchors =
        trust.map { TrustAnchor(it, null) }.toSet()
    return validateCertificateChain(chain, trustAnchors)
}
