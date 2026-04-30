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
package eu.europa.ec.eudi.etsi1196x2.consultation.dss

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import org.slf4j.LoggerFactory

class DssAndroidJUnitRunner : AndroidJUnitRunner() {

    private val log = LoggerFactory.getLogger(DssAndroidJUnitRunner::class.java)

    override fun onCreate(arguments: Bundle?) {
        log.info("DssAndroidJUnitRunner: Android test runner initialized")
        super.onCreate(arguments)
    }
}
