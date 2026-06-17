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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
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
    fun `OpenJDK classes decompile like AsmTools jdec`(
        jcodFile: Path,
        @TempDir tempDir: Path
    ) {
        assumeTrue(
            !jcodFile.isMalformedModuleLengthFixture(),
            "AsmTools jdec folds following attributes into these intentionally overlong module attributes"
        )
        assumeTrue(
            !jcodFile.isAsmToolsJdecHangFixture(),
            "AsmTools jdec does not terminate for this intentionally malformed attribute-length fixture"
        )

        val classDir = tempDir.resolve("classes")
        val asmToolsJcodDir = tempDir.resolve("asmtools-jcod")
        val textifierJcodDir = tempDir.resolve("textifier-jcod")

        var comparedClasses = 0
        assembleClassesFromJcod(jcodFile, classDir)
            .map { AssembledClass(jcodFile, classDir, it) }
            .forEachIndexed { index, assembledClass ->
                val asmToolsJcod = decompileClassWithAsmTools(
                    assembledClass,
                    asmToolsJcodDir.resolve(index.toString())
                ) ?: return@forEachIndexed
                val textifierJcod = writeTextifierJcod(assembledClass, textifierJcodDir)

                comparedClasses++
                assertUsesExpectedTopLevelDeclaration(
                    originalClass = assembledClass,
                    asmToolsJcod = asmToolsJcod,
                    textifierJcod = textifierJcod
                )
                assertJcodEssentiallyMatches(
                    expected = asmToolsJcod,
                    actual = textifierJcod,
                    description = assembledClass.displayName
                )
            }
        assumeTrue(comparedClasses > 0, "AsmTools jdec could not decompile any class from $jcodFile")
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
        val result = execAsmTools(args.toList())
        assertThat(result.timedOut)
            .describedAs(result.output)
            .isFalse()
        assertThat(result.exitCode)
            .describedAs(result.output)
            .isEqualTo(0)
    }

    private fun execAsmTools(args: List<String>, timeoutSeconds: Long = ASMTOOLS_TIMEOUT_SECONDS): AsmToolsResult {
        val asmtoolsJar = Path.of(System.getProperty("jardiff.asmtools.jar"))
        val command = listOf(
            Path.of(System.getProperty("java.home")).resolve("bin/java").toString(),
            "-jar",
            asmtoolsJar.toString()
        ) + args
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor()
            val output = process.outputText()
            return AsmToolsResult(
                exitCode = null,
                output = output + "\nTimed out after ${timeoutSeconds}s: ${command.joinToString(" ")}",
                timedOut = true
            )
        }
        val output = process.outputText()
        return AsmToolsResult(process.exitValue(), output, timedOut = false)
    }

    private fun Process.outputText(): String {
        return runCatching {
            inputStream.bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            "Could not read AsmTools output: ${error.message}"
        }
    }

    private fun decompileClassWithAsmTools(
        originalClass: AssembledClass,
        outputDir: Path
    ): Path? {
        Files.createDirectories(outputDir)
        val result = execAsmTools(
            listOf("jdec", "-g", "-d", outputDir.toString(), originalClass.file.toString()),
            timeoutSeconds = ASMTOOLS_JDEC_TIMEOUT_SECONDS
        )
        if (result.timedOut || result.exitCode != 0) {
            return null
        }

        val decompiledJcodFiles = jcodFilesUnder(outputDir)
        assertThat(decompiledJcodFiles)
            .describedAs("AsmTools jdec files for ${originalClass.displayName}")
            .hasSize(1)
        return decompiledJcodFiles.single()
    }

    private data class AsmToolsResult(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean
    )

    private fun writeTextifierJcod(
        originalClass: AssembledClass,
        outputDir: Path
    ): Path {
        val generatedJcod = outputDir.resolve(originalClass.relativePath.withExtension("jcod"))
        Files.createDirectories(generatedJcod.parent)

        val jcodClassText = JcodTextifier().toText(ByteArrayInputStream(Files.readAllBytes(originalClass.file)))
        Files.writeString(generatedJcod, jcodClassText)
        return generatedJcod
    }

    private fun assertJcodEssentiallyMatches(expected: Path, actual: Path, description: String) {
        assertThat(Files.readString(actual).toComparableJcod())
            .describedAs(
                "$description%n" +
                    "AsmTools jdec: $expected%n" +
                    "JCodTextifier: $actual"
            )
            .isEqualTo(Files.readString(expected).toComparableJcod())
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

    private fun jcodFilesUnder(directory: Path): List<Path> {
        return Files.walk(directory).use { stream ->
            stream
                .filter { it.extension == "jcod" }
                .sorted()
                .toList()
        }
    }

    private fun Path.withExtension(extension: String): Path {
        return (parent ?: Path.of("")).resolve("$nameWithoutExtension.$extension")
    }

    private fun Path.isMalformedModuleLengthFixture(): Boolean {
        val normalizedPath = toString().replace('\\', '/')
        return "/test/jdk/java/lang/module/badclasses/BadModule" in normalizedPath &&
            normalizedPath.endsWith("/module-info.jcod")
    }

    private fun Path.isAsmToolsJdecHangFixture(): Boolean {
        return toString().replace('\\', '/')
            .endsWith("/test/langtools/tools/javap/attribute_length/JavapBug.jcod")
    }

    private fun assertUsesExpectedTopLevelDeclaration(
        originalClass: AssembledClass,
        asmToolsJcod: Path,
        textifierJcod: Path
    ) {
        val textifierJcodText = Files.readString(textifierJcod)
        val emittedDeclaration = textifierJcodText.firstJcodDeclaration()
        if (emittedDeclaration.isRawByteDeclaration()) {
            assertThat(textifierJcodText)
                .describedAs("${originalClass.displayName} raw JCod fallback")
                .contains("// JCodTextifier could not parse this class structurally.")
            return
        }

        val expectedDeclarations = openJdkTopLevelDeclarations(originalClass.sourceJcod) +
            Files.readString(asmToolsJcod).firstJcodDeclaration()

        assertThat(expectedDeclarations.distinct())
            .describedAs("${originalClass.displayName} top-level declarations")
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

    private fun String.toComparableJcod(): String {
        val lines = lines()
        val tokens = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val attribute = lines[index].attributeHeader()
            if (attribute != null && attribute.name !in structurallyComparedAttributes) {
                tokens += "Attr(#${attribute.nameIndex},${attribute.length}){${attribute.name}:opaque}"
                index = lines.indexAfterJcodBlock(index)
            } else {
                val line = lines[index].withoutJcodLineComment().collapseJcodWhitespace().trim()
                if (line.isNotEmpty()) {
                    tokens += line
                }
                index++
            }
        }

        return tokens.joinToString(" ")
            .replace(Regex("""(0x[0-9A-Fa-f]+);"""), "$1")
            .replace(Regex(""";\s*}"""), " }")
            .replace(Regex("""\s+}"""), " }")
    }

    private fun String.attributeHeader(): AttributeHeader? {
        val match = attributeHeaderRegex.find(this) ?: return null
        return AttributeHeader(
            nameIndex = match.groupValues[1],
            length = match.groupValues[2],
            name = match.groupValues[3]
        )
    }

    private fun List<String>.indexAfterJcodBlock(startIndex: Int): Int {
        var index = startIndex
        var depth = 0
        do {
            depth += this[index].jcodBraceDepthDelta()
            index++
        } while (index < size && depth > 0)
        return index
    }

    private fun String.jcodBraceDepthDelta(): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var index = 0

        while (index < length) {
            val char = this[index]
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                inString && char == '"' -> inString = false
                !inString && char == '"' -> inString = true
                !inString && char == '/' && getOrNull(index + 1) == '/' -> return depth
                !inString && char == '{' -> depth++
                !inString && char == '}' -> depth--
            }
            index++
        }

        return depth
    }

    private fun String.withoutJcodLineComment(): String {
        var inString = false
        var escaped = false
        var index = 0

        while (index < length) {
            val char = this[index]
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                inString && char == '"' -> inString = false
                !inString && char == '"' -> inString = true
                !inString && char == '/' && getOrNull(index + 1) == '/' -> return substring(0, index)
            }
            index++
        }

        return this
    }

    private fun String.collapseJcodWhitespace(): String = buildString {
        var inString = false
        var escaped = false
        var pendingWhitespace = false

        for (char in this@collapseJcodWhitespace) {
            when {
                escaped -> {
                    append(char)
                    escaped = false
                }
                inString && char == '\\' -> {
                    append(char)
                    escaped = true
                }
                inString && char == '"' -> {
                    append(char)
                    inString = false
                }
                !inString && char == '"' -> {
                    if (pendingWhitespace && isNotEmpty()) {
                        append(' ')
                    }
                    pendingWhitespace = false
                    append(char)
                    inString = true
                }
                inString -> append(char)
                char.isWhitespace() -> pendingWhitespace = true
                else -> {
                    if (pendingWhitespace && isNotEmpty()) {
                        append(' ')
                    }
                    pendingWhitespace = false
                    append(char)
                }
            }
        }
    }

    private data class AttributeHeader(
        val nameIndex: String,
        val length: String,
        val name: String
    )

    companion object {
        private const val ASMTOOLS_TIMEOUT_SECONDS = 30L
        private const val ASMTOOLS_JDEC_TIMEOUT_SECONDS = 10L

        private val attributeHeaderRegex = Regex("""^\s*Attr\(#(\d+),\s*(\d+)\)\s*\{\s*//\s*([A-Za-z0-9_$]+)""")

        private val structurallyComparedAttributes = setOf(
            "Code",
            "ConstantValue",
            "Deprecated",
            "EnclosingMethod",
            "Exceptions",
            "InnerClasses",
            "LineNumberTable",
            "LocalVariableTable",
            "LocalVariableTypeTable",
            "MethodParameters",
            "ModuleMainClass",
            "NestHost",
            "NestMembers",
            "PermittedSubclasses",
            "Signature",
            "SourceFile",
            "Synthetic"
        )

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
