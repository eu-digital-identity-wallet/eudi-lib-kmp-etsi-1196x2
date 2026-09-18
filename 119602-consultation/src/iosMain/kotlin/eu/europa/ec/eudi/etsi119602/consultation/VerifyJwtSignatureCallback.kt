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

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Swift-implementable variant of [VerifyJwtSignature].
 *
 */
public fun interface VerifyJwtSignatureCallback {
    public fun verify(jwt: String, onOutcome: (VerifyJwtSignature.Outcome) -> Unit)
}

/**
 * Adapts a [VerifyJwtSignatureCallback] into a [VerifyJwtSignature].
 */
public fun VerifyJwtSignatureCallback.asVerifyJwtSignature(): VerifyJwtSignature =
    VerifyJwtSignature { jwt ->
        suspendCancellableCoroutine { cont ->
            verify(jwt) { outcome -> cont.resume(outcome) }
        }
    }
