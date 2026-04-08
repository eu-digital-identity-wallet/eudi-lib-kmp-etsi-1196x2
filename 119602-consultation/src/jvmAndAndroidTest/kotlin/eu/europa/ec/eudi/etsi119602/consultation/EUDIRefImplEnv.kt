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

import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntities
import eu.europa.ec.eudi.etsi119602.ListOfTrustedEntitiesClaims
import eu.europa.ec.eudi.etsi119602.Uri
import eu.europa.ec.eudi.etsi119602.consultation.eu.EUPIDProvidersList
import eu.europa.ec.eudi.etsi1196x2.consultation.SensitiveApi
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.etsi1196x2.consultation.VerificationContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours

object EUDIRefImplEnv {

    // https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/RegistrarsAndRegisters.json
    // https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PubEAAProviders.json

    private fun String.uri() = Uri.parse(this)
    val LOTE_URL = SupportedLists(
        pidProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/PIDProviders.json".uri(),
        walletProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WalletProviders.json".uri(),
        wrpacProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPACProviders.json".uri(),
        wrprcProviders = "https://trustedlist.serviceproviders.eudiw.dev/LOTE/json/WRPRCProviders.json".uri(),
    )
}

class EUDIRefImplEnvTest {
    // TODO Remove this hack once the LoTE is fixed
    // this is a hack
    val hackedParseJwt = run {
        val jwsJson = ParseJwt.jwsJson<JsonObject, ListOfTrustedEntities>()
        ParseJwt { json ->
            when (val outcome = jwsJson(json)) {
                is ParseJwt.Outcome.Parsed -> {
                    val (h, p) = outcome
                    ParseJwt.Outcome.Parsed(h, ListOfTrustedEntitiesClaims(p))
                }

                is ParseJwt.Outcome.ParseFailed -> outcome
            }
        }
    }

    @Test
    @SensitiveApi
    fun testDownload() = runTest {
        createHttpClient().use { httpClient ->
            val fileStore = LoTEFileStore(
                cacheDirectory = Path(System.getProperty("java.io.tmpdir")!!, "ref-impl-lote"),
            )
            val loadLoTE = LoadSingleLoTEWithFileCache(
                fileStore = fileStore,
                downloadSingleLoTE = DownloadSingleLoTE(httpClient),
                fileCacheExpiration = 24.hours,
                parseJwt = hackedParseJwt,
            )
            // Get the LoTEs, organized them as EUDIW verification contexts
            val provisionTrustAnchors = getTrustAnchorsProvisioner(loadLoTE, parseJwt = hackedParseJwt)
            val isChainTrustedForContext = provisionTrustAnchors.nonCached(EUDIRefImplEnv.LOTE_URL)

            val expectedContexts: List<VerificationContext> =
                listOf(
                    VerificationContext.PID,
                    VerificationContext.PIDStatus,
                    VerificationContext.WalletInstanceAttestation,
                    VerificationContext.WalletUnitAttestation,
                    VerificationContext.WalletUnitAttestationStatus,
                    VerificationContext.WalletRelyingPartyAccessCertificate,
                    VerificationContext.WalletRelyingPartyRegistrationCertificate,
                    VerificationContext.WalletRelyingPartyRegistrationCertificateStatus,
                )

            val actualContexts = isChainTrustedForContext.supportedContexts
            assertContentEquals(expectedContexts.sortedBy { it.toString() }, actualContexts.sortedBy { it.toString() })
            val errors = mutableMapOf<VerificationContext, Throwable>()
            actualContexts.forEach { ctx ->
                try {
                    when (val outcome = isChainTrustedForContext.getTrustAnchors(ctx)) {
                        null -> println("$ctx : Not found")
                        else -> println("$ctx : ${outcome.list.size} ")
                    }
                } catch (e: Exception) {
                    errors[ctx] = e
                }
            }
            if (errors.isNotEmpty()) {
                val es = buildString {
                    appendLine("Errors:")
                    errors.forEach { (ctx, e) ->
                        appendLine("$ctx ")
                        e.suppressed.forEach { appendLine(" - $it") }
                    }
                }
                fail(es)
            }
            fileStore.clear()
        }
    }

    @Test @Ignore
    fun foo() {
        val json = """
            {
              "LoTE": {
                "ListAndSchemeInformation": {
                  "LoTEVersionIdentifier": 1,
                  "LoTESequenceNumber": 1,
                  "SchemeOperatorName": [
                    {
                      "lang": "en",
                      "value": "User Tsp"
                    }
                  ],
                  "ListIssueDateTime": "2026-03-16T16:34:24Z",
                  "NextUpdate": "2026-09-12T16:34:24Z",
                  "LoTEType": "http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList",
                  "SchemeOperatorAddress": {
                    "SchemeOperatorPostalAddress": [
                      {
                        "lang": "en",
                        "StreetAddress": "Test",
                        "Country": "EU",
                        "Locality": "Test",
                        "StateOrProvince": "test",
                        "PostalCode": "teste"
                      }
                    ],
                    "SchemeOperatorElectronicAddress": [
                      {
                        "lang": "en",
                        "uriValue": "teste@teste.com"
                      }
                    ]
                  },
                  "SchemeName": [
                    {
                      "lang": "en",
                      "value": "EU PID Providers List"
                    }
                  ],
                  "SchemeInformationURI": [
                    {
                      "lang": "en",
                      "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/TL/NL/01.xml"
                    }
                  ],
                  "StatusDeterminationApproach": "http://uri.etsi.org/19602/PIDProvidersList/StatusDetn/EU",
                  "SchemeTypeCommunityRules": [
                    {
                      "lang": "en",
                      "uriValue": "http://uri.etsi.org/19602/PIDProviders/schemerules/EU"
                    }
                  ],
                  "SchemeTerritory": "EU",
                  "PolicyOrLegalNotice": [
                    {
                      "LoTEPolicy": {
                        "lang": "en",
                        "uriValue": "Test"
                      }
                    }
                  ],
                  "DistributionPoints": [
                    "https://trustedlist.serviceproviders.eudiw.dev/TL/NL/01.xml"
                  ]
                },
                "TrustedEntitiesList": [
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity CZ"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "CZ",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity CZ"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service CZ"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC0zCCAnmgAwIBAgIUFxoZrqz1jgmJeXu6UxkuRcCgf/AwCgYIKoZIzj0EAwMwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJDWjAeFw0yNTA0MDgyMzU0MTFaFw0zNDA3MDUyMzU0MTBaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIgQ0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCQ1owWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQujURwlrlAeGV/8wYTl9ceasR2YM55vI9cSaZOeXpBu3v6wlEhOHvDLVxlz2zJEJrqvg373DOO0RdWEvkGwf7Zo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBSzozJELVa05Qse3atn1+cKtbbXgDATBgNVHSUEDDAKBggrgQICAAABBzBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX0NaXzAyLmNybDAdBgNVHQ4EFgQUs6MyRC1WtOULHt2rZ9fnCrW214AwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIhAOWHisDphPFySZtS+/1Ufp5aW+Ci3w4aDSw7+EW+TD6mAiAh3/SiF2zzZybp64sG/OiwdhH2LqsizuTD1zFx4oCdqQ=="
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity EE"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "EE",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity EE"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service EE"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC0jCCAnmgAwIBAgIUPP5TRFaC6GrLVVc5T83dCyunbcMwCgYIKoZIzj0EAwMwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFRTAeFw0yNTA0MDkwMDAxMzZaFw0zNDA3MDYwMDAxMzVaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIgQ0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCRUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR+LkqcgTsK8wHwzdAgCtL54yHpe/pfAMF5BuDJ+0SQAl1E+eN2g2BelLKrHwyiiktORwI8tH/52pfJf+PdNcnno4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBQ1uvSpg46wPpr1mUort5On76if2DATBgNVHSUEDDAKBggrgQICAAABBzBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX0VFXzAyLmNybDAdBgNVHQ4EFgQUNbr0qYOOsD6a9ZlKK7eTp++on9gwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDRwAwRAIgN+axEyAC2Z62WkW0eLB5C9vZmqOf8+MNKzoB+uHjK+wCIE5fee6J0rnBkw2ZnFHpX0zxUiuDL9C5sjkwAbVJjmT1"
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity EU"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "EU",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity EU"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service EU"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC0zCCAnmgAwIBAgIUXRXxkLbUM6+njr/XT0IIw/HA/uowCgYIKoZIzj0EAwMwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNTA0MDkwMDAzMzBaFw0zNDA3MDYwMDAzMjlaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIgQ0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCRVUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARkqdLmwIlv+SSWr00tAIrt7EAMztgd3w9qA6qEm16yVfsLcyx2f4oIWuH45wa37J9GoNWpdeo27VoSoNMCzxOYo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTATBgNVHSUEDDAKBggrgQICAAABBzBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUQlBQvhC4EPCdRFyNv6sQCO4n3EkwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIhAIavYfC5o0VVLKfgTKkzzWgc09hzDMsCl3O2le2sQfG7AiA2soqAN5gtUOLQKWK00DUz22EW79rvaV+VJPvfdQeokA=="
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity LU"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "LU",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity LU"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service LU"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC0zCCAnmgAwIBAgIUYGz2Xxw7UFgSmRsIkFTTBclg8fcwCgYIKoZIzj0EAwMwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJMVTAeFw0yNTA0MDkwMDA1MzlaFw0zNDA3MDYwMDA1MzhaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIgQ0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCTFUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR6X1NlEGbdWUWiUrSA/YiNpcZeI95z2MglbvISRO19YUc4GvPTevbg/Fm9MekJeHqRQO4GHTlBPNGM2aiBtu5Mo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBRcYvvRpiZ6Zz81VxQ4+AdY4Iez8DATBgNVHSUEDDAKBggrgQICAAABBzBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX0xVXzAyLmNybDAdBgNVHQ4EFgQUXGL70aYmemc/NVcUOPgHWOCHs/AwDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDSAAwRQIgd69HgNvnIVbHg5lY2SzgExy72DUNCyi20An6OGNqWw4CIQDkDMDTmPd6p/aHAtYP8Jh7z/4Nb/09LxpNXQS72ouixA=="
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity NL"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "NL",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity NL"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service NL"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC0jCCAnmgAwIBAgIUWcI7dH8iTcdDsGe93t9tWfdoKr0wCgYIKoZIzj0EAwMwVzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxsZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJOTDAeFw0yNTA0MDkwMDA3MjZaFw0zNDA3MDYwMDA3MjVaMFcxGTAXBgNVBAMMEFBJRCBJc3N1ZXIgQ0EgMDIxLTArBgNVBAoMJEVVREkgV2FsbGV0IFJlZmVyZW5jZSBJbXBsZW1lbnRhdGlvbjELMAkGA1UEBhMCTkwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT0h0ljqQJKWwHW8sHJX3psnuCJBt/z82bO9yiy8z9CxLLXUNJj4vi8ox7itb5nXTaSlL02ChMl8GgXLl3lg1A3o4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBTMrdmCj0nOwclVaiDpYcnwxnCY7jATBgNVHSUEDDAKBggrgQICAAABBzBDBgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlkX0NBX05MXzAyLmNybDAdBgNVHQ4EFgQUzK3Zgo9JzsHJVWog6WHJ8MZwmO4wDgYDVR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1kaWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5jZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDRwAwRAIgd24eUv61oXeE2tZQ/WRe28t4Q575ktymDiHPzwj5UCQCIGoSN5ntiPEa4dk8P48blwgXY74+1svzmTVTWYtVmCsv"
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity PT"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "PT",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity PT"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service PT"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC3jCCAoOgAwIBAgIUZXIN5wL8XzOB1WtwPqAr6GSIm2MwCgYIKoZIzj0EAwMwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFBUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlBUMB4XDTI1MDQwODE5MzU0NFoXDTM0MDcwNTE5MzU0M1owXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFBUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlBUMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQctevxuugp0BBrsKpxBUJfoF4t/vRgxYFh2VzklNZisO7aihVhdiXyvOLdZJZk7H4nbJltmhG3P+Wjb2QY8Mp6OCASEwggEdMBIGA1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0jBBgwFoAU/Fq6x2Mh0FTKT5R5feWq08S+mJEwEwYDVR0lBAwwCgYIK4ECAgAAAQcwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9QVF8wMi5jcmwwHQYDVR0OBBYEFPxausdjIdBUyk+UeX3lqtPEvpiRMA4GA1UdDwEB/wQEAwIBBjBdBgNVHRIEVjBUhlJodHRwczovL2dpdGh1Yi5jb20vZXUtZGlnaXRhbC1pZGVudGl0eS13YWxsZXQvYXJjaGl0ZWN0dXJlLWFuZC1yZWZlcmVuY2UtZnJhbWV3b3JrMAoGCCqGSM49BAMDA0kAMEYCIQCsH7B1TjFfNI2mr3zDy2TCboDgcgiQ/Xzh5ZNfyeT3/gIhAK7frwdyKHyhe1ruHEhK6bZ/eovd4pE/w+WDPBOqYcIM"
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  },
                  {
                    "TrustedEntityInformation": {
                      "TEName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity UT"
                        }
                      ],
                      "TEAddress": {
                        "TEPostalAddress": [
                          {
                            "lang": "en",
                            "StreetAddress": "Test",
                            "Country": "UT",
                            "Locality": "Test",
                            "StateOrProvince": "Test",
                            "PostalCode": "Test"
                          }
                        ],
                        "TEElectronicAddress": [
                          {
                            "lang": "en",
                            "uriValue": "test@test.com"
                          }
                        ]
                      },
                      "TEInformationURI": [
                        {
                          "lang": "en",
                          "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                        }
                      ],
                      "TETradeName": [
                        {
                          "lang": "en",
                          "value": "Trusted Entity UT"
                        }
                      ]
                    },
                    "TrustedEntityServices": [
                      {
                        "ServiceInformation": {
                          "ServiceName": [
                            {
                              "lang": "en",
                              "value": "Service UT"
                            }
                          ],
                          "ServiceDigitalIdentity": {
                            "X509Certificates": [
                              {
                                "val": "MIIC3TCCAoOgAwIBAgIUEwybFc9Jw+az3r188OiHDaxCfHEwCgYIKoZIzj0EAwMwXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI1MDMyNDIwMjYxNFoXDTM0MDYyMDIwMjYxM1owXDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEesDKj9rCIcrGj0wbSXYvCV953bOPSYLZH5TNmhTz2xa7VdlvQgQeGZRg1PrF5AFwt070wvL9qr1DUDdvLp6a1qOCASEwggEdMBIGA1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0jBBgwFoAUYseURyi9D6IWIKeawkmURPEB08cwEwYDVR0lBAwwCgYIK4ECAgAAAQcwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9VVF8wMi5jcmwwHQYDVR0OBBYEFGLHlEcovQ+iFiCnmsJJlETxAdPHMA4GA1UdDwEB/wQEAwIBBjBdBgNVHRIEVjBUhlJodHRwczovL2dpdGh1Yi5jb20vZXUtZGlnaXRhbC1pZGVudGl0eS13YWxsZXQvYXJjaGl0ZWN0dXJlLWFuZC1yZWZlcmVuY2UtZnJhbWV3b3JrMAoGCCqGSM49BAMDA0gAMEUCIQCe4R9rO4JhFp821kO8Gkb8rXm4qGG/e5/Oi2XmnTQqOQIgfFs+LDbnP2/j1MB4rwZ1FgGdpr4oyrFB9daZyRIcP90="
                              }
                            ]
                          },
                          "SchemeServiceDefinitionURI": [
                            {
                              "lang": "en",
                              "uriValue": "https://trustedlist.serviceproviders.eudiw.dev/"
                            }
                          ]
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val lote = JsonSupportDebug.decodeFromString<ListOfTrustedEntitiesClaims>(json).listOfTrustedEntities

        with(EUPIDProvidersList) {
            lote.ensureCompliesToProfile()
        }
    }
}
