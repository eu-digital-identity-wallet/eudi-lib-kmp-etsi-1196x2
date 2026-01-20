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

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public value class Language(public val value: String) {
    init {
        requireNotBlank(value, ETSI19602.LANG)
        require(value.matches(ALPHA_2_PATTERN)) { "Invalid ${ETSI19602.LANG}" }
    }

    override fun toString(): String = value

    public companion object {
        public val ENGLISH: Language get() = Language(ETSI19602.LANG_ENGLISH)
        private val ALPHA_2_PATTERN = Regex("^[a-z]{2}$")
    }
}
