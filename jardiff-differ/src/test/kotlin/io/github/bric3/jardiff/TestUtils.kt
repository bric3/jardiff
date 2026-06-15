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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
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

fun createSemanticSameClassDirectories(destinationDir: Path, kclass: KClass<*>): Pair<Path, Path> {
    val leftDirectory = destinationDir.resolve("${UUID.randomUUID()}-left-semantic-class")
    val rightDirectory = destinationDir.resolve("${UUID.randomUUID()}-right-semantic-class")
    val classPath = Path.of(kclass.path)
    val originalClassBytes = kclass.bytes
        ?: throw IllegalStateException("Could not load fixture class bytes for $kclass")

    Files.createDirectories(leftDirectory.resolve(classPath).parent)
    Files.createDirectories(rightDirectory.resolve(classPath).parent)
    Files.write(leftDirectory.resolve(classPath), originalClassBytes)
    Files.write(
        rightDirectory.resolve(classPath),
        appendUnusedEntriesToClassConstantPool(originalClassBytes, "jardiff-unused-test-constant")
    )

    return leftDirectory to rightDirectory
}

fun createMemberReorderedClassDirectories(destinationDir: Path, kclass: KClass<*>): Pair<Path, Path> {
    val leftDirectory = destinationDir.resolve("${UUID.randomUUID()}-left-member-order")
    val rightDirectory = destinationDir.resolve("${UUID.randomUUID()}-right-member-order")
    val classPath = Path.of(kclass.path)
    val originalClassBytes = kclass.bytes
        ?: throw IllegalStateException("Could not load fixture class bytes for $kclass")

    Files.createDirectories(leftDirectory.resolve(classPath).parent)
    Files.createDirectories(rightDirectory.resolve(classPath).parent)
    Files.write(leftDirectory.resolve(classPath), originalClassBytes)
    Files.write(rightDirectory.resolve(classPath), reorderClassMembers(originalClassBytes))

    return leftDirectory to rightDirectory
}

fun reorderClassMembers(classBytes: ByteArray): ByteArray {
    val classNode = ClassNode()
    ClassReader(classBytes).accept(classNode, 0)

    classNode.fields.reverse()
    classNode.methods.reverse()

    return ClassWriter(0).also { classNode.accept(it) }.toByteArray()
}

/**
 * Returns valid class bytes that differ from [classBytes] but textify to the same
 * ASM output by appending an unreferenced UTF-8 constant-pool entry.
 *
 * This lets tests exercise the "bytes differ, semantic class diff does not" path.
 */
private fun appendUnusedEntriesToClassConstantPool(classBytes: ByteArray, value: String): ByteArray {
    require(classBytes[0] == 0xCA.toByte() && classBytes[1] == 0xFE.toByte()) {
        "Expected a class file"
    }

    val constantPoolCount = readUnsignedShort(classBytes, 8)
    var offset = 10
    var index = 1
    while (index < constantPoolCount) {
        when (val tag = classBytes[offset++].toInt() and 0xff) {
            1 -> offset += 2 + readUnsignedShort(classBytes, offset)
            3, 4, 9, 10, 11, 12, 17, 18 -> offset += 4
            5, 6 -> {
                offset += 8
                index++
            }
            7, 8, 16, 19, 20 -> offset += 2
            15 -> offset += 3
            else -> error("Unsupported constant pool tag $tag")
        }
        index++
    }

    val extraConstant = value.toByteArray(Charsets.UTF_8)
    require(extraConstant.size <= 65535) {
        "UTF-8 constant is too large"
    }

    val updatedClassBytes = ByteArray(classBytes.size + 3 + extraConstant.size)
    classBytes.copyInto(updatedClassBytes, endIndex = offset)
    writeUnsignedShort(updatedClassBytes, 8, constantPoolCount + 1)

    var writeOffset = offset
    updatedClassBytes[writeOffset++] = 1
    writeUnsignedShort(updatedClassBytes, writeOffset, extraConstant.size)
    writeOffset += 2
    extraConstant.copyInto(updatedClassBytes, writeOffset)
    writeOffset += extraConstant.size
    classBytes.copyInto(
        destination = updatedClassBytes,
        destinationOffset = writeOffset,
        startIndex = offset
    )

    return updatedClassBytes
}

private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

private fun writeUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 8).toByte()
    bytes[offset + 1] = value.toByte()
}
