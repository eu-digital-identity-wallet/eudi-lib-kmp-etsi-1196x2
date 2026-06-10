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

/**
 * A [VerifyJwtSignature] that performs NO verification — it accepts every LoTE JWT.
 *
 * **INSECURE. For local development and integration demos only.** It exists because a Kotlin
 * `suspend fun interface` is awkward to implement directly from Swift. A production wallet MUST
 * pass a real [VerifyJwtSignature] (verifying against the trusted scheme operator keys) instead;
 * using this in production defeats the entire trust model.
 */
public object InsecureAcceptAllJwtSignature : VerifyJwtSignature {
    override suspend fun invoke(jwt: String): VerifyJwtSignature.Outcome =
        VerifyJwtSignature.Outcome.Verified(jwt)
}
