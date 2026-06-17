/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.jcod

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class JcodAsmToolsCompatibilityTest {
    @Test
    fun `emitted jcod assembles back to identical class bytes`(@TempDir tempDir: Path) {
        val originalBytes = classBytes()
        val jcod = JcodTextifier().toText(ByteArrayInputStream(originalBytes))
        val jcodFile = tempDir.resolve("JcodAsmToolsCompatibilityTest.jcod")
        val outputDir = tempDir.resolve("out")
        Files.writeString(jcodFile, jcod)
        Files.createDirectories(outputDir)

        runAsmTools("jcoder", "-d", outputDir.toString(), jcodFile.toString())

        val generatedClass = Files.walk(outputDir).use { stream ->
            stream
                .filter { it.extension == "class" }
                .findFirst()
                .orElseThrow { AssertionError("No class file generated under $outputDir") }
        }

        assertThat(Files.readAllBytes(generatedClass)).isEqualTo(originalBytes)
    }

    @ParameterizedTest
    @MethodSource("openJdkJcodFiles")
    fun `OpenJDK JCod files round-trip through the textifier`(
        jcodFile: Path,
        @TempDir tempDir: Path
    ) {
        val regeneratedJcodDir = tempDir.resolve("regenerated-jcod")
        val regeneratedClassDir = tempDir.resolve("regenerated-classes")
        val outputDir = tempDir.resolve("original")

        assembleClassesFromJcod(jcodFile, outputDir)
            .map { AssembledClass(jcodFile, outputDir, it) }
            .forEachIndexed { index, originalClass ->
                assertTextifiedClassRoundTrips(
                    originalClass,
                    regeneratedJcodDir,
                    regeneratedClassDir.resolve(index.toString())
                )
            }
    }

    /**
     * Assembles `.jcod` file into `.class` files using AsmTools and outputs them to the specified directory.
     *
     * Note a `jcod` file act as a bundle that can contain multiple classes.
     *
     * @param jcodFile the path to the `.jcod` file to assemble.
     * @param outputDir the path to the output directory where assembled `.class` files will be stored.
     * @return a list of paths to the assembled `.class` files within the output directory.
     */
    private fun assembleClassesFromJcod(jcodFile: Path, outputDir: Path): List<Path> {
        Files.createDirectories(outputDir)
        runAsmTools("jcoder", "-d", outputDir.toString(), jcodFile.toString())

        return classFilesUnder(outputDir).also {
            assertThat(it)
                .describedAs("$jcodFile assembled class files")
                .isNotEmpty()
        }
    }

    private fun classBytes(): ByteArray {
        val resourceName = "${JcodAsmToolsCompatibilityTest::class.java.name.replace('.', '/')}.class"
        return checkNotNull(JcodAsmToolsCompatibilityTest::class.java.classLoader.getResourceAsStream(resourceName)) {
            "Could not load test class resource $resourceName"
        }.use { it.readBytes() }
    }

    private fun runAsmTools(vararg args: String) {
        val asmtoolsJar = Path.of(System.getProperty("jardiff.asmtools.jar"))
        val command = listOf(
            Path.of(System.getProperty("java.home")).resolve("bin/java").toString(),
            "-jar",
            asmtoolsJar.toString()
        ) + args
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        assertThat(exitCode)
            .describedAs(output)
            .isEqualTo(0)
    }

    private fun assertTextifiedClassRoundTrips(
        originalClass: AssembledClass,
        regeneratedJcodDir: Path,
        regeneratedClassDir: Path
    ) {
        val regeneratedJcod = regeneratedJcodDir.resolve(originalClass.relativePath.withExtension("jcod"))
        Files.createDirectories(regeneratedJcod.parent)

        val jcodClassText = JcodTextifier().toText(ByteArrayInputStream(Files.readAllBytes(originalClass.file)))
        assertUsesOpenJdkTopLevelDeclaration(originalClass, jcodClassText)
        Files.writeString(regeneratedJcod, jcodClassText)

        val regeneratedClass = assembleSingleClassFromJcod(originalClass, regeneratedJcod, regeneratedClassDir)

        assertThat(Files.readAllBytes(regeneratedClass))
            .describedAs(originalClass.displayName)
            .isEqualTo(Files.readAllBytes(originalClass.file))
    }

    private fun assembleSingleClassFromJcod(
        originalClass: AssembledClass,
        regeneratedJcod: Path,
        outputDir: Path
    ): Path {
        val regeneratedClasses = assembleClassesFromJcod(regeneratedJcod, outputDir)
        assertThat(regeneratedClasses)
            .describedAs("Regenerated class files for ${originalClass.displayName}")
            .hasSize(1)
        return regeneratedClasses.single()
    }

    private data class AssembledClass(
        val sourceJcod: Path,
        val root: Path,
        val file: Path
    ) {
        val relativePath: Path = root.relativize(file)
        val displayName: String = "$sourceJcod -> $relativePath"
    }

    private fun classFilesUnder(directory: Path): List<Path> {
        return Files.walk(directory).use { stream ->
            stream
                .filter { it.extension == "class" }
                .sorted()
                .toList()
        }
    }

    private fun Path.withExtension(extension: String): Path {
        return (parent ?: Path.of("")).resolve("$nameWithoutExtension.$extension")
    }

    private fun assertUsesOpenJdkTopLevelDeclaration(originalClass: AssembledClass, jcodClassText: String) {
        val emittedDeclaration = jcodClassText.firstJcodDeclaration()
        if (emittedDeclaration.isRawByteDeclaration()) {
            assertThat(jcodClassText)
                .describedAs("${originalClass.displayName} raw JCod fallback")
                .contains("// JCodTextifier could not parse this class structurally.")
            return
        }

        assertThat(openJdkTopLevelDeclarations(originalClass.sourceJcod))
            .describedAs("${originalClass.displayName} OpenJDK top-level declarations")
            .contains(emittedDeclaration)
    }

    private fun String.firstJcodDeclaration(): String {
        return lineSequence()
            .map { it.trimEnd() }
            .first { it.isJcodTopLevelDeclaration() }
            .normalizeJcodDeclaration()
    }

    private fun openJdkTopLevelDeclarations(sourceJcod: Path): List<String> {
        return Files.readAllLines(sourceJcod)
            .asSequence()
            .map { it.trimEnd() }
            .filter { it.isJcodTopLevelDeclaration() }
            .map { it.normalizeJcodDeclaration() }
            .toList()
    }

    private fun String.isJcodTopLevelDeclaration(): Boolean {
        return startsWith("class ") || startsWith("file \"") || startsWith("module ")
    }

    private fun String.isRawByteDeclaration(): Boolean {
        return startsWith("file \"class-bytes.class\" Bytes[")
    }

    private fun String.normalizeJcodDeclaration(): String {
        return "${substringBefore("{").trimEnd()} {"
    }

    companion object {
        @JvmStatic
        fun openJdkJcodFiles(): Stream<Named<Path>> {
            val root = Path.of(System.getProperty("jardiff.openjdk.jcod.dir"))
            return Files.walk(root).use { stream ->
                stream
                    .filter { it.extension == "jcod" }
                    .sorted()
                    .map { named(openJdkJcodDisplayName(root, it), it) }
                    .toList()
                    .stream()
            }
        }

        private fun openJdkJcodDisplayName(root: Path, jcodFile: Path): String {
            val relativePath = root.relativize(jcodFile)
            return if (relativePath.nameCount > 1) {
                relativePath.subpath(1, relativePath.nameCount).toString()
            } else {
                relativePath.toString()
            }
        }
    }
}
