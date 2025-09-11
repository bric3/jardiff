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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.random.Random

class FileReaderTest {
    @Test
    fun `should textify class from jar`() {
        val fileAccess =
            FileAccess.FromJar(fixtureJar, Path.of(FooFixtureClass::class.path), JarFile(fixtureJar.toFile()))
        val textifiedClass = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textifiedClass).containsExactly(*fooFixtureClassLines)
    }

    @Test
    fun `should textify class from directory`() {
        val fileAccess = FileAccess.FromDirectory(fixtureClassesOutput, Path.of(FooFixtureClass::class.path))
        val textifiedClass = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textifiedClass).containsExactly(*fooFixtureClassLines)
    }

    @Test
    fun `should return a hash of binary files`(@TempDir tempDir: Path) {
        val binaryFile = Path.of("file.bin")
        Files.write(
            tempDir.resolve(binaryFile),
            // make the beginning not random, this is the beggining of a Matroska header
            ubyteArrayOf(0x1Au, 0x45u, 0xDFu, 0xA3u, 0x42u, 0x86u, 0x42u, 0xF7u, 0x42u, 0xF2u, 0x42u, 0xF3u).asByteArray() +
                    Random.nextBytes(1048)
        )

        val fileAccess = FileAccess.FromDirectory(tempDir, binaryFile)
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).element(0).asString().matches("BINARY FILE SHA-1: [0-9a-fA-F]{40}")
    }

    @Test
    fun `should properly read ISO-8859-1 text files`() {
        val fileAccess = FileAccess.FromDirectory(fixtureResources, Path.of("iso8859-1.properties"))
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).containsExactly(
            "#",
            "# jardiff",
            "#",
            "# Copyright (c) 2025 - Brice Dutheil",
            "#",
            "# This Source Code Form is subject to the terms of the Mozilla Public",
            "# License, v. 2.0. If a copy of the MPL was not distributed with this",
            "# file, You can obtain one at https://mozilla.org/MPL/2.0/.",
            "#",
            "",
            "# This is a properties file because encoding is supposed to be ISO-8859-1.",
            "# See Properties javadoc",
            "",
            "# Accented chars are improperly decoded when using an UTF-8 decoder",
            "# è é à â î ï ñ",
            ""
        )
    }

    @Test
    fun `should properly read UTF-8 text files`() {
        val fileAccess = FileAccess.FromDirectory(fixtureResources, Path.of("utf-8.md"))
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).containsExactly(
            "> [!IMPORTANT]",
            "> This file is supposed to be **UTF-8**",
            "",
            "> Café naïve jalapeño",
            "",
            "UTF-8 encodes é as two bytes (`0xC3` `0xA9`), but in Latin-1 those bytes show as “Ã©”.",
            "",
            "> The temperature is 19°C 🌤️",
            "",
            "* The degree sign (°) is `0xC2` `0xBA` in UTF-8.",
            "* The sun emoji (🌤️) is three code points. Wrong decoders may show gibberish or replacement boxes.",
            "",
            "> Привет мир (Hello world in Russian)",
            "> こんにちは世界 (Hello world in Japanese)",
            "> مرحبا بالعالم (Hello world in Arabic)",
            "",
            "These characters require 2–3 bytes each in UTF-8. Single-byte decoders will definitely break.",
            "",
            "> Café (the e + combining acute U+0301)",
            "",
            "This looks identical to “Café” but is actually two code points."
        )
    }

    @Test
    fun `should properly read UTF-16 text files`() {
        val fileAccess = FileAccess.FromDirectory(fixtureResources, Path.of("utf-16be.md"))
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).containsExactly(
            "﻿> [!IMPORTANT]",
            "> This file is supposed to be **UTF-16**",
            "",
            "> Café naïve jalapeño",
            "> The temperature is 19°C 🌤️",
            "> Привет мир (Hello world in Russian)",
            "> こんにちは世界 (Hello world in Japanese)",
            "> مرحبا بالعالم (Hello world in Arabic)",
            "> Café (the e + combining acute U+0301)"
        )
    }

    @Test
    fun `should properly read US_ASCII text files`() {
        val fileAccess = FileAccess.FromDirectory(fixtureResources, Path.of("us-ascii.md"))
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).containsExactly(
            "> [!IMPORTANT]",
            "> This file is supposed to be **US-ASCII**",
            "",
            "> Cafe naive jalapeno",
            "",
            "Characters are 7-bits"
        )
    }

    @Test
    fun `should properly read Windoes CP-1252 text files`() {
        val fileAccess = FileAccess.FromDirectory(fixtureResources, Path.of("cp1252.md"))
        val textContent = FileReader.readFileAsTextIfPossible(fileAccess)

        assertThat(textContent).containsExactly(
            "> [!IMPORTANT]",
            "> This file is supposed to be **Windows CP-1252**",
            "",
            "> Café naïve jalapeño",
            "> The temperature is 19ºC"
        )
    }

    private val fooFixtureClassLines = arrayOf(
        "// class version 68.0 (68)",
        "// access flags 0x31",
        "public final class io/github/bric3/jardiff/FooFixtureClass {",
        "",
        "  // compiled from: FooFixtureClass.kt",
        "",
        """  @Lkotlin/Metadata;(mv={2, 2, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0003\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"Lio/github/bric3/jardiff/FooFixtureClass;", "", "<init>", "()V", "bar", "", "jardiff-differ_testFixtures"})""",
        "",
        "  // access flags 0x1",
        "  public <init>()V",
        "   L0",
        "    LINENUMBER 13 L0",
        "    ALOAD 0",
        "    INVOKESPECIAL java/lang/Object.<init> ()V",
        "    RETURN",
        "   L1",
        "    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L1 0",
        "    MAXSTACK = 1",
        "    MAXLOCALS = 1",
        "",
        "  // access flags 0x11",
        "  public final bar()V",
        "   L0",
        "    LINENUMBER 15 L0",
        "    LDC \"Hello, World!\"",
        "    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;",
        "    SWAP",
        "    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/Object;)V",
        "   L1",
        "    LINENUMBER 16 L1",
        "    RETURN",
        "   L2",
        "    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L2 0",
        "    MAXSTACK = 2",
        "    MAXLOCALS = 1",
        "}",
        ""
    )
}