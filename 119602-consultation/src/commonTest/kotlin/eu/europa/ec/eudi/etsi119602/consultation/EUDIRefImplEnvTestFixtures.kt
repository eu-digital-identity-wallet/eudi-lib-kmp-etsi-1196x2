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

object EUDIRefImplEnvTestFixtures {

    /**
     * This is not a compliant certificate
     */
    val pidProviderSigningCertificateNotCompliant =
        """
            -----BEGIN CERTIFICATE-----
            MIIDADCCAqWgAwIBAgIUET5T0Zf8eiEGSYn1U4xFBedGXsswCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MTQxMDM1MjVaFw0yODA1MTMxMDM1MjRaMFcxHjAcBgNVBAMMFUtvdGxpbiBQSUQg
            SXNzdWVyIERFVjEYMBYGA1UEYQwPTEVJRVUtMTIzNDU2Nzg5MQ4wDAYDVQQKDAVO
            aXNjeTELMAkGA1UEBhMCRVUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ9e6zs
            PHWFwl+GJo6/TX2OfHgTtYpb+owtb4ssILvOZFwLFW6UPd5YidcOYDNH/r4fB+50
            4igzpFHhn2na7GEMo4IBTTCCAUkwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBRC
            UFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUHMAKG
            PWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJDQTAy
            LUVVLmNhY2VydC5wZW0wLgYDVR0gBCcwJTAjBgMqAwQwHDAaBggrBgEFBQcCARYO
            ZXhhbXBsZS5wb2xpY3kwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9k
            LnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYEFCII
            oL5fiFXGYu65C0oT0w2FHWmkMA4GA1UdDwEB/wQEAwIHgDAZBggrBgEFBQcBAwQN
            MAswCQYHBACL7E4BATAKBggqhkjOPQQDAgNJADBGAiEAymfJqRvn3bqToZEMxrku
            PMIZjiKt9xplNamzAoTpv0kCIQCdzmncNLr51HhgGRZiPj7nolhJNoonoH6TkmZu
            pJXe3A==
            -----END CERTIFICATE-----
        """.trimIndent()

    /**
     * This is not a compliant certificate
     */
    val walletProviderSigningCertificateNotCompliant =
        """
            -----BEGIN CERTIFICATE-----
            MIIDAjCCAqigAwIBAgIUWclZqMVuu3Er5tgW7exeSm1ibAkwCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDcxMDQ2MDVaFw0yODA1MDYxMDQ2MDRaMFoxCzAJBgNVBAYTAkVVMQ4wDAYDVQQK
            DAVOaXNjeTEhMB8GA1UEAwwYV2FsbGV0IFByb3ZpZGVyIERFViBFWCAyMRgwFgYD
            VQRhDA9MRUlFVS0xMjM0NTY3ODkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQm
            +N+6Cj8/4B59z1Fw/+iLXb+AG2BKIsBWMG1UZNJ7rdcxrXuaGEsVHWZL2vXBUGdx
            E8gr5Kkc6725Eh+spPyho4IBTTCCAUkwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW
            gBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUH
            MAKGPWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJD
            QTAyLUVVLmNhY2VydC5wZW0wLgYDVR0gBCcwJTAjBgMqAwQwHDAaBggrBgEFBQcC
            ARYOZXhhbXBsZS5wb2xpY3kwQwYDVR0fBDwwOjA4oDagNIYyaHR0cHM6Ly9wcmVw
            cm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9DQV9FVV8wMi5jcmwwHQYDVR0OBBYE
            FPlYls0Eintao0UtQeopc5Cs+EhIMA4GA1UdDwEB/wQEAwIHgDAZBggrBgEFBQcB
            AwQNMAswCQYHBACL7E4BAjAKBggqhkjOPQQDAgNIADBFAiB89GW6rJmzKDi/AbLG
            JLfFee9FJntiQAT4Qh6rnuAhigIhAPhddtIl9ZpNxoVT0deASmgzeTv6lv6aRpAB
            xoZ/gbin
            -----END CERTIFICATE-----
        """.trimIndent()

    val issuerAccessCertificate =
        """
            -----BEGIN CERTIFICATE-----
            MIIDAzCCAqqgAwIBAgIURqZMwltm47FnrUuswJZawUAjTtEwCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDcxMzM3MzBaFw0yODA1MDYxMzM3MjlaMFoxITAfBgNVBAMMGEtvdGxpbiBJc3N1
            ZXIgU2lnbmVyIERldjELMAkGA1UEBhMCRVUxDjAMBgNVBAoMBU5pc2N5MRgwFgYD
            VQRhDA9MRUlFVS0xMjM0NTY3ODkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQD
            sy1vqh8TI7SUYY7OyZ0Tn08TaZPn+Zdw5BilTEVzXc6SSu0gAFkcaNKunRZB4JAk
            luQ5YKi6DRPa3s8fcYGWo4IBTzCCAUswDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW
            gBRCUFC+ELgQ8J1EXI2/qxAI7ifcSTBZBggrBgEFBQcBAQRNMEswSQYIKwYBBQUH
            MAKGPWh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2FpYS9QSURJc3N1ZXJD
            QTAyLUVVLmNhY2VydC5wZW0wNQYDVR0RBC4wLIYqaHR0cHM6Ly9kZXYua290bGlu
            SXNzdWVyU2lnbmVyLmNvbS9zdXBwb3J0MBQGA1UdIAQNMAswCQYHBACL7EYBAjBD
            BgNVHR8EPDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9j
            cmwvcGlkX0NBX0VVXzAyLmNybDAdBgNVHQ4EFgQUwu8/c7hdHHi6rGE75pg3f4Yf
            JSswDgYDVR0PAQH/BAQDAgeAMAoGCCqGSM49BAMCA0cAMEQCIDjAxHdaaRIc1CG3
            oqbvYRbzIbMHoqNh2EUfLjLfsezLAiBPVXyUJQyJ/rE43aVgjB4tX5h8oAuQNEBS
            G9WdPfYDrg==
            -----END CERTIFICATE-----
        """.trimIndent()

