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
package eu.europa.ec.eudi.etsi1196x2.consultation

@JvmInline
public value class NonEmptyList<out T>(public val list: List<T>) {
    init {
        require(list.isNotEmpty()) { "Non-empty list expected, but was empty" }
    }
    public companion object {
        public fun <T> nelOrNull(list: List<T>): NonEmptyList<T>? =
            if (list.isEmpty()) null else NonEmptyList(list)
    }
}
