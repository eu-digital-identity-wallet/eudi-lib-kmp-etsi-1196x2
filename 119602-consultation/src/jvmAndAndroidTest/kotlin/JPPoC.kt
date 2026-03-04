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
import JPPoC.LC_VCT
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.eudi.etsi119602.consultation.*
import eu.europa.ec.eudi.etsi119602.x509Certificate
import eu.europa.ec.eudi.etsi1196x2.consultation.*
import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.vc.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import kotlin.test.Test

object JPPoC {

    //
    // The PoC uses two LoTE, which in the EUDIW context are mapped to
    // - WRP Access Certificate Providers &
    // - Learning Credential Attestation Providers (an EAA provider use case)
    //
    private const val JP_WRPAC_PROVIDER_ISSUANCE_SVC_TYPE =
        "http://tl.eujp.ownd-project.com/19602/SvcType/WRPAC/Issuance"
    private const val LC_EAA_PROVIDER_SVC_TYPE =
        "http://tl.eujp.ownd-project.com/19602/SvcType/EAA/Issuance"
    private const val LC_USE_CASE = "jp-learning-credential-poc"
    private val wrpacProviders: LotEMata<VerificationContext, X509Certificate> =
        LotEMata(
            svcTypePerCtx = mapOf(
                VerificationContext.WalletRelyingPartyAccessCertificate to JP_WRPAC_PROVIDER_ISSUANCE_SVC_TYPE,
            ),
            directTrust = false,
        )

    private val learningCredentialProviders: LotEMata<VerificationContext, X509Certificate> = LotEMata(
        svcTypePerCtx =
        mapOf(
            VerificationContext.EAA(LC_USE_CASE) to LC_EAA_PROVIDER_SVC_TYPE,
        ),
        directTrust = true,
    )
    val SVC_TYPE_PER_CTX: SupportedLists<LotEMata<VerificationContext, X509Certificate>> =
        SupportedLists(
            wrpacProviders = wrpacProviders,
            eaaProviders = mapOf(LC_USE_CASE to learningCredentialProviders),
        )

    //
    // Learning credential is classified as EAA
    // Supporting SD-JWT-VC format
    //
    const val LC_VCT = "urn:eu.europa.ec.eudi:learning:credential:1"
    private val learningCredentialsInSdJwtVc =
        AttestationIdentifierPredicate.equalsTo(AttestationIdentifier.SDJwtVc(LC_VCT))

    val attestationClassifications = AttestationClassifications(
        eaAs = mapOf(LC_USE_CASE to learningCredentialsInSdJwtVc),
    )

    //
    // Runtime
    //
    val loteLocations = SupportedLists(
        wrpacProviders = "https://tl.eujp.ownd-project.com/trusted-list/jpwrpac_providers_list.jwt",
        eaaProviders = mapOf(
            LC_USE_CASE to "https://tl.eujp.ownd-project.com/trusted-list/jpeaa_providers_list.jwt",
        ),
    )
}

class JPLoTEDownloaderTest {

