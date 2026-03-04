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
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.PolicyInformation
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
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
        name: X500Name,
    ): Pair<KeyPair, X509CertificateHolder> {
        val kp = Ctx.generateECPair()
        val certHolder = createTrustAnchor(kp, sigAlg, name)
        return kp to certHolder
    }

    fun genIntermediateCertificate(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        followingCACerts: Int = 0,
        subject: X500Name,
    ): Pair<KeyPair, X509CertificateHolder> {
        val caKp = Ctx.generateECPair()
        val caCertHolder =
            createIntermediateCertificate(signerCert, signerKey, sigAlg, caKp.public, followingCACerts, subject)
        return caKp to caCertHolder
    }

    fun genEndEntity(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        subject: X500Name,
    ): Pair<KeyPair, X509CertificateHolder> {
        val eeKp = Ctx.generateECPair()
        val eeCertHolder = createEndEntity(signerCert, signerKey, sigAlg, eeKp.public, subject)
        return eeKp to eeCertHolder
    }

    /**
     * Build a sample self-signed V3 certificate to use as a trust anchor, or
     *  root certificate.
     */
    fun createTrustAnchor(
        keyPair: KeyPair,
        sigAlg: String,
        name: X500Name,
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
            basicConstraints(BasicConstraints(true))
            keyUsage(KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        }.build(sigAlg, keyPair.private)
    }

    /**
     * Build a sample self-signed V3 certificate with QCStatement for PID/Wallet providers.
     *
     * @param keyPair the key pair for the certificate
     * @param sigAlg signature algorithm (e.g., "SHA256withECDSA")
     * @param name subject/issuer name
     * @param qcType QCStatement type OID (e.g., "0.4.0.1949.1.1" for PID)
     * @param qcCompliance whether the certificate is compliant with the QC type
     * @param policyOids optional list of certificate policy OIDs
     * @return the generated certificate holder
     */
    fun createTrustAnchorWithQCStatement(
        keyPair: KeyPair,
        sigAlg: String,
        name: X500Name,
        qcType: String,
        qcCompliance: Boolean = true,
        policyOids: List<String>? = null,
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
            basicConstraints(BasicConstraints(false)) // End-entity certificate
            keyUsage(KeyUsage(KeyUsage.digitalSignature))
            qcStatement(qcType, qcCompliance)
            policyOids?.let { certificatePolicies(it) }
        }.build(sigAlg, keyPair.private)
    }

    /**
     * Build a sample self-signed V3 certificate with AIA extension for CA-issued end-entity certificates.
     *
     * @param keyPair the key pair for the certificate
     * @param sigAlg signature algorithm (e.g., "SHA256withECDSA")
     * @param name subject name
     * @param issuerCert issuer certificate (for authority key identifier)
     * @param qcType QCStatement type OID (e.g., "0.4.0.1949.1.1" for PID)
     * @param caIssuersUri URI where the CA certificate can be retrieved
     * @param ocspUri optional URI of the OCSP responder
     * @param policyOids optional list of certificate policy OIDs
     * @return the generated certificate holder
     */
    fun createEndEntityWithAIA(
        keyPair: KeyPair,
        sigAlg: String,
        name: X500Name,
        issuerCert: X509CertificateHolder,
        qcType: String,
        caIssuersUri: String,
        ocspUri: String? = null,
        policyOids: List<String>? = null,
    ): X509CertificateHolder {
        return JcaX509v3CertificateBuilder(
            issuerCert.subject,
            calculateSerialNumber(),
            Date.from(notBefore().toJavaInstant()),
            calculateDate(24 * 31),
            name,
            keyPair.public,
        ).apply {
            authorityKeyIdentifier(issuerCert)
            subjectKeyIdentifier(keyPair.public)
            basicConstraints(BasicConstraints(false)) // End-entity certificate
            keyUsage(KeyUsage(KeyUsage.digitalSignature))
            qcStatement(qcType, qcCompliance = true)
            authorityInformationAccess(caIssuersUri, ocspUri)
            policyOids?.let { certificatePolicies(it) }
        }.build(sigAlg, keyPair.private)
    }

    /**
     * Build a sample V3 intermediate certificate that can be used as a CA
     *  certificate.
     *  @param signerCert certificate carrying the public key that will late
     *  be used to verify this certificate's signature
     *  @param signerKey private key used to generate the signature in the certificate
     *  @param certKey public key to be installed in the certificate.
     */
    fun createIntermediateCertificate(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        certKey: PublicKey,
        followingCACerts: Int = 0,
        subject: X500Name,
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
            basicConstraints(BasicConstraints(followingCACerts)) // allow this cert to sign other certs
            keyUsage(KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.cRLSign))
        }.build(sigAlg, signerKey)

    /**
     * Build a sample V3 intermediate CA certificate with policy OIDs.
     *
     * @param signerCert certificate carrying the public key that will be used to verify this certificate's signature
     * @param signerKey private key used to generate the signature
     * @param sigAlg signature algorithm
     * @param certKey public key to be installed in the certificate
     * @param followingCACerts number of CA certificates that can follow in the chain
     * @param subject subject name
     * @param policyOids list of certificate policy OIDs
     * @return the generated certificate holder
     */
    fun createIntermediateCertificateWithPolicy(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        certKey: PublicKey,
        followingCACerts: Int = 0,
        subject: X500Name,
        policyOids: List<String>,
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
            basicConstraints(BasicConstraints(followingCACerts))
            keyUsage(KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            certificatePolicies(policyOids)
        }.build(sigAlg, signerKey)

    fun createEndEntity(
        signerCert: X509CertificateHolder,
        signerKey: PrivateKey,
        sigAlg: String,
        certKey: PublicKey,
        subject: X500Name,
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
            keyUsage(KeyUsage(KeyUsage.digitalSignature))
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

    private fun JcaX509v1CertificateBuilder.build(sigAlg: String, privateKey: PrivateKey): X509CertificateHolder {
        val signer = signer(sigAlg, privateKey)
        return build(signer)
    }

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
 * Adds a QCStatement extension to the certificate.
 *
 * @param qcType OID identifying the QC type (e.g., id-etsi-qct-pid, id-etsi-qct-wal)
 * @param qcCompliance whether the certificate is compliant with the QC type
 *
 * @see [ETSI EN 319 412-5 - QCStatements](https://www.etsi.org/deliver/etsi_en/319400_319499/31941205/)
 */
private fun JcaX509v3CertificateBuilder.qcStatement(qcType: String, qcCompliance: Boolean = true) {
    // QCStatement ASN.1 structure:
    // QCStatements ::= SEQUENCE OF QCStatement
    // QCStatement ::= SEQUENCE {
    //   statementId   OBJECT IDENTIFIER,
    //   statementInfo ANY DEFINED BY statementId OPTIONAL
    // }
    val qcStatementSequence = DERSequence(
        arrayOf<ASN1Encodable>(
            ASN1ObjectIdentifier(qcType),
            DERUTF8String(if (qcCompliance) "compliant" else "non-compliant"),
        ),
    )
    val qcStatementsSequence = DERSequence(qcStatementSequence)

    // OID for QCStatements extension (id-pe-qcStatements = 1.3.6.1.5.5.7.1.3)
    addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"), false, qcStatementsSequence)
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

    fun jcaContentSignerBuilder(sigAlg: String) =
        JcaContentSignerBuilder(sigAlg).apply {
            if (provider != null) {
                setProvider(provider)
            }
        }
}
