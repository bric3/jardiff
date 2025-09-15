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
import io.github.bric3.jardiff.OutputMode.simple
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

        val output = diff(simple, singleClassJar, singleClassJar)

        assertThat(output).isEqualTo(
            """
            ${green("✔")}️ META-INF/MANIFEST.MF
            ${green("✔")}️ io/github/bric3/jardiff/FooFixtureClass.class
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
    fun `should detect differences between jar and directory using simple mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(simple, FooFixtureClass::class.location, singleClassJar, excludes = setOf("*.md", "*.properties"))

        assertThat(output).isEqualTo(
            """
            ${red("⨯")} META-INF/MANIFEST.MF
            ${red("⨯")} META-INF/jardiff-differ_testFixtures.kotlin_module
            ${green("✔")}️ io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    @Test
    fun `should detect single differences between jar and directory using diff mode`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(diff, FooFixtureClass::class.location, singleClassJar, excludes = setOf("*.md", "*.properties"))

        assertThat(output).isEqualTo(
            """
            --- META-INF/MANIFEST.MF
            +++ META-INF/MANIFEST.MF
            @@ -1,2 +1,1 @@
            -Manifest-Version: 1.0
            -
            +BINARY FILE SHA-1: ba8ab5a0280b953aa97435ff8946cbcbb2755a27
            --- META-INF/jardiff-differ_testFixtures.kotlin_module
            +++ /dev/null
            @@ -1,1 +1,0 @@
            -BINARY FILE SHA-1: c4ab3f5c96ccba90c685717137ef543a3b2c30d9
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

        val output = diff(simple,
            FooFixtureClass::class.location,
            singleClassJar,
            excludes = setOf("*.md", "*.properties"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            ${red("⨯")} META-INF/MANIFEST.MF
            ${red("⨯")} META-INF/jardiff-differ_testFixtures.kotlin_module
            ${green("✔")}️ io/github/bric3/jardiff/FooFixtureClass.class
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
            FooFixtureClass::class.location,
            singleClassJar,
            excludes = setOf("*.md", "*.properties"),
            coalesceClassFileWithExts = setOf("classdata")
        )

        assertThat(output).describedAs("no class differences").isEqualTo(
            """
            --- META-INF/MANIFEST.MF
            +++ META-INF/MANIFEST.MF
            @@ -1,2 +1,1 @@
            -Manifest-Version: 1.0
            -
            +BINARY FILE SHA-1: ba8ab5a0280b953aa97435ff8946cbcbb2755a27
            --- META-INF/jardiff-differ_testFixtures.kotlin_module
            +++ /dev/null
            @@ -1,1 +1,0 @@
            -BINARY FILE SHA-1: c4ab3f5c96ccba90c685717137ef543a3b2c30d9
            """.trimIndent()
        )
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
            left = PathToDiff.of(PathToDiff.LeftOrRight.LEFT, left),
            right = PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, right),
            excludes = excludes,
            coalesceClassFileWithExtensions = coalesceClassFileWithExts,
        ).diff()
        return output.toString().trim()
    }

    private fun verbosity(level: Int) = BooleanArray(level)
}