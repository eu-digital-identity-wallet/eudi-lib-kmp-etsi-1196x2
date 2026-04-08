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

import kotlin.jvm.JvmInline

/**
 * A value class representing a non-empty list.
 *
 * This class provides compile-time guarantees that a list contains at least one element,
 * eliminating the need for runtime checks in code that requires non-empty collections.
 *
 * @param T the type of elements in the list
 * @property list the underlying list, guaranteed to be non-empty
 * @throws IllegalArgumentException if the provided list is empty
 *
 * @sample
 * ```kotlin
 * val nonEmpty = NonEmptyList(listOf(1, 2, 3)) // OK
 * val empty = NonEmptyList(emptyList<Int>())    // throws IllegalArgumentException
 * ```
 */
@JvmInline
public value class NonEmptyList<out T>(public val list: List<T>) {
    init {
        require(list.isNotEmpty()) { "Non-empty list expected, but was empty" }
    }

    public companion object {
        /**
         * Creates a [NonEmptyList] from the given list, or returns null if the list is empty.
         *
         * This is a safe constructor that allows handling the empty case explicitly.
         *
         * @param list the list to wrap
         * @return a [NonEmptyList] if the list is not empty, null otherwise
         *
         * @sample
         * ```kotlin
         * val result = NonEmptyList.nelOrNull(listOf(1, 2)) // NonEmptyList([1, 2])
         * val empty = NonEmptyList.nelOrNull(emptyList())   // null
         * ```
         */
        public fun <T> nelOrNull(list: List<T>): NonEmptyList<T>? =
            if (list.isEmpty()) null else NonEmptyList(list)
    }
}
