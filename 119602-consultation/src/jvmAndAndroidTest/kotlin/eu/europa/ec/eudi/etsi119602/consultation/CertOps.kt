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

import eu.europa.ec.eudi.etsi1196x2.consultation.JvmSecurity
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

object CertOps {
    private val Ctx = SecCtx(provider = null)

    private val clock = Clock.System

    private var serialNumberBase: Long = System.currentTimeMillis()

    @Synchronized
    private fun calculateSerialNumber(): BigInteger = BigInteger.valueOf(serialNumberBase++)

    private fun calculateDate(@Suppress("SameParameterValue") hoursInFuture: Int): Date {
        val secs = System.currentTimeMillis() / 1000
        return Date((secs + (hoursInFuture * 60 * 60)) * 1000)
    }

    private fun notBefore(d: Duration = Duration.ZERO): Instant =
        (clock.now() + d)

    fun genTrustAnchor(
        sigAlg: String,
        subject: X500Name,
        keyUsage: KeyUsage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = null,
        pathLenConstraint: Int? = null,
    ): Pair<KeyPair, X509CertificateHolder> {
        val kp = Ctx.generateECPair()
        val certHolder =
            createTrustAnchor(kp, sigAlg, subject, keyUsage, qcStatements, policyOids, pathLenConstraint)
        return kp to certHolder
    }

    /**
     * Build a sample self-signed V3 certificate to use as a trust anchor, or
     *  root certificate.
     */
    private fun createTrustAnchor(
        keyPair: KeyPair,
        sigAlg: String,
        name: X500Name,
        keyUsage: KeyUsage,
        qcStatements: List<Pair<String, Boolean>>?,
        policyOids: List<String>? = null,
        pathLenConstraint: Int? = null,
    ): X509CertificateHolder {
        return JcaX509v3CertificateBuilder(
            name,
            calculateSerialNumber(),
            Date.from(notBefore().toJavaInstant()),
            calculateDate(24 * 31),
            name,
            keyPair.public,
        ).apply {
            subjectKeyIdentifier(keyPair.public)
            if (pathLenConstraint != null) {
                basicConstraints(BasicConstraints(pathLenConstraint))
            } else {
                basicConstraints(BasicConstraints(true))
            }
            keyUsage(keyUsage)
            if (policyOids != null) {
                certificatePolicies(policyOids)
            }
            if (!qcStatements.isNullOrEmpty()) {
                val qcStatementSequences = qcStatements.map { (qcType, qcCompliance) ->
                    DERSequence(
                        arrayOf(
                            ASN1ObjectIdentifier(qcType),
                            DERUTF8String(if (qcCompliance) "compliant" else "non-compliant"),
                        ),
                    )
                }
                val qcStatementsSeq = DERSequence(qcStatementSequences.toTypedArray())
                addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"), false, qcStatementsSeq)
            }
        }.build(sigAlg, keyPair.private)
    }

    // TODO
    //  - magic numbers
    //  - defaults (should be removed)
    fun genCAIssuedEndEntityCertificate(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        subject: X500Name,
        keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = listOf("0.4.0.194118.1.1"), // Default: NCP-n-eudiwrp
        caIssuersUri: String? = "http://ca.example.com/ca.crt",
        ocspUri: String? = "http://ocsp.example.com/",
        subjectAltNameUri: String? = "https://wallet-relying-party.example.com",
        subjectKeyPairAlg: String = "EC",
        subjectKeySize: Int? = null,
    ): Pair<KeyPair, X509CertificateHolder> {
        val eeKp = Ctx.generateKeyPair(subjectKeyPairAlg, subjectKeySize)
        val eeCertHolder = createEndEntity(
            signerCert,
            signerKey,
            sigAlg,
            eeKp.public,
            subject,
            keyUsage,
            qcStatements,
            policyOids,
            caIssuersUri,
            ocspUri,
            subjectAltNameUri,
        )
        return eeKp to eeCertHolder
    }

    fun genSelfSignedEndEntityCertificate(
        sigAlg: String,
        subject: X500Name,
        keyUsage: KeyUsage = KeyUsage(KeyUsage.digitalSignature),
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = null,
    ): Pair<KeyPair, X509CertificateHolder> {
        val kp = Ctx.generateECPair()
        val certHolder = createSelfSignedEndEntity(
            kp,
            sigAlg,
            subject,
            keyUsage,
            qcStatements,
            policyOids,
        )
        return kp to certHolder
    }

