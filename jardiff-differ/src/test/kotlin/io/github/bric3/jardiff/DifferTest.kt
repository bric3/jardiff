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

import io.github.bric3.jardiff.Logger.Companion.green
import io.github.bric3.jardiff.Logger.Companion.red
import io.github.bric3.jardiff.OutputMode.diff
import io.github.bric3.jardiff.OutputMode.stat
import io.github.bric3.jardiff.OutputMode.`status`
import io.github.bric3.jardiff.classes.ClassTextifierProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path

class DifferTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should detect no differences in same jar using simple mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(status, singleClassJar, singleClassJar)

        assertThat(output).isEqualTo(
            """
            ${green("  ")} META-INF/MANIFEST.MF
            ${green("  ")} io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    @Test
    fun `should detect differences in same jar using diff mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(diff, singleClassJar, singleClassJar)

        assertThat(output).isEmpty()
    }

    @Test
    fun `should detect differences between directory and jar using simple mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(status, fixtureClassesOutput, singleClassJar, excludes = setOf("*.md", "*.properties"))

        assertThat(output).isEqualTo(
            """
            ${red("D ")} META-INF/MANIFEST.MF
            ${red(" D")} META-INF/jardiff-differ_testFixtures.kotlin_module
            ${green("  ")} io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    @Test
    fun `should detect single differences between directory and jar using diff mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(diff, fixtureClassesOutput, singleClassJar, excludes = setOf("*.md", "*.properties"))

        assertThat(output).isEqualTo(
            """
            --- /dev/null
            +++ META-INF/MANIFEST.MF
            @@ -0,0 +1,3 @@
            +Manifest-Version: 1.0
            +Created-By: TestUtils
            +
            --- META-INF/jardiff-differ_testFixtures.kotlin_module
            +++ /dev/null
            @@ -1,1 +1,0 @@
            -FILE SHA-1: f5d556b1d36c1500736285c79e957a9363d2a4ac
            """.trimIndent()
        )
    }

    @Test
    fun `should find no difference on coalesced classes using simple mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            { it.replace(".class", ".classdata") },
            FooFixtureClass::class.path
        )

        val output = diff(
            outputMode = status,
            left = fixtureClassesOutput,
            right = singleClassJar,
            excludes = setOf("*.md", "*.properties"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            ${red("D ")} META-INF/MANIFEST.MF
            ${red(" D")} META-INF/jardiff-differ_testFixtures.kotlin_module
            ${green("  ")} io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    @Test
    fun `should find no difference on coalesced classes using diff mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            { it.replace(".class", ".classdata") },
            FooFixtureClass::class.path
        )

        val output = diff(diff,
            fixtureClassesOutput,
            singleClassJar,
            excludes = setOf("*.md", "*.properties"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            --- /dev/null
            +++ META-INF/MANIFEST.MF
            @@ -0,0 +1,3 @@
            +Manifest-Version: 1.0
            +Created-By: TestUtils
            +
            --- META-INF/jardiff-differ_testFixtures.kotlin_module
            +++ /dev/null
            @@ -1,1 +1,0 @@
            -FILE SHA-1: f5d556b1d36c1500736285c79e957a9363d2a4ac
            """.trimIndent()
        )
    }

    @Test
    fun `should show differences when coalescing disabled due to multiple class files using simple mode`() {
        val seenOnce = mutableSetOf<String>()
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            {
                if (seenOnce.add(it)) {
                    it.replace(".class", ".classdata")
                } else {
                    it
                }
            },
            FooFixtureClass::class.path, // renamed with .classdata
            FooFixtureClass::class.path, // keep the .class
        )

        val output = diff(
            outputMode = status,
            left = FooFixtureClass::class.location,
            right = singleClassJar,
            excludes = setOf("*.md", "*.properties", "**/TestClassWithSynthetics*.class"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            ${red("M ")} META-INF/MANIFEST.MF
            ${red(" D")} META-INF/jardiff-differ_testFixtures.kotlin_module
            ${green("  ")} io/github/bric3/jardiff/FooFixtureClass.class
            ${red("D ")} io/github/bric3/jardiff/FooFixtureClass.classdata
            """.trimIndent()
        )
    }

    @Test
    fun `should show differences when coalescing disabled due to multiple class files using diff mode`() {
        val seenOnce = mutableSetOf<String>()
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            {
                if (seenOnce.add(it)) {
                    it.replace(".class", ".classdata")
                } else {
                    it
                }
            },
            FooFixtureClass::class.path, // renamed with .classdata
            FooFixtureClass::class.path, // keep the .class
        )

        val output = diff(diff,
            FooFixtureClass::class.location,
            singleClassJar,
            excludes = setOf("*.md", "*.properties", "**/TestClassWithSynthetics*.class"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            --- META-INF/MANIFEST.MF
            +++ META-INF/MANIFEST.MF
            @@ -1,2 +1,3 @@
             Manifest-Version: 1.0
            +Created-By: TestUtils
             
            --- META-INF/jardiff-differ_testFixtures.kotlin_module
            +++ /dev/null
            @@ -1,1 +1,0 @@
            -FILE SHA-1: f5d556b1d36c1500736285c79e957a9363d2a4ac
            --- /dev/null
            +++ io/github/bric3/jardiff/FooFixtureClass.classdata
            @@ -0,0 +1,37 @@
            +// class version 55.0 (55)
            +// access flags 0x31
            +public final class io/github/bric3/jardiff/FooFixtureClass {
            +
            +  // compiled from: FooFixtureClass.kt
            +
            +  @Lkotlin/Metadata;(mv={2, 3, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0003\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"Lio/github/bric3/jardiff/FooFixtureClass;", "", "<init>", "()V", "bar", "", "jardiff-differ_testFixtures"})
            +
            +  // access flags 0x1
            +  public <init>()V
            +   L0
            +    LINENUMBER 13 L0
            +    ALOAD 0
            +    INVOKESPECIAL java/lang/Object.<init> ()V
            +    RETURN
            +   L1
            +    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L1 0
            +    MAXSTACK = 1
            +    MAXLOCALS = 1
            +
            +  // access flags 0x11
            +  public final bar()V
            +   L0
            +    LINENUMBER 15 L0
            +    LDC "Hello, World!"
            +    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
            +    SWAP
            +    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/Object;)V
            +   L1
            +    LINENUMBER 16 L1
            +    RETURN
            +   L2
            +    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L2 0
            +    MAXSTACK = 2
            +    MAXLOCALS = 1
            +}
            +
            """.trimIndent()
        )
    }

    @Test
    fun `should show statistics using stat mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(stat, fixtureClassesOutput, singleClassJar, excludes = setOf("*.md", "*.properties"))

        assertThat(output).isEqualTo(
            """
            | META-INF/MANIFEST.MF                               | 3 ${green("+++")}${red("")}
            | META-INF/jardiff-differ_testFixtures.kotlin_module | 1 ${green("")}${red("-")}
            | io/github/bric3/jardiff/FooFixtureClass.class      | 0
            | 2 files changed, 3 insertions(+), 1 deletions(-)
            """.trimMargin() // seems like trimIndent() eats the leading space if each has one.
        )
    }

    @Test
    fun `should return false when no differences found`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val hasDifferences = Differ(
            logger = Logger(StringWriter(), StringWriter(), verbosity(0)),
            outputMode = status,
            classTextifierProducer = ClassTextifierProducer.`asm-textifier`,
            left = PathToDiff.of(PathToDiff.LeftOrRight.LEFT, singleClassJar),
            right = PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, singleClassJar),
            excludes = emptySet(),
            coalesceClassFileWithExtensions = emptySet(),
        ).use { it.diff() }

        assertThat(hasDifferences).isFalse()
    }

    @Test
    fun `should return true when differences found`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val hasDifferences = Differ(
            logger = Logger(StringWriter(), StringWriter(), verbosity(0)),
            outputMode = status,
            classTextifierProducer = ClassTextifierProducer.`asm-textifier`,
            left = PathToDiff.of(PathToDiff.LeftOrRight.LEFT, fixtureClassesOutput),
            right = PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, singleClassJar),
            excludes = emptySet(),
            coalesceClassFileWithExtensions = emptySet(),
        ).use { it.diff() }

        assertThat(hasDifferences).isTrue()
    }

    private fun diff(
        outputMode: OutputMode,
        left: Path,
        right: Path,
        excludes: Set<String> = emptySet(),
        coalesceClassFileWithExts: Set<String> = emptySet(),
    ): String {
        val output = StringWriter()
        Differ(
            logger = Logger(output, StringWriter(), verbosity(0)),
            outputMode = outputMode,
            classTextifierProducer = ClassTextifierProducer.`asm-textifier`,
            left = PathToDiff.of(PathToDiff.LeftOrRight.LEFT, left),
            right = PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, right),
            excludes = excludes,
            coalesceClassFileWithExtensions = coalesceClassFileWithExts,
        ).use { it.diff() }
        return output.toString().trimEnd()
    }

    private fun verbosity(level: Int) = BooleanArray(level)
}