    @Test
    fun testDownload() = runTest {
        // Get the LoTEs, organized them as EUDIW verification contexts
        val isChainTrustedForContext =
            createHttpClient().use { httpClient ->
                httpClient.getThem(JPPoC.loteLocations, JPPoC.SVC_TYPE_PER_CTX)
            }

        // Establish a chain verifier for attestation
        val isChainTrustedForAttestation =
            IsChainTrustedForAttestation(isChainTrustedForContext, JPPoC.attestationClassifications)

        // Define SD-JWT-VC typed metadata policy
        val typedMetadataPolicy = TypeMetadataPolicy.RequiredFor(
            vcts = setOf(Vct(LC_VCT)),
            resolveTypeMetadata = ResolveTypeMetadata({ vct, _ ->
                runCatching {
                    createHttpClient().use { httpClient ->
                        val url = Url("https://dev.issuer-backend.eudiw.dev/type-metadata/$vct")
                        httpClient.get(url).body<SdJwtVcTypeMetadata>()
                    }
                }
            }),
        )

        // Create an SD-JWT-VC verifier
        val verifier =
            IntegrationWithSdJwtVc.nimbusSdJwtVcVerifier(isChainTrustedForAttestation, typedMetadataPolicy)

        //
        // Verify
        //

        val credential =
            """eyJ0eXAiOiJkYytzZC1qd3QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlGZFRDQ0JGMmdBd0lCQWdJTVBNZjhjdmtPQTNoSVJ3WGhNQTBHQ1NxR1NJYjNEUUVCREFVQU1Gd3hDekFKQmdOVkJBWVRBa0pGTVJrd0Z3WURWUVFLRXhCSGJHOWlZV3hUYVdkdUlHNTJMWE5oTVRJd01BWURWUVFERXlsSGJHOWlZV3hUYVdkdUlFZERReUJTTkRVZ1FVRlVUQ0JEUVNBeU1ESXdJQzBnVTNSaFoybHVaekFlRncweU5URXhNVEF5TXpRNU1qWmFGdzB5TmpFeE1URXlNelE1TWpaYU1JRzBNUXN3Q1FZRFZRUUdFd0pLVURFT01Bd0dBMVVFQ0JNRlZHOXJlVzh4RlRBVEJnTlZCQWNUREZOb2FXNWhaMkYzWVMxcmRURWJNQmtHQTFVRUJSTVNRVUZVVERJd01qVXhNVEV3TURFMU1UYzFNUmN3RlFZRFZRUUxFdzVOWVhKclpYUnBibWNnUkdWd2RERWpNQ0VHQTFVRUNoTWFRM2xpWlhJZ1UyVmpkWEpwZEhrZ1EyeHZkV1FzSUVsdVl5NHhJekFoQmdOVkJBTVRHa041WW1WeUlGTmxZM1Z5YVhSNUlFTnNiM1ZrTENCSmJtTXVNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUU5Qk5iWlk0am0wbktqMGUrQWRMa015VGtSa21JZXNFa3dqZkhwZVplK3dSWDFaSmdtRVN2VkUvZFRzd0JyVVYrMzJNRllGYmh6Q3VjeXVkS3Fidll0YU9DQXFjd2dnS2pNQTRHQTFVZER3RUIvd1FFQXdJRHlEQ0Jvd1lJS3dZQkJRVUhBUUVFZ1pZd2daTXdUZ1lJS3dZQkJRVUhNQUtHUW1oMGRIQTZMeTl6WldOMWNtVXVjM1JoWjJsdVp5NW5iRzlpWVd4emFXZHVMbU52YlM5allXTmxjblF2WjNOblkyTnlORFZoWVhSc1kyRXlNREl3TG1OeWREQkJCZ2dyQmdFRkJRY3dBWVkxYUhSMGNEb3ZMMjlqYzNBdWMzUmhaMmx1Wnk1bmJHOWlZV3h6YVdkdUxtTnZiUzluYzJkalkzSTBOV0ZoZEd4allUSXdNakF3Z2RvR0ExVWRJQVNCMGpDQnp6Q0J6QVlLS3dZQkJBR2dNZ0VvSGpDQnZUQ0JoZ1lJS3dZQkJRVUhBZ0l3ZWd4NFZHaHBjeUJqWlhKMGFXWnBZMkYwWlNCb1lYTWdZbVZsYmlCcGMzTjFaV1FnYVc0Z1lXTmpiM0prWVc1alpTQjNhWFJvSUhSb1pTQkhiRzlpWVd4VGFXZHVJRU5RVXlCc2IyTmhkR1ZrSUdGMElHaDBkSEJ6T2k4dmQzZDNMbWRzYjJKaGJITnBaMjR1WTI5dEwzSmxjRzl6YVhSdmNua3ZNRElHQ0NzR0FRVUZCd0lCRmlab2RIUndjem92TDNkM2R5NW5iRzlpWVd4emFXZHVMbU52YlM5eVpYQnZjMmwwYjNKNUx6QUpCZ05WSFJNRUFqQUFNRWtHQTFVZEh3UkNNRUF3UHFBOG9EcUdPR2gwZEhBNkx5OWpjbXd1YzNSaFoybHVaeTVuYkc5aVlXeHphV2R1TG1OdmJTOW5jMmRqWTNJME5XRmhkR3hqWVRJd01qQXVZM0pzTUdJR0NpcUdTSWIzTHdFQkNRRUVWREJTQWdFQmhrcG9kSFJ3T2k4dmNqUTFZV0YwYkMxMGFXMWxjM1JoYlhBdWMzUmhaMmx1Wnk1bmJHOWlZV3h6YVdkdUxtTnZiUzkwYzJFdmQyUnlOSFkwWVhjeWVUSTFNRzFwT1dZMU4zZGxid0VCQURBVUJnTlZIU1VFRFRBTEJna3Foa2lHOXk4QkFRVXdId1lEVlIwakJCZ3dGb0FVdEhTSTFDa2hxeHZvcDZpdEM5SExyaWR5MFlBd0hRWURWUjBPQkJZRUZFLzQrdzV4N0JmWU1JOHZQTXhxZDZ6RkE3djhNQTBHQ1NxR1NJYjNEUUVCREFVQUE0SUJBUUE4RVhJWmp1T3R2bC9jSWcvb0cwNlpYUzhQcFY1Wk9jTVhQMkVwdkQ3Y2ZiZTdZS0tjdm1EWnNaQmI2TVlwT1N4QUJPWlBDVzJmdlIrUEkzWksyeUdFcTlxY1JKdFpNWjdyUlRlY0hXcmtpcGM1L0dIU1BMM2JmcTBWSDR4RTNaSDUzd3paM1dBYzRSK3c4ZFNyZ2ZuZTBqVSt2eWt1bTVGVDdCL3NjSFhsaDc5T2kxTXhuTWVZajVVL2kwS0Y0ekFRY2YzcjZSd1kwZGx1UWlMdzQzV3doYkQzUjNjSkp1emgyc1lvR0hIZ2V2L1pJNk92SXgySUpJT0ltZ2gxU0l1MGRVYkcxcDJNMGdiYXN2Y2Jwak1FOTB6OXJaL0FiNS8yY2VsTW1lSm4zakN5ODFSUDZnYmt1bnI2MlowdE91cnQ4ZUNqaFFFQTV3UzM5VkxEMnExMyJdfQ.eyJpc3N1aW5nX2F1dGhvcml0eSI6IlRFU1RfIElzc3VpbmdfQXV0aG9yaXR5IiwiaXNzdWluZ19jb3VudHJ5IjoiSlAiLCJkYXRlX29mX2lzc3VhbmNlIjoiMjAyNi0wMi0xNiIsImFjaGlldmVtZW50X3RpdGxlIjoiVEVTVF8gQ3JlZGVudGlhbF9UaXRsZSIsImxhbmd1YWdlX29mX2NsYXNzZXMiOlsiamEiLCJlbiJdLCJkYXRlX29mX2V4cGlyeSI6IjIwMjYtMDMtMzEiLCJhY2hpZXZlbWVudF9kZXNjcmlwdGlvbiI6IlRFU1QgQWNoaWV2ZW1lbnQgRGVzY3JpcHRpb24iLCJjbmYiOnsiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJ4IjoiRzFSbktOVWxRaF9jRnk5YmFqeDZvdDJrSUV3VG42cVd5cjQyN0IyYk5uWSIsInkiOiJ2VG9iTFAyYm52VU1IN3A1azdoODRLYXBsZ0dhbTlOeGJuTWhadHRiUzUwIn19LCJ2Y3QiOiJ1cm46ZXUuZXVyb3BhLmVjLmV1ZGk6bGVhcm5pbmc6Y3JlZGVudGlhbDoxIiwiaXNzIjoiaHR0cHM6Ly90ZXN0Lmlzc3Vlci5jb20vIiwiaWF0IjoxNzcxNDE1MTc5LCJleHAiOjE4MDI5NTExNzksIl9zZCI6WyJLOGVUQnRXbjZpNm80LUc2OW1FaWFsUWxNUHVuRmQ1WHZLei1TbnR6R0hvIiwiUWdtaHhnbHlfanZNWHhrMEw1eTdFN2JYRFFIN3ZIVERJYXpFMUlQLVNWWSIsIkh6QUM5eGlGZURtSlk2akpLdERQOHRnVXM1QjlQaHZiYVphZ1RwbGdGWFkiLCJyQkt6VjgyTWxCTjgzOGdtZXNNb3lZWnRpQmsxX2tsMFFkNVR5UERnTUVnIiwiSnVybmhlb3NRcVVZejFXckdSa3VCT0EydTA5WXRyYmM0MkFNb2dMZ2I0byIsIm0yQnVuX00tdXVLbDRRbkkxOHJZS00wZHJyZ19KdDlqSDVlWWJPT2kwT0EiLCIxekZ0T0ZFTHg4QzdPY2lOVjNtTFROTERnQjUwNlp5aldtcy1EUS1oWk1vIiwiWFFmYmZnVnFtaVlDRnNOcV8yM1hNang5bXAxNS0zeWRmX2dzUjJsUWluRSIsImFkS3Nidng3T1hmTGFHVVJnS3NiTUZuUUowQlhOSTVVcDJoaFlmOXNhLXMiLCJqSW5iTGxkeEh4TEZETXFOcFpZUXN0TndOb0FPNk1OM3IxVXNjSGtQUzBVIl19.rE8g5c_MK7oQwWJiQ1C-1tsXx2EfLNCHunMCDN2O9kdCrQtKk1rqaAvqKx1-HHY5DQJrfkNGbEE2XBB2qdN2og~WyJoUDZRTkN2Y0doVTk4eFpYIiwiZmFtaWx5X25hbWUiLCJURVNUXyBGYW1pbHlfTmFtZSJd~WyJ3elI5UjR2VkswR3B0RkM2IiwibGVhcm5lcl9pZGVudGlmaWNhdGlvbiIsIlRFU1RfSURfRm9yX0Nvbm5lY3Rpb24iXQ~WyJkd3VxY2Q5YjZEdnpSeTBBIiwiZXhwZWN0ZWRfc3R1ZHlfdGltZSIsIlRFU1RfRXhwZWN0ZWRfU3R1ZHlfVGltZSJd~WyJObk1lV0lpRzhraENrbDBkIiwibGV2ZWxfb2ZfbGVhcm5pbmdfZXhwZXJpZW5jZSIsMV0~WyI0ZDFEU1BsWWV1RjVxVERMIiwidHlwZXNfb2ZfcXVhbGl0eV9hc3N1cmFuY2UiLFsiVEVTVF8gVHlwZXNfb2ZfUXVhbGl0eV9Bc3N1cmFuY2UiXV0~WyJnSWhMMmRacjlUaGV5UVJHIiwiZ2l2ZW5fbmFtZSIsbnVsbF0~WyJ2aUtXa1hUb2lJU3dsOVdFIiwibGVhcm5pbmdfb3V0Y29tZXMiLFsiVGVzdCBMZWFybmluZyBPdXRjb21lczEiLCJUZXN044CATGVhcm5pbmcgT3V0Y29tZXMyIl1d~WyJJdGxuNzg5Ylh5NHdEOTJpIiwiYXNzZXNzbWVudF9ncmFkZSIsIlRFU1RfIEFzc2Vzc21lbnRfR3JhZGUiXQ~WyJ5T0lYQUZhN2ZOUFlUbnRmIiwicHJlcmVxdWlzaXRlc190b19lbnJvbGwiLFsiVEVTVF9QcmVyZXF1aXNpdGVzX3RvX0Vucm9sbCJdXQ~WyJhN1RuSzBBOUhDendkUnJoIiwiaW50ZWdyYXRpb25fc3RhY2thYmlsaXR5X29wdGlvbnMiLDFd~""".trimIndent()

        verifier.verify(credential).getOrThrow().also { println(it) }
    }