    /**
     * Creates a self-signed end-entity certificate (cA=FALSE).
     * Note: No AIA extension is added (per ETSI TS 119 412-6 PID-4.4.3-01).
     */
    private fun createSelfSignedEndEntity(
        keyPair: KeyPair,
        sigAlg: String,
        name: X500Name,
        keyUsage: KeyUsage,
        qcStatements: List<Pair<String, Boolean>>?,
        policyOids: List<String>?,
    ): X509CertificateHolder {
        return JcaX509v3CertificateBuilder(
            name,
            calculateSerialNumber(),
            Date.from(notBefore().toJavaInstant()),
            calculateDate(24 * 31),
            name,
            keyPair.public,
        ).apply {
            subjectKeyIdentifier(keyPair.public)
            basicConstraints(BasicConstraints(false)) // end-entity (cA=FALSE)
            keyUsage(keyUsage)
            if (!qcStatements.isNullOrEmpty()) {
                val qcStatementSequences = qcStatements.map { (qcType, qcCompliance) ->
                    DERSequence(
                        arrayOf(
                            ASN1ObjectIdentifier(qcType),
                            DERUTF8String(if (qcCompliance) "compliant" else "non-compliant"),
                        ),
                    )
                }
                val qcStatementsSeq = DERSequence(qcStatementSequences.toTypedArray())
                addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"), false, qcStatementsSeq)
            }
            if (policyOids != null) {
                certificatePolicies(policyOids)
            }
        }.build(sigAlg, keyPair.private)
    }

    private fun createEndEntity(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        certKey: PublicKey,
        subject: X500Name,
        keyUsage: KeyUsage,
        qcStatements: List<Pair<String, Boolean>>? = null,
        policyOids: List<String>? = null,
        caIssuersUri: String? = null,
        ocspUri: String? = null,
        subjectAltNameUri: String? = null,
    ): X509CertificateHolder =
        JcaX509v3CertificateBuilder(
            signerCert.subject,
            calculateSerialNumber(),
            Date.from(notBefore().toJavaInstant()),
            calculateDate(24 * 31),
            subject,
            certKey,
        ).apply {
            authorityKeyIdentifier(signerCert)
            subjectKeyIdentifier(certKey)
            basicConstraints(BasicConstraints(false)) // do not allow this cert to sign other certs
            keyUsage(keyUsage)
            if (!qcStatements.isNullOrEmpty()) {
                val qcStatementSequences = qcStatements.map { (qcType, qcCompliance) ->
                    DERSequence(
                        arrayOf(
                            ASN1ObjectIdentifier(qcType),
                            DERUTF8String(if (qcCompliance) "compliant" else "non-compliant"),
                        ),
                    )
                }
                val qcStatementsSeq = DERSequence(qcStatementSequences.toTypedArray())
                addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"), false, qcStatementsSeq)
            }
            certificatePolicies(policyOids ?: listOf())
            authorityInformationAccess(caIssuersUri, ocspUri)
            if (subjectAltNameUri != null) {
                subjectAlternativeName(subjectAltNameUri)
            }
        }.build(sigAlg, signerKey)

    /**
     * Public extension function for converting [X509CertificateHolder] to [X509Certificate].
     */
    fun X509CertificateHolder.toX509Certificate(): X509Certificate {
        val cFact = Ctx.certFactory()
        return cFact.generateCertificate(encoded.inputStream()) as X509Certificate
    }

    private fun signer(sigAlg: String, privateKey: PrivateKey): ContentSigner =
        Ctx.jcaContentSignerBuilder(sigAlg).build(privateKey)

    private fun JcaX509v3CertificateBuilder.build(sigAlg: String, privateKey: PrivateKey): X509CertificateHolder {
        val signer = signer(sigAlg, privateKey)
        return build(signer)
    }
}

//
// Kotlin extensions
//

private fun JcaX509v3CertificateBuilder.authorityKeyIdentifier(signerCert: X509CertificateHolder) {
    addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(signerCert))
}

private fun JcaX509v3CertificateBuilder.subjectKeyIdentifier(certKey: PublicKey) {
    addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(certKey))
}

private fun JcaX509v3CertificateBuilder.keyUsage(keyUsage: KeyUsage) {
    addExtension(Extension.keyUsage, true, keyUsage)
}

