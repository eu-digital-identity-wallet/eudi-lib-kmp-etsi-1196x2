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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private fun cannotBeBlankMsg(field: String) = "$field cannot be blank"

internal fun requireNotBlank(
    v: String,
    field: String,
) {
    require(v.isNotBlank()) { cannotBeBlankMsg(field) }
}

internal fun requireNullOrNotBlank(
    v: String?,
    field: String,
) {
    if (v != null) requireNotBlank(v, field)
}

internal fun requireNullOrNonEmpty(list: List<*>?, name: String) {
    if (null != list) require(list.isNotEmpty()) { "$name cannot be empty" }
}

internal fun requireNonEmpty(list: List<*>, name: String) {
    require(list.isNotEmpty()) { "$name cannot be empty" }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <reified T : Any> checkNotNull(
    value: T?,
    field: String,
): T {
    contract {
        returns() implies (value != null)
    }
    return checkNotNull(value) { "$field must be set" }
}
