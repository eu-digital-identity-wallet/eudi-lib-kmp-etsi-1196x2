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
