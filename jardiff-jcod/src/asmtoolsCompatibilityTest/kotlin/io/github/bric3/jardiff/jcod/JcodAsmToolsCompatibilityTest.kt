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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

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

    private fun classBytes(): ByteArray {
        val resourceName = "${JcodAsmToolsCompatibilityTest::class.java.name.replace('.', '/')}.class"
        return checkNotNull(JcodAsmToolsCompatibilityTest::class.java.classLoader.getResourceAsStream(resourceName)) {
            "Could not load test class resource $resourceName"
        }.use { it.readBytes() }
    }
}
