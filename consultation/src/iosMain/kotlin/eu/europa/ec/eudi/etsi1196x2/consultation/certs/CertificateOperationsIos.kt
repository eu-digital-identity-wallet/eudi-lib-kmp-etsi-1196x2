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

import eu.europa.ec.eudi.etsi1196x2.consultation.pkix.PKIXCertificateInspector
import eu.europa.ec.eudi.etsi1196x2.consultation.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import kotlin.time.Instant

/**
 * iOS implementation of [CertificateOperations] for DER-encoded certificates ([NSData]).
 *
 * Delegates all field extraction to the native [PKIXCertificateInspector] (Security.framework
 * for public-key info, an in-bridge ASN.1 parser for everything else) and converts the
 * Objective-C return shapes into the common-side data classes.
 *
 * @param inspector the Objective-C bridge to certificate introspection
 */
public class CertificateOperationsIos(
    private val inspector: PKIXCertificateInspector = PKIXCertificateInspector(),
) : CertificateOperations<NSData> {

    override fun getBasicConstraints(certificate: NSData): BasicConstraintsInfo {
        val raw = inspector.getBasicConstraints(certificate)
            ?: return BasicConstraintsInfo(isCa = false, pathLenConstraint = null)
        return BasicConstraintsInfo(
            isCa = raw.boolFor("isCA") ?: false,
            pathLenConstraint = raw.intFor("pathLengthConstraint"),
        )
    }

    override fun getQcStatements(certificate: NSData): List<QCStatementInfo> {
        val raw = inspector.getQcStatements(certificate) ?: return emptyList()
        return raw.mapNotNull { entry ->
            val dict = entry as? Map<*, *> ?: return@mapNotNull null
            when (dict["kind"] as? String) {
                "qcType" -> (dict["typeIdentifier"] as? String)?.let { QCStatementInfo.QcType(it) }
                "other" -> (dict["statementId"] as? String)?.let { QCStatementInfo.OtherQcStatement(it) }
                else -> null
            }
        }
    }

    override fun getKeyUsage(certificate: NSData): KeyUsageBits? {
        val raw = inspector.getKeyUsage(certificate) ?: return null
        return KeyUsageBits(
            digitalSignature = raw.boolFor("digitalSignature") ?: false,
            nonRepudiation = raw.boolFor("nonRepudiation") ?: false,
            keyEncipherment = raw.boolFor("keyEncipherment") ?: false,
            dataEncipherment = raw.boolFor("dataEncipherment") ?: false,
            keyAgreement = raw.boolFor("keyAgreement") ?: false,
            keyCertSign = raw.boolFor("keyCertSign") ?: false,
            crlSign = raw.boolFor("crlSign") ?: false,
            encipherOnly = raw.boolFor("encipherOnly") ?: false,
            decipherOnly = raw.boolFor("decipherOnly") ?: false,
        )
    }

    override fun getValidityPeriod(certificate: NSData): ValidityPeriod {
        val notBefore = (inspector.getValidityNotBeforeEpochSeconds(certificate) as? NSNumber)?.epochInstant()
            ?: error("Certificate is missing notBefore")
        val notAfter = (inspector.getValidityNotAfterEpochSeconds(certificate) as? NSNumber)?.epochInstant()
            ?: error("Certificate is missing notAfter")
        return ValidityPeriod(notBefore, notAfter)
    }

    override fun getCertificatePolicies(certificate: NSData): List<String>? =
        inspector.getCertificatePolicies(certificate)?.mapNotNull { it as? String }

    override fun isSelfSigned(certificate: NSData): Boolean =
        inspector.isSelfSigned(certificate)

    override fun getAiaExtension(certificate: NSData): AuthorityInformationAccess? {
        val raw = inspector.getAuthorityInfoAccess(certificate) ?: return null
        return AuthorityInformationAccess(
            caIssuersUri = raw["caIssuersUri"] as? String,
            ocspUri = raw["ocspUri"] as? String,
        )
    }

    override fun getSubject(certificate: NSData): DistinguishedName? =
        inspector.getSubject(certificate)?.let { DistinguishedName(it.toStringMap()) }

    override fun getIssuer(certificate: NSData): DistinguishedName? =
        inspector.getIssuer(certificate)?.let { DistinguishedName(it.toStringMap()) }

    override fun getSubjectAltNames(certificate: NSData): List<SubjectAlternativeName>? {
        val raw = inspector.getSubjectAltNames(certificate) ?: return null
        return raw.mapNotNull { entry ->
            val dict = entry as? Map<*, *> ?: return@mapNotNull null
            val type = dict["type"] as? String ?: return@mapNotNull null
            when (type) {
                "dnsName" -> (dict["value"] as? String)?.let { SubjectAlternativeName.DNSName(it) }
                "email" -> (dict["value"] as? String)?.let { SubjectAlternativeName.Email(it) }
                "uri" -> (dict["value"] as? String)?.let { SubjectAlternativeName.Uri(it) }
                "ipAddress" -> (dict["value"] as? NSData)?.let { SubjectAlternativeName.IPAddress(it.toByteArray()) }
                "registeredId" -> (dict["value"] as? String)?.let { SubjectAlternativeName.RegisteredId(it) }
                "otherName" -> (dict["value"] as? String)?.let { SubjectAlternativeName.OtherName(it, "") }
                else -> null
            }
        }
    }

    override fun getExtensionCriticality(certificate: NSData): Map<String, Boolean> {
        val raw = inspector.getExtensionCriticality(certificate) ?: return emptyMap()
        return buildMap {
            raw.forEach { (key, value) ->
                val oid = key as? String ?: return@forEach
                put(oid, (value as? NSNumber)?.boolValue ?: false)
            }
        }
    }

    override fun getCrlDistributionPoints(certificate: NSData): List<CrlDistributionPoint> {
        val raw = inspector.getCrlDistributionPoints(certificate) ?: return emptyList()
        return raw.mapNotNull { it as? String }.map { CrlDistributionPoint(distributionPointUri = it) }
    }

    override fun getAuthorityKeyIdentifier(certificate: NSData): AuthorityKeyIdentifier? {
        val raw = inspector.getAuthorityKeyIdentifier(certificate) ?: return null
        val keyId = (raw["keyIdentifier"] as? NSData)?.toByteArray()
        val serial = (raw["authorityCertSerialNumber"] as? NSData)?.toByteArray()
        if (keyId == null && serial == null) return null
        return AuthorityKeyIdentifier(
            keyIdentifier = keyId,
            authorityCertSerialNumber = serial,
        )
    }

    override fun getSubjectKeyIdentifier(certificate: NSData): ByteArray? =
        (inspector.getSubjectKeyIdentifier(certificate) as? NSData)?.toByteArray()

    override fun getSerialNumber(certificate: NSData): SerialNumber {
        val raw = (inspector.getSerialNumber(certificate) as? NSData)
            ?: error("Certificate is missing serial number")
        return SerialNumber(raw.toByteArray())
    }

    override fun getVersion(certificate: NSData): Version =
        Version(inspector.getVersion(certificate).toInt())

    override fun getSubjectPublicKeyInfo(certificate: NSData): PublicKeyInfo {
        val raw = inspector.getPublicKeyInfo(certificate)
            ?: error("Certificate is missing subject public key info")
        return PublicKeyInfo(
            algorithm = raw["algorithm"] as? String ?: "UNKNOWN",
            keySize = raw.intFor("keySize"),
        )
    }

    override fun hasExtension(certificate: NSData, oid: String): Boolean =
        inspector.hasExtension(certificate, oid = oid)
}

private fun NSNumber.epochInstant(): Instant =
    Instant.fromEpochSeconds(doubleValue.toLong())

private fun Map<Any?, *>.boolFor(key: String): Boolean? =
    (this[key] as? NSNumber)?.boolValue

private fun Map<Any?, *>.intFor(key: String): Int? =
    (this[key] as? NSNumber)?.intValue

@Suppress("UNCHECKED_CAST")
private fun Map<Any?, *>.toStringMap(): Map<String, String> =
    buildMap {
        this@toStringMap.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val value = v as? String ?: return@forEach
            put(key, value)
        }
    }
