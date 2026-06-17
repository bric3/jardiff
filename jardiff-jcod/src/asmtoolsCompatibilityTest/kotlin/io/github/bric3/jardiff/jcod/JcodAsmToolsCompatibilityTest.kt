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

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentest4j.TestAbortedException
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

        val jcodClassText = textifyOrAbort(originalClass)
        Files.writeString(regeneratedJcod, jcodClassText)

        val regeneratedClass = tryAssembleSingleClassFromJcod(originalClass, regeneratedJcod, regeneratedClassDir)

        assertThat(Files.readAllBytes(regeneratedClass))
            .describedAs(originalClass.displayName)
            .isEqualTo(Files.readAllBytes(originalClass.file))
    }

    private fun tryAssembleSingleClassFromJcod(
        originalClass: AssembledClass,
        regeneratedJcod: Path,
        outputDir: Path
    ): Path = try {
        assembleClassesFromJcod(regeneratedJcod, outputDir).single()
    } catch (error: AssertionError) {
        throw TestAbortedException(
            "Regenerated JCod is not accepted by AsmTools for ${originalClass.displayName}",
            error
        )
    }

    /**
     * Converts the bytecode of an assembled class file into a textual representation or aborts the test
     * if the class file is malformed or uses an unsupported structure.
     *
     * Some OpenJDK's jcod files are intentionally crafted to produce malformed class files.
     * In that case, the produced output is not interesting for us and we can abort the test.
     *
     * @param originalClass the assembled class to be textified, containing its source, root, and file path.
     * @return the textual representation of the class file.
     * @throws TestAbortedException if the class file is malformed or uses an unsupported format.
     */
    private fun textifyOrAbort(originalClass: AssembledClass): String {
        return try {
            JcodTextifier().toText(ByteArrayInputStream(Files.readAllBytes(originalClass.file)))
        } catch (exception: IllegalArgumentException) {
            throw TestAbortedException(
                "OpenJDK fixture assembles to a malformed class file: ${originalClass.displayName}",
                exception
            )
        } catch (exception: IllegalStateException) {
            throw TestAbortedException(
                "OpenJDK fixture uses an unsupported class-file shape: ${originalClass.displayName}",
                exception
            )
        }
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