    private suspend fun HttpClient.getThem(
        loteLocationsSupported: SupportedLists<String>,
        svcTypePerCtx: SupportedLists<LotEMata<VerificationContext, X509Certificate>>,
        provider: String? = null,
    ): ComposeChainTrust<List<X509Certificate>, VerificationContext, TrustAnchor> {
        val fromHttp = ProvisionTrustAnchorsFromLoTEs(
            loadLoTEAndPointers = LoadLoTEAndPointers(
                constraints = LoadLoTEAndPointers.Constraints(
                    otherLoTEParallelism = 1,
                    maxDepth = 1,
                    maxLists = 2,
                ),
                verifyJwtSignature = NotValidating,
                loadLoTE = LoadLoTEFromHttp(this),

            ),
            svcTypePerCtx = svcTypePerCtx,
            extractCertificate = { it.trustedCert },
            continueOnProblem = ContinueOnProblem.AlwaysIfDownloaded,
            createTrustAnchors = { serviceDigitalIdentity ->
                serviceDigitalIdentity.x509Certificates.orEmpty().map { TrustAnchor(it.x509Certificate(provider), null) }
            },
            directTrust = ValidateCertificateChainUsingDirectTrustJvm,
            pkix = ValidateCertificateChainUsingPKIXJvm(customization = { isRevocationEnabled = false }),
        )
        return fromHttp(loteLocationsSupported, parallelism = 2)
    }
}

