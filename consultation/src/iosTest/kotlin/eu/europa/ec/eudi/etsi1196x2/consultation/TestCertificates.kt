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

import platform.Foundation.NSData
import kotlin.io.encoding.Base64

/**
 * DER-encoded test certificates, embedded as base64 to avoid resource-loading complexity in
 * Kotlin/Native test targets. Generated with openssl (RSA-2048, SHA-256, validity 2020-2099 so
 * the chain is valid regardless of the simulator clock):
 *
 *  - [ROOT]:  self-signed CA (basicConstraints CA:TRUE, keyUsage keyCertSign+cRLSign)
 *  - [LEAF]:  end-entity signed by ROOT (basicConstraints CA:FALSE, keyUsage digitalSignature)
 *  - [OTHER]: an unrelated self-signed CA that did NOT sign LEAF (for NotTrusted cases)
 *
 * `openssl verify -CAfile root.pem leaf.pem` => OK; `-CAfile other.pem leaf.pem` => fails.
 */
internal object TestCertificates {

    val rootDer: NSData get() = Base64.decode(ROOT).toNSData()
    val leafDer: NSData get() = Base64.decode(LEAF).toNSData()
    val otherDer: NSData get() = Base64.decode(OTHER).toNSData()

    private const val ROOT: String =
        "MIIDbTCCAlWgAwIBAgIUbJsQd2f5HVnnRqfiIQ5G8QJZozQwDQYJKoZIhvcNAQELBQAwPTEL" +
            "MAkGA1UEBhMCREUxEjAQBgNVBAoMCUVVREkgVGVzdDEaMBgGA1UEAwwRRVVESSBUZXN0IFJv" +
            "b3QgQ0EwIBcNMjAwMTAxMDAwMDAwWhgPMjA5OTAxMDEwMDAwMDBaMD0xCzAJBgNVBAYTAkRF" +
            "MRIwEAYDVQQKDAlFVURJIFRlc3QxGjAYBgNVBAMMEUVVREkgVGVzdCBSb290IENBMIIBIjAN" +
            "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuJGVPLGNEfQWvBGCxVhr+usadcicPzjm/WqO" +
            "aJYIy7F17EHdwjvT+UlhenLjZAk6qqmCRmFQ8Sq/QT+Q67/KY1LcX85J5lyzbPDmlGYULvWZ" +
            "c3tuv0Hzm3gon8EXeIvwfZqPmvFXAd/4Xq6NZF9z/6kk9avjZXhGxAdUbeankEnDP1k4JttA" +
            "sxOXIeJ9USfufN1SlcIvi2/14Xtrq2fVvnbRGnF2QDjGiTgndr1E9/4SoKtunwfwD7CgDwzX" +
            "9hCSOzO+laFQ/3fk4KylLjJNQ90bCfEro64W2Q0maHXxVB6x8FSf1qa/wUkOE2yiTmEtNNNk" +
            "5ES8la9cCtOOrHXeIwIDAQABo2MwYTAdBgNVHQ4EFgQUu0Fn2wN5ckCsVtpnLk0nAbfq7wcw" +
            "HwYDVR0jBBgwFoAUu0Fn2wN5ckCsVtpnLk0nAbfq7wcwDwYDVR0TAQH/BAUwAwEB/zAOBgNV" +
            "HQ8BAf8EBAMCAQYwDQYJKoZIhvcNAQELBQADggEBAJWuth0OABfrj72oX8CPvQTh3PJcz9Hm" +
            "+/uLvFEjeaBvBgVxsGOIhPm7XTVnoWEvuEE5C0J8J8eaX4MaGgZy2nl3l6dN7IgUJTuvsKUi" +
            "ZTjpuf2pVrtXBj3NmkKPfzBJ6pwVZ8qT16JTZxGsoumXPqb/AGrhTv9phJrO8MHBOQlAF5kO" +
            "jGHCs0ueJQ0zmvqP78x3sUBB75WRHfqQaVFyft7JrLZDqwd0GKxPUuGPUAHre40joVwIu3Mo" +
            "IR8LjOTdItccqU4DhKZu/PIcgMwnoPMg/m6PdWiQqHHATqEkyXnrtWn85moslhKOyPQ0VSVp" +
            "JEy7w7FN//PZlipdRJgw7+o="

