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

import eu.europa.ec.eudi.etsi119602.datamodel.ServiceDigitalIdentity
import eu.europa.ec.eudi.etsi1196x2.consultation.ByteArrayKey
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrust
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingDirectTrustIos
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIX
import eu.europa.ec.eudi.etsi1196x2.consultation.ValidateCertificateChainUsingPKIXIos
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import eu.europa.ec.eudi.etsi1196x2.consultation.certs.CertificateProfileValidatorIos
import eu.europa.ec.eudi.etsi1196x2.consultation.toNSData
import platform.Foundation.NSData

/**
 * Default conversion of a [ServiceDigitalIdentity] into iOS trust anchors.
 * Each [eu.europa.ec.eudi.etsi119602.PKIObject.value] is already the raw DER (base64 is decoded
 * during JSON parsing), so it is wrapped directly in an [NSData].
 */
public fun defaultCreateTrustAnchorsIos(
    serviceDigitalIdentity: ServiceDigitalIdentity,
): List<NSData> =
    serviceDigitalIdentity.x509Certificates.orEmpty().map { it.value.toNSData() }

/**
 * iOS factory for [ProvisionTrustAnchorsFromLoTEs] wired with concrete iOS types, mirroring
 * [ProvisionTrustAnchorsFromLoTEs.Companion.eudiwJvm]. Certificates flow as DER [NSData];
 * validation is backed by Apple's Security.framework via the PKIXBridge.
 *
 * @param loadLoTEAndPointers loader for LoTE documents and their pointers
 * @param svcTypePerCtx mapping of verification contexts to LoTE service types (default: EU)
 * @param continueOnProblem strategy for LoTE-loading problems (default: never)
 * @param directTrust direct-trust validator (default: DER byte comparison)
 * @param pkix PKIX validator (default: Security.framework, revocation disabled)
 */
public fun ProvisionTrustAnchorsFromLoTEs.Companion.eudiwIos(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<VerificationContext>> = SupportedLists.eu(),
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData, ByteArrayKey> =
        ValidateCertificateChainUsingDirectTrustIos,
    pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
        ValidateCertificateChainUsingPKIXIos(),
): ProvisionTrustAnchorsFromLoTEs<List<NSData>, VerificationContext, NSData, NSData> =
    ios(
        loadLoTEAndPointers,
        svcTypePerCtx,
        ::defaultCreateTrustAnchorsIos,
        continueOnProblem,
        directTrust,
        pkix,
    )

/**
 * Generic iOS factory for [ProvisionTrustAnchorsFromLoTEs] over an arbitrary context type [CTX],
 * mirroring [ProvisionTrustAnchorsFromLoTEs.Companion.jvm].
 */
public fun <CTX : Any> ProvisionTrustAnchorsFromLoTEs.Companion.ios(
    loadLoTEAndPointers: LoadLoTEAndPointers,
    svcTypePerCtx: SupportedLists<LotEMeta<CTX>>,
    createTrustAnchors: (ServiceDigitalIdentity) -> List<NSData> = ::defaultCreateTrustAnchorsIos,
    continueOnProblem: ContinueOnProblem = ContinueOnProblem.Never,
    directTrust: ValidateCertificateChainUsingDirectTrust<List<NSData>, NSData, ByteArrayKey> =
        ValidateCertificateChainUsingDirectTrustIos,
    pkix: ValidateCertificateChainUsingPKIX<List<NSData>, NSData> =
        ValidateCertificateChainUsingPKIXIos(),
): ProvisionTrustAnchorsFromLoTEs<List<NSData>, CTX, NSData, NSData> =
    ProvisionTrustAnchorsFromLoTEs(
        loadLoTEAndPointers,
        svcTypePerCtx,
        createTrustAnchors,
        continueOnProblem = continueOnProblem,
        directTrust = directTrust,
        pkix = pkix,
        certificateProfileValidator = CertificateProfileValidatorIos(),
        endEntityCertificateOf = { checkNotNull(it.firstOrNull()) { "Chain cannot be empty" } },
    )
