/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff

import org.assertj.core.api.Assertions.assertThat
import sun.tools.jar.resources.jar
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.reflect.KClass

val KClass<*>.path: String
    get() {
        val simpleClassNameWithHostName = qualifiedName?.substring(java.packageName.length + 1)
            ?: throw IllegalStateException("The class $this has no qualified name")

        return buildString {
            append(java.packageName.replace('.', '/'))
            append("/")
            append(simpleClassNameWithHostName.replace('.', '$'))
            append(".class")
        }
    }

val KClass<*>.location: Path
    get() = Paths.get(java.protectionDomain.codeSource.location.toURI())

val KClass<*>.bytes: ByteArray?
    get() = this.path.let {
        this.java.classLoader.getResourceAsStream(it)?.readAllBytes()
    }

val fixtureClassesOutput: Path
    get() = Path.of(System.getProperty("text-fixtures.kotlin.classes.path")).also {
        require(Files.isDirectory(it)) { "Path in 'text-fixtures.classes.path' must be a directory, got $it" }
    }
val fixtureJar: Path
    get() = Path.of(System.getProperty("text-fixtures.jar.path")).also {
        require(it.extension == "jar" && Files.isRegularFile(it)) { "Path in 'text-fixtures.jar.path' must be a jar, got $it" }
    }