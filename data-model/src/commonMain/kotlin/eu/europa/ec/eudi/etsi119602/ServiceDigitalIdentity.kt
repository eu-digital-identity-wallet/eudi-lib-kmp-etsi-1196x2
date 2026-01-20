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

import eu.europa.ec.eudi.etsi119602.serialization.ByteArraySerializedInBase64
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ServiceDigitalIdentity(
    @SerialName(ETSI19602.X509_CERTIFICATES) val x509Certificates: List<PkiOb>? = null,
    @SerialName(ETSI19602.X509_SUBJECT_NAMES) val x509SubjectNames: List<String>? = null,
    @SerialName(ETSI19602.PUBLIC_KEY_VALUES) val publicKeyValues: List<JsonObject>? = null,
    @SerialName(ETSI19602.X509_SKIS) val x509SKIs: List<ByteArraySerializedInBase64>? = null,
    @SerialName(ETSI19602.OTHER_IDS) val otherIds: JsonArray? = null,
) {
    init {
        requireNullOrNonEmpty(x509Certificates, ETSI19602.X509_CERTIFICATES)
        requireNullOrNonEmpty(x509SubjectNames, ETSI19602.X509_SUBJECT_NAMES)
        requireNullOrNonEmpty(publicKeyValues, ETSI19602.PUBLIC_KEY_VALUES)
        requireNullOrNonEmpty(x509SKIs, ETSI19602.X509_SKIS)
        requireNullOrNonEmpty(otherIds, ETSI19602.OTHER_IDS)
    }
}

@Serializable
public data class PkiOb(
    @SerialName(ETSI19602.PKI_OB_VAL) @Required val value: ByteArraySerializedInBase64,
    @SerialName(ETSI19602.PKI_OB_ENCODING) val encoding: URI? = null,
    @SerialName(ETSI19602.PKI_OB_SPEC_REF) val specRef: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PkiOb

        if (!value.contentEquals(other.value)) return false
        if (encoding != other.encoding) return false
        if (specRef != other.specRef) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + (encoding?.hashCode() ?: 0)
        result = 31 * result + (specRef?.hashCode() ?: 0)
        return result
    }
}
