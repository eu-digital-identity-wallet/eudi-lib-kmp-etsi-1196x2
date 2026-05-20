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

import platform.Foundation.NSData

/**
 * Creates a [CertificateProfileValidator] for iOS DER-encoded certificates ([NSData]),
 * backed by [CertificateOperationsIos]. The common-side interpreter drives all constraint
 * evaluation, so the only platform-specific piece is field extraction.
 *
 * @param operations certificate field extraction (default: [CertificateOperationsIos])
 */
@Suppress("ktlint:standard:function-naming")
public fun CertificateProfileValidatorIos(
    operations: CertificateOperations<NSData> = CertificateOperationsIos(),
): CertificateProfileValidator<NSData> =
    CertificateProfileValidator(operations)
