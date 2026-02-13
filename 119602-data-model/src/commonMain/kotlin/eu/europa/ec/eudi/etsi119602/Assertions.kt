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
package eu.europa.ec.eudi.etsi119602

import kotlin.contracts.contract

public object Assertions {
    public fun requireNotBlank(
        value: String,
        attributeName: String,
    ) {
        require(value.isNotBlank()) { "$attributeName cannot be blank" }
    }

    public fun requireNullOrNotBlank(
        value: String?,
        attributeName: String,
    ) {
        if (value != null) requireNotBlank(value, attributeName)
    }

    public fun requireNullOrNonEmpty(list: List<*>?, attributeName: String) {
        if (null != list) require(list.isNotEmpty()) { "$attributeName cannot be empty" }
    }

    public fun requireNonEmpty(list: List<*>, attributeName: String) {
        require(list.isNotEmpty()) { "$attributeName cannot be empty" }
    }

    public inline fun <reified T : Any> checkNotNull(
        value: T?,
        attributeName: String,
    ): T {
        contract {
            returns() implies (value != null)
        }
        return checkNotNull(value) { "$attributeName must be set" }
    }

    public inline fun <reified T : Any> checkIsNull(
        value: T?,
        attributeName: String,
    ) {
        contract {
            returns() implies (value == null)
        }
        check(value == null) { "$attributeName must not be set" }
    }
}
