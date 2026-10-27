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

import eu.europa.ec.eudi.etsi119602.datamodel.Uri

/**
 * Provides the last known good LoTE content for a URI.
 *
 * [LoadLoTEAndPointers] uses this as a fallback when the fresh path for a LoTE step fails
 * (for example when the fresh download fails verification or cannot be downloaded). The returned
 * content is re-verified before [LoadLoTEAndPointers] emits
 * [LoadLoTEAndPointers.Event.LoTELoaded] for it.
 *
 * When [loTEFallbackProvider] is not provided to [LoadLoTEAndPointers], it is auto-wired from
 * [loadLoTE] if the loader implements [LoTEFallbackProvider] (e.g. [LoadSingleLoTEWithFileCache]).
 */
public fun interface LoTEFallbackProvider {

    /**
     * Returns the last known good LoTE content for [uri], or `null` when none is available or it
     * is no longer valid.
     *
     * @param uri of the LoTE
     */
    public suspend fun lastKnownGood(uri: Uri): String?
}
