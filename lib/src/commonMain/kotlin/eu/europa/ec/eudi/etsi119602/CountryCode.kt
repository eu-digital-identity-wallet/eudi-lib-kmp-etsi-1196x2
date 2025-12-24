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
public value class CountryCode(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Country code cannot be blank" }
        require(value.matches(CAPITAL_LETTERS_PATTERN)) { "Country code must be all capital letters" }
    }

    override fun toString(): String = value

    public companion object {
        public val EU: CountryCode get() = iso3166(ETSI19602.COUNTRY_CODE_EU)
        private val CAPITAL_LETTERS_PATTERN = Regex("^[A-Z]+$")
        private val ISO3166_1_ALPHA_2_PATTERN = Regex("^[A-Z]{2}$")

        public fun iso3166(value: String): CountryCode {
            require(value.matches(ISO3166_1_ALPHA_2_PATTERN)) { "Invalid ISO 3166-1 alpha-2 code: $value" }
            return CountryCode(value)
        }
    }
}