object IntegrationWithSdJwtVc {
    fun <CHAIN : Any> IsChainTrustedForAttestation<CHAIN, *>.sdJwtVcIssuerTrust(): X509CertificateTrust<CHAIN> =
        X509CertificateTrust { chain, claims ->
            val vct =
                claims[SdJwtVcSpec.VCT]
                    ?.takeIf { it is JsonPrimitive && it.jsonPrimitive.isString }
                    ?.jsonPrimitive?.contentOrNull
                    ?.let { AttestationIdentifier.SDJwtVc(it) }

            val validation = vct?.let { issuance(chain, it) }
            when (validation) {
                is CertificationChainValidation.NotTrusted -> false
                is CertificationChainValidation.Trusted<*> -> {
                    if (validation.trustAnchor is TrustAnchor) {
                        println("SD-JWT-VC signed by: ${(validation.trustAnchor as TrustAnchor).trustedCert.subjectX500Principal}")
                    }
                    true
                }
                null -> false
            }
        }

    fun <CHAIN : Any, TRUST_ANCHOR : Any> sdJwtVcVerificationMethod(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<CHAIN, TRUST_ANCHOR>,
    ): IssuerVerificationMethod.UsingX5c<CHAIN> =
        IssuerVerificationMethod.usingX5c(
            x509CertificateTrust = isChainTrustedForAttestation.sdJwtVcIssuerTrust(),
        )

    fun defaultSdJwtVcVerifier(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<List<X509Certificate>, TrustAnchor>,
        typedMetadataPolicy: TypeMetadataPolicy,
    ): SdJwtVcVerifier<JwtAndClaims> =
        DefaultSdJwtOps.SdJwtVcVerifier(
            sdJwtVcVerificationMethod(isChainTrustedForAttestation),
            typedMetadataPolicy,
        )

    fun nimbusSdJwtVcVerifier(
        isChainTrustedForAttestation: IsChainTrustedForAttestation<List<X509Certificate>, TrustAnchor>,
        typedMetadataPolicy: TypeMetadataPolicy,
    ): SdJwtVcVerifier<SignedJWT> =
        NimbusSdJwtOps.SdJwtVcVerifier(
            sdJwtVcVerificationMethod(isChainTrustedForAttestation),
            typedMetadataPolicy,
        )
}