    private const val LEAF: String =
        "MIIDZDCCAkygAwIBAgIUIp0yZor/Ilu4YrUBzOyfYAEFS/swDQYJKoZIhvcNAQELBQAwPTEL" +
            "MAkGA1UEBhMCREUxEjAQBgNVBAoMCUVVREkgVGVzdDEaMBgGA1UEAwwRRVVESSBUZXN0IFJv" +
            "b3QgQ0EwIBcNMjAwMTAxMDAwMDAwWhgPMjA5OTAxMDEwMDAwMDBaMDoxCzAJBgNVBAYTAkRF" +
            "MRIwEAYDVQQKDAlFVURJIFRlc3QxFzAVBgNVBAMMDkVVREkgVGVzdCBMZWFmMIIBIjANBgkq" +
            "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhh5241PcI+22TjEyjgub8+BZ/n6m9UWfS7oElZhI" +
            "qPb6iaqeddGj9r/shlRk+rqlV8SMZ+MQ03D9z8+iYrfMyVcoE6dvghDZOZeX3Fnl7STr3TiY" +
            "Ym4GLetvI0sexKh6NOKnLntOe4zzUtP4KGE2/BIk1xYCk/GEmAByzYZgQt6rm4b3NfRR8jAx" +
            "DHzehlfnnb0xuyx5I4HikX2pLZrgs3iPBidJWQj/elnWn9vsyMMAiXTMZNA5OdO4JzW9PhN5" +
            "3FhRyuiQThW1Xd0Gdwd8zLxiPXWPmWXKyHnWID2jdtl/KNzLhzXbwTlWfd8N7T2wbgLSsYEF" +
            "gfk+hLqqb+YYWQIDAQABo10wWzAJBgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIHgDAdBgNVHQ4E" +
            "FgQUA9vm85uQvXnyrKFy0XOm7cOuDPswHwYDVR0jBBgwFoAUu0Fn2wN5ckCsVtpnLk0nAbfq" +
            "7wcwDQYJKoZIhvcNAQELBQADggEBAAanjQa1t5LVbJGGYdBnLgrCT6PnOuySSzG0J+eYLmUS" +
            "Vxzg0E+UTQEXxz+0KL66enSY7lJu2l1gqSfa+hjZCo7boSz2ub3bYFRGgFULrJapPtkI5a4N" +
            "Yh3+AJSGXJKHKcJGv4X7EhSjggEcsuA8NQbrpZ+8t3feCMjprd9xUXCB3zCnNKxz+E6E2TeJ" +
            "0RSZtOZJAK87OofW6vuIT9mKg09WQpck8c9aNxagVgJFH4BxyWN/OPxBPmYNksVcKsMeTZDR" +
            "lj1yFDKp3wnkiSUlieY8Zw+Zjp7F7X+jTEAHQ74kvUsxqPQ9XZ2U0ogBVTU6wLz4rpLFI+iX" +
            "1z9LjGyU8Bs="

    private const val OTHER: String =
        "MIIDbTCCAlWgAwIBAgIUWodyXM4xLZLHeqC4jq7sKV/Rz1MwDQYJKoZIhvcNAQELBQAwPTEL" +
            "MAkGA1UEBhMCREUxEjAQBgNVBAoMCU90aGVyIE9yZzEaMBgGA1UEAwwRVW5yZWxhdGVkIFJv" +
            "b3QgQ0EwIBcNMjAwMTAxMDAwMDAwWhgPMjA5OTAxMDEwMDAwMDBaMD0xCzAJBgNVBAYTAkRF" +
            "MRIwEAYDVQQKDAlPdGhlciBPcmcxGjAYBgNVBAMMEVVucmVsYXRlZCBSb290IENBMIIBIjAN" +
            "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoPqXNUKUXD9GeK3QXJB/iS/eWgM+jQF7oYi5" +
            "rLE9WH7dx3dlwIXqklQKE/YRtwao5eIm+U+w9l5iiDdIcuCrWRnajX6mjSucAXBXTYW3rFk4" +
            "LC8EFJhhMhibywz9MzoO5M7Fvldm6A/4LstzNcIJ6/4cbxOa1YPmAvEgxCxt4vY3+NXgtar7" +
            "gQFwgnaUPrsaJf+2BbtYZb6boFZbBkYFQAdkAB4nSQZacyrMRZ8V13gMGaLyCDEWtXoJr1ZL" +
            "PcdetCfHbHq2Pxxea5fkb1QoBBIRdvFXB23rprS10HnfuK6bXKNXO6Ymbsk+6qzEjJ5G7Fku" +
            "XQhMjRDdzr90x+rJ5wIDAQABo2MwYTAdBgNVHQ4EFgQUzv1fVadIqTe8UsGgWSXIMz82UWMw" +
            "HwYDVR0jBBgwFoAUzv1fVadIqTe8UsGgWSXIMz82UWMwDwYDVR0TAQH/BAUwAwEB/zAOBgNV" +
            "HQ8BAf8EBAMCAQYwDQYJKoZIhvcNAQELBQADggEBAC0fxMpIY5Nd4a3hwvZLmzi+j034FraS" +
            "5XnDN/XmiO37qScatKdRJuO/UYk8XcuX34mmLVpmLdjtpyH0e87VYxf3WFCSEPy/FBlk5IBA" +
            "ZkUKlV5YCzmUdXJ9Lgv2f03GFQ6OE+XIPvYC/s2RMnICEjXErbwb0J55+i6x8ffOxiya4nUk" +
            "wD7dWiwNJMQIf7W2jCW/iaacijOM7Mgz/9iZa8uvYDWYDBt5ByXi9A36p9oReumUB0Te5+SE" +
            "dN72A+9Pd0gbDxpMbgDtpJ/9t261weNjE5eZVH0bvzwb/qwTb2id82b5zEPQ24id6dkY4xhx" +
            "VwHpZDSvmEDC/FNpxotAUt8="
}
