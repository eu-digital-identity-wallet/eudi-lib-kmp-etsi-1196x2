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

/**
 * Gradle script that patches the DSS dss-jaxb-common JAR for Android compatibility.
 *
 * DSS 6.4 uses XMLInputFactory.newFactory() (Java 9+) which doesn't exist on Android.
 * This script replaces those calls with XMLInputFactory.newInstance() which works on Android.
 *
 * Usage:
 *   apply(from = "dss-android-patch.gradle.kts")
 *
 * The DSS version can be overridden via project property:
 *   -PdssVersion=6.4.1
 */

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm:9.8") }
}

val dssVersion = project.findProperty("dssVersion")?.toString() ?: "6.4"

val patchDssJaxbCommon by tasks.registering {
    val inputJarProvider =
        provider {
            val cfg =
                project.configurations.detachedConfiguration(
                    project.dependencies.create("eu.europa.ec.joinup.sd-dss:dss-jaxb-common:$dssVersion"),
                )
            cfg.resolve().single { it.name.startsWith("dss-jaxb-common") }
        }
    val outputJar = project.layout.buildDirectory.file("libs/dss-jaxb-common-$dssVersion-android.jar")
    outputs.file(outputJar)
    inputs.file(inputJarProvider)

    doLast {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val inputJar = inputJarProvider.get()
        val classToPatch = "eu/europa/esig/dss/jaxb/common/AbstractJaxbFacade.class"
        JarInputStream(inputJar.inputStream()).use { jis ->
            JarOutputStream(out.outputStream()).use { jos ->
                var entry = jis.nextJarEntry
                while (entry != null) {
                    jos.putNextEntry(JarEntry(entry.name))
                    if (entry.name == classToPatch) {
                        val bytes: ByteArray = jis.readAllBytes()
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(0)
                        reader.accept(
                            object : ClassVisitor(Opcodes.ASM9, writer) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String?,
                                    desc: String?,
                                    signature: String?,
                                    exceptions: Array<out String>?,
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, desc, signature, exceptions)
                                    return if (name == "avoidXXE") {
                                        object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitMethodInsn(
                                                opcode: Int,
                                                owner: String?,
                                                methodName: String?,
                                                methodDesc: String?,
                                                isInterface: Boolean,
                                            ) {
                                                val effectiveName =
                                                    if (owner == "javax/xml/stream/XMLInputFactory" &&
                                                        methodName == "newFactory" &&
                                                        methodDesc == "()Ljavax/xml/stream/XMLInputFactory;"
                                                    ) "newInstance" else methodName
                                                super.visitMethodInsn(opcode, owner, effectiveName, methodDesc, isInterface)
                                            }
                                        }
                                    } else {
                                        mv
                                    }
                                }
                            },
                            0,
                        )
                        jos.write(writer.toByteArray())
                    } else {
                        jis.copyTo(jos)
                    }
                    jos.closeEntry()
                    entry = jis.nextJarEntry
                }
            }
        }
    }
}

// Exclude original dss-jaxb-common from all Android configurations
project.configurations.matching { it.name.contains("Android") && it.name.contains("Classpath") }.configureEach {
    exclude(group = "eu.europa.ec.joinup.sd-dss", module = "dss-jaxb-common")
}

// Add patched JAR — detect KMP vs standard Android project
val androidConfigName =
    if (project.configurations.findByName("androidMainImplementation") != null) {
        "androidMainImplementation"
    } else {
        "implementation"
    }

project.dependencies.add(androidConfigName, project.files(patchDssJaxbCommon.map { it.outputs.files.singleFile }))