/**
 * Adds a Certificate Policies extension to the certificate.
 *
 * @param policyOids list of policy OIDs to include
 *
 * @see [RFC 5280 Section 4.2.1.4](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.4)
 */
private fun JcaX509v3CertificateBuilder.certificatePolicies(policyOids: List<String>) {
    // CertificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
    // PolicyInformation ::= SEQUENCE {
    //   policyIdentifier   CertPolicyId,
    //   policyQualifiers   SEQUENCE SIZE (1..MAX) OF PolicyQualifierInfo OPTIONAL
    // }
    val policyInfos = policyOids.map { oid ->
        PolicyInformation(ASN1ObjectIdentifier(oid))
    }
    addExtension(Extension.certificatePolicies, false, DERSequence(policyInfos.toTypedArray()))
}

/**
 * Adds an Authority Information Access (AIA) extension to the certificate.
 *
 * @param caIssuersUri URI where the CA certificate can be retrieved (id-ad-caIssuers)
 * @param ocspUri URI of the OCSP responder (id-ad-ocsp)
 *
 * @see [RFC 5280 Section 4.2.2.1](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)
 */
private fun JcaX509v3CertificateBuilder.authorityInformationAccess(
    caIssuersUri: String? = null,
    ocspUri: String? = null,
) {
    val accessDescriptions = mutableListOf<AccessDescription>()

    caIssuersUri?.let { uri ->
        accessDescriptions.add(
            AccessDescription(
                AccessDescription.id_ad_caIssuers,
                GeneralName(GeneralName.uniformResourceIdentifier, DERIA5String(uri)),
            ),
        )
    }

    ocspUri?.let { uri ->
        accessDescriptions.add(
            AccessDescription(
                AccessDescription.id_ad_ocsp,
                GeneralName(GeneralName.uniformResourceIdentifier, DERIA5String(uri)),
            ),
        )
    }

    if (accessDescriptions.isNotEmpty()) {
        // OID for AIA extension (id-pe-authorityInfoAccess)
        addExtension(Extension.authorityInfoAccess, false, DERSequence(accessDescriptions.toTypedArray()))
    }
}

/**
 * Adds a Subject Alternative Name extension with a URI.
 *
 * @param uri the URI to add to the SAN extension
 *
 * @see [RFC 5280 Section 4.2.1.6](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.6)
 */
private fun JcaX509v3CertificateBuilder.subjectAlternativeName(uri: String) {
    val generalName = GeneralName(GeneralName.uniformResourceIdentifier, DERIA5String(uri))
    addExtension(Extension.subjectAlternativeName, false, DERSequence(arrayOf(generalName)))
}

/**
 * The BasicConstraints extension helps you to determine if the certificate containing it is allowed to
 * sign other certificates, and if so, what depth this can go to.
 *
 * So, for example, if cA is TRUE and the pathLenConstraint is 0, then the certificate, as far as this extension
 * is concerned, is allowed to sign other certificates, but none of the certificates so signed can be used to sign other certificates and lengthen
 * the chain.
 *
 */
private fun JcaX509v3CertificateBuilder.basicConstraints(c: BasicConstraints) {
    addExtension(Extension.basicConstraints, true, c)
}

private val extUtils = JcaX509ExtensionUtils()

private class SecCtx(val provider: Provider? = null) {

    fun certFactory(): CertificateFactory =
        provider
            ?.let { JvmSecurity.x509CertFactory(it) }
            ?: JvmSecurity.DefaultX509Factory

    fun kpGenerator(): KeyPairGenerator =
        provider
            ?.let { KeyPairGenerator.getInstance("EC", provider) }
            ?: KeyPairGenerator.getInstance("EC")

    fun generateECPair(): KeyPair = kpGenerator().genKeyPair()

    fun generateKeyPair(algorithm: String, keySize: Int? = null): KeyPair =
        if (algorithm.uppercase() == "EC") {
            generateECPair()
        } else {
            val kpg = provider
                ?.let { KeyPairGenerator.getInstance(algorithm, it) }
                ?: KeyPairGenerator.getInstance(algorithm)
            if (keySize != null) kpg.initialize(keySize)
            kpg.genKeyPair()
        }

    fun jcaContentSignerBuilder(sigAlg: String) =
        JcaContentSignerBuilder(sigAlg).apply {
            if (provider != null) {
                setProvider(provider)
            }
        }
}
