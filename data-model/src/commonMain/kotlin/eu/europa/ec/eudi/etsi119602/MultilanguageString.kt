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
public data class MultilanguageString(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.STRING_VALUE) @Required val value: String,
) {
    init {
        Assertions.requireNotBlank(value, ETSI19602.STRING_VALUE)
        require(MultilingualStringValue.isValid(value)) { "Invalid ${ETSI19602.STRING_VALUE}" }
    }
    public companion object {
        public fun en(value: String): MultilanguageString = MultilanguageString(Language.ENGLISH, value)
    }
}

@Serializable
@JvmInline
public value class MultilingualStringValue(public val value: String) {
    init {
        require(isValid(value)) { "Invalid multilingual string" }
    }

    override fun toString(): String = value

    public companion object {
        /**
         * Validates a string according to ETSI 119602 rules,
         * provided in annex B.2
         */
        public fun isValid(s: String): Boolean {
            // Rule 2 & 3: UTF-8 is the standard for Kotlin Strings, but we must ensure no BOM.
            // UTF-8 BOM is 0xEF, 0xBB, 0xBF (\uFEFF)
            if (s.startsWith('\uFEFF')) return false

            // We iterate via code points to handle Surrogate Pairs (characters above U+FFFF)
            var i = 0
            while (i < s.length) {
                val codePoint = getCodePointAt(s, i)
                val charCount = if (codePoint > 0xFFFF) 2 else 1
                if (!isCodePointValid(codePoint)) return false
                i += charCount
            }

            // Rule 7: Simple check for markup-like patterns (tags)
            // While a full parser is complex, Rule 7 forbids mark-up elements/tags.
            if (s.contains("<") && s.contains(">")) {
                // Heuristic: if it looks like an XML/HTML tag, reject it.
                if (Regex("<[^>]+>").containsMatchIn(s)) return false
            }

            return true
        }

        private fun isCodePointValid(cp: Int): Boolean =
            with(ISO6429) {
                !(cp.isControlFunction() || cp.isPrivateUseZone() || cp.isTag())
            }

        /**
         * Helper to get Unicode Code Point.
         */
        private fun getCodePointAt(s: String, index: Int): Int {
            val high = s[index]
            if (high.isHighSurrogate() && index + 1 < s.length) {
                val low = s[index + 1]
                if (low.isLowSurrogate()) {
                    return (high.code - 0xD800 shl 10) + (low.code - 0xDC00) + 0x10000
                }
            }
            return high.code
        }
    }
}

internal object ISO6429 {
    private val CONTROL_FUNCTIONS_C0: IntRange = 0x00..0x1F
    private val CONTROL_FUNCTIONS_C1: IntRange = 0x80..0x9F
    private val TAG_CHARACTERS: IntRange = 0xE0000..0xE007F
    private val PRIVATE_USE_ZONE_BMP: IntRange = 0xE000..0xF8FF
    private val PRIVATE_USE_ZONE_PLANE_15: IntRange = 0xF0000..0xFFFFD
    private val PRIVATE_USE_ZONE_PLANE_16: IntRange = 0x100000..0x10FFFD

    /**
     * Control Functions (C0 and C1 sets), No TAB (9), LF (10), CR (13)
     */
    fun Int.isControlFunction(): Boolean =
        this in CONTROL_FUNCTIONS_C0 || this in CONTROL_FUNCTIONS_C1

    /**
     * True if in the range of Tag Characters (U+E0000 to U+E007F)
     */
    fun Int.isTag(): Boolean =
        this in TAG_CHARACTERS

    fun Int.isPrivateUseZone(): Boolean =
        this in PRIVATE_USE_ZONE_BMP || this in PRIVATE_USE_ZONE_PLANE_15 || this in PRIVATE_USE_ZONE_PLANE_16
}