    val verifierAccessCertificate =
        """
            -----BEGIN CERTIFICATE-----
            MIIC/TCCAqKgAwIBAgIUK/6I3nrQOiMq/aIqMF7D7vv+xA4wCgYIKoZIzj0EAwIw
            VzEZMBcGA1UEAwwQUElEIElzc3VlciBDQSAwMjEtMCsGA1UECgwkRVVESSBXYWxs
            ZXQgUmVmZXJlbmNlIEltcGxlbWVudGF0aW9uMQswCQYDVQQGEwJFVTAeFw0yNjA1
            MDcxMzM4MzhaFw0yODA1MDYxMzM4MzdaMFUxHDAaBgNVBAMME1ZlcmlmaWVyIFNp
            Z25lciBkZXYxCzAJBgNVBAYTAkVVMQ4wDAYDVQQKDAVOaXNjeTEYMBYGA1UEYQwP
            TEVJRVUtMTIzNDU2Nzg5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvZPdm4oz
            0rYYexoyJSYU5YG0ZBMTUQRzSVZjo2y0gZYU2jpxwb8/Rk1Aeb2rcc98CfJONqky
            a9p/wae5k7fChaOCAUwwggFIMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUQlBQ
            vhC4EPCdRFyNv6sQCO4n3EkwWQYIKwYBBQUHAQEETTBLMEkGCCsGAQUFBzAChj1o
            dHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9haWEvUElESXNzdWVyQ0EwMi1F
            VS5jYWNlcnQucGVtMDIGA1UdEQQrMCmGJ2h0dHBzOi8vZGV2LnZlcmlmaWVyLWJh
            Y2tlbmQuZXVkaXcuZGV2LzAUBgNVHSAEDTALMAkGBwQAi+xGAQIwQwYDVR0fBDww
            OjA4oDagNIYyaHR0cHM6Ly9wcmVwcm9kLnBraS5ldWRpdy5kZXYvY3JsL3BpZF9D
            QV9FVV8wMi5jcmwwHQYDVR0OBBYEFO+X15taOVBhkGJTBBt50FSN0zMPMA4GA1Ud
            DwEB/wQEAwIHgDAKBggqhkjOPQQDAgNJADBGAiEApj2PCZqVuQwq/Wy6y5gf2tm4
            XXYfyjgJS2jl6poPBK0CIQDOrjRS9rPbEK3MbUnQdcfZpRHCMeaT5+Fhqb+nrb89
            cw==
            -----END CERTIFICATE-----
        """.trimIndent()

    val wrprcProviderSigningCertificate =
        """
            -----BEGIN CERTIFICATE-----
            MIIDADCCAqagAwIBAgIUUljqyaHD3IkeyD37S3leTe0DJSgwCgYIKoZIzj0EAwIwXD
            EeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAyMS0wKwYDVQQKDCRFVURJIFdh
            bGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4XDTI2MD
            UyMTE1NTMzM1oXDTI4MDUyMDE1NTMzMlowUzEaMBgGA1UEAwwRUFkgUElEIElzc3Vl
            ciBERVYxGDAWBgNVBGEMD0xFSUVVLTEyMzQ1Njc4OTEOMAwGA1UECgwFTmlzY3kxCz
            AJBgNVBAYTAlVUMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE90J7U8sxIE61Lu8j
            WqVqq3bXf3ra7poWY6381JFUyECN1NMlHqbj96mTvzvPC3PAhCumVWoi4l56KMd0lT
            tXgqOCAU0wggFJMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUYseURyi9D6IWIKea
            wkmURPEB08cwWQYIKwYBBQUHAQEETTBLMEkGCCsGAQUFBzAChj1odHRwczovL3ByZX
            Byb2QucGtpLmV1ZGl3LmRldi9haWEvUElESXNzdWVyQ0EwMi1VVC5jYWNlcnQucGVt
            MC4GA1UdIAQnMCUwIwYDKgMEMBwwGgYIKwYBBQUHAgEWDmV4YW1wbGUucG9saWN5ME
            MGA1UdHwQ8MDowOKA2oDSGMmh0dHBzOi8vcHJlcHJvZC5wa2kuZXVkaXcuZGV2L2Ny
            bC9waWRfQ0FfVVRfMDIuY3JsMB0GA1UdDgQWBBRf4pfkwe1uZj/hPD8av9oh60QPij
            AOBgNVHQ8BAf8EBAMCB4AwGQYIKwYBBQUHAQMEDTALMAkGBwQAi+xOAQEwCgYIKoZI
            zj0EAwIDSAAwRQIhAKMSQDJufNWNw75kR6dmUB4RyT+EmUz0njYsN6fAV/cIAiB2BB
            JRp0ZLucz19pBj2Qoo/BiUkhYDSiw3+lQ/dmCXgw==
            -----END CERTIFICATE-----
        """.trimIndent()
}
