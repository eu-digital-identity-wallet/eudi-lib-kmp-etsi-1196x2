package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public value class Language(
    public val value: String,
) {
    init {
       requireNotBlank(value, ETSI19602.LANG)
    }

    override fun toString(): String = value

}

@Serializable
public data class MultiLangString(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.STRING_VALUE) @Required val value: String,
){
    init {
        requireNotBlank(value, ETSI19602.STRING_VALUE)
    }
}


