package eu.europa.ec.eudi.etsi119602

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class MultiLanguageURI(
    @SerialName(ETSI19602.LANG) @Required val language: Language,
    @SerialName(ETSI19602.URI_VALUE) @Required val value: URIValue,
)

// TODO Value is URI
@Serializable
@JvmInline
public value class URIValue(
    public val uri: String,
) {
    init {
        requireNotBlank(uri, ETSI19602.URI_VALUE)
    }
}
