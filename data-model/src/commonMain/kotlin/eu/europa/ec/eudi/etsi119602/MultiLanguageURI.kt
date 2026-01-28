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

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class MultiLanguageURI(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.URI_VALUE) @Required val value: URIValue,
) {
    public companion object {
        public fun en(value: URIValue): MultiLanguageURI = MultiLanguageURI(Language.ENGLISH, value)
    }
}

// TODO Value is URI
@Serializable
@JvmInline
public value class URIValue(public val value: String) {
    init {
        Assertions.requireNotBlank(value, ETSI19602.URI_VALUE)
    }

    override fun toString(): String = value
}
