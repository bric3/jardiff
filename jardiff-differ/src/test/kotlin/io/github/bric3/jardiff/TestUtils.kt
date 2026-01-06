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

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.extension
import kotlin.reflect.KClass

fun green(string: String): String = "${Logger.GREEN}$string${Logger.RESET}"

fun red(string: String): String = "${Logger.RED}$string${Logger.RESET}"

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

private const val FIXTURES_JAR = "text-fixtures.jar.path"
private const val FIXTURES_KOTLIN_CLASSES = "text-fixtures.kotlin.classes.path"
private const val FIXTURES_RESOURCES = "text-fixtures.resources.path"

val fixtureClassesOutput: Path
    get() = Path.of(System.getProperty(FIXTURES_KOTLIN_CLASSES)).also {
        require(Files.isDirectory(it)) { "Path in '$FIXTURES_KOTLIN_CLASSES' must be a directory, got $it" }
    }
val fixtureResources: Path
    get() = Path.of(System.getProperty(FIXTURES_RESOURCES)).also {
        require(Files.isDirectory(it)) { "Path in '$FIXTURES_RESOURCES' must be a directory, got $it" }
    }
val fixtureJar: Path
    get() = Path.of(System.getProperty(FIXTURES_JAR)).also {
        require(it.extension == "jar" && Files.isRegularFile(it)) { "Path in '$FIXTURES_JAR' must be a jar, got $it" }
    }

fun createJarFromResources(
    destinationDir: Path,
    cl: ClassLoader,
    vararg resourceNames: String
): Path {
    return createJarFromResources(
        destinationDir = destinationDir,
        cl = cl,
        entryRenamer = { it },
        resourceNames = resourceNames
    )
}

fun createJarFromResources(
    destinationDir: Path,
    cl: ClassLoader,
    entryRenamer: (String) -> String,
    vararg resourceNames: String
): Path {
    val tmpJar = destinationDir.resolve("${UUID.randomUUID()}.jar")

    val manifest = Manifest().apply {
        mainAttributes.let {
            it[Attributes.Name.MANIFEST_VERSION] = "1.0"
            it[Attributes.Name("Created-By")] = "TestUtils"
        }
    }
    JarOutputStream(
        Files.newOutputStream(tmpJar).buffered(),
        manifest
    ).use { target ->
        for (resourceName in resourceNames) {
            val inputStream = cl.getResourceAsStream(resourceName)
            if (inputStream == null) {
                System.err.println("$resourceName not found in $cl")
                continue
            }
            
            inputStream.buffered().use {
                target.putNextEntry(JarEntry(entryRenamer.invoke(resourceName)))
                target.write(it.readAllBytes())
                target.closeEntry()
            }
        }
    }
    return tmpJar
}

fun fixtureClassInputStream(kclass: KClass<*>): InputStream =
    kclass.java.classLoader.getResourceAsStream(kclass.path)!!.buffered()
