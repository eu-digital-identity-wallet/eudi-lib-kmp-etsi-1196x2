/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.json.Json
import kotlin.test.Test

class PKIObjectTest {

    @Test
    fun happyPath() {
        val json = """
        {
         "val": "MIIDLTCCAtKgAwIBAgISESEFJUbpBJovlg7lg3Eb5YTCMAoGCCqGSM49BAMCMIGiMQswCQYDVQQGEwJGUjEwMC4GA1UECgwnQWdlbmNlIE5hdGlvbmFsZSBkZXMgVGl0cmVzIFPDqWN1cmlzw6lzMRcwFQYDVQQLDA4wMDAyIDEzMDAwMzI2MjE8MDoGA1UEAwwzQXV0b3JpdMOpIGRlIENlcnRpZmljYXRpb24gRnJhbmNlIEF0dGVzdGF0aW9ucyBJQUNBMQowCAYDVQQFEwExMB4XDTI1MTAxNzAwMDAwMFoXDTM0MTAxNzAwMDAwMFowgaIxCzAJBgNVBAYTAkZSMTAwLgYDVQQKDCdBZ2VuY2UgTmF0aW9uYWxlIGRlcyBUaXRyZXMgU8OpY3VyaXPDqXMxFzAVBgNVBAsMDjAwMDIgMTMwMDAzMjYyMTwwOgYDVQQDDDNBdXRvcml0w6kgZGUgQ2VydGlmaWNhdGlvbiBGcmFuY2UgQXR0ZXN0YXRpb25zIElBQ0ExCjAIBgNVBAUTATEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASa4ZI0w4Mn4FW6kYdKPUlYYgVbwFf1A6lBDnurRsoPJxM3+dVupbkGl9O+QnJ36wc8ngoXE3oH1hP11flDmWsIo4HlMIHiMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMDAGA1UdEgQpMCeBJWZyYW5jZS1hdHRlc3RhdGlvbnNAaW50ZXJpZXVyLmdvdXYuZnIwSgYDVR0fBEMwQTA/oD2gO4Y5aHR0cDovL2NybC5hbnRzLmdvdXYuZnIvYWNfZnJhbmNlX2F0dGVzdGF0aW9uc19pYWNhXzEuY3JsMB0GA1UdDgQWBBT/dscZoX+tou0+F2dDsFrTPfsMpzAfBgNVHSMEGDAWgBT/dscZoX+tou0+F2dDsFrTPfsMpzAKBggqhkjOPQQDAgNJADBGAiEAmMD8Kpgnctmx12gCBYrj98knoKDSPlO5SucThy1EEqwCIQDsYM80Ere4Yw0fHNJQQHl6D1rAITDV3qFKP62Uq7xtsQ=="
        }
        """.trimIndent()

        val pkiOb = Json.decodeFromString<PKIObject>(json)
        val x509Certificate = pkiOb.x509Certificate()
        println(x509Certificate)
    }
}
