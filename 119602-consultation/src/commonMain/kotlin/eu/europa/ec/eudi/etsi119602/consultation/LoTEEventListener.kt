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
 * Listens to the [LoadLoTEAndPointers.Event]s emitted for each processed LoTE step
 */
public fun interface LoTEEventListener {

    /**
     * A callback on a LoTE event
     *
     * @param jwt is the raw content of the step that produced [event] (null when nothing was downloaded).
     * @param uri of the LoTE
     * @param event an evaluated event for the LoTE
     */
    public suspend fun onEvent(uri: Uri, jwt: String?, event: LoadLoTEAndPointers.Event)
}
