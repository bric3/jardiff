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
import io.github.bric3.jardiff.OutputMode.simple
import io.github.bric3.jardiff.classes.ClassTextifierProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path

class DifferExclusionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should exclude files matching simple extension pattern using simple mode`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("*.properties", "META-INF/MANIFEST.MF")
        )

        assertThat(output).doesNotContain("test.properties")
            .doesNotContain("META-INF/MANIFEST.MF")
            .contains("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should exclude files matching directory glob pattern using simple mode`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("META-INF/**")
        )

        assertThat(output).describedAs("META-INF directory should be excluded")
            .doesNotContain("META-INF/MANIFEST.MF")
            .doesNotContain("META-INF/jardiff-differ_testFixtures.kotlin_module")
            .doesNotContain("META-INF")
            .contains("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should exclude files matching nested file pattern using simple mode`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("**/MANIFEST.MF", "**/*.kotlin_module")
        )

        assertThat(output).describedAs("nested files matching pattern should be excluded")
            .doesNotContain("META-INF/MANIFEST.MF")
            .doesNotContain("jardiff-differ_testFixtures.kotlin_module")
            .contains("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should exclude files matching multiple glob patterns using simple mode`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("*.md", "*.properties", "META-INF/**")
        )

        assertThat(output).describedAs("all matching patterns should be excluded")
            .doesNotContain(".properties")
            .doesNotContain(".md")
            .doesNotContain("META-INF/")
            .contains("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should only show non-excluded differences when exclude pattern is applied`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(
            simple,
            fixtureClassesOutput,
            singleClassJar,
            excludes = setOf("META-INF/**")
        )

        // Should show the class file as matching, but not show META-INF files at all
        assertThat(output).describedAs("Ignored").isEqualTo(
            """
            ${green("✔")}️ io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    @Test
    fun `should exclude files with complex glob pattern matching filenames`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("**/jardiff*.kotlin_module")
        )

        assertThat(output).describedAs("complex pattern should exclude matching files")
            .doesNotContain("jardiff-differ_testFixtures.kotlin_module")
    }

    @Test
    fun `should exclude class files when pattern matches class extension`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("**/*.class")
        )

        assertThat(output).describedAs("all class files should be excluded")
            .doesNotContain(".class")
    }

    @Test
    fun `should exclude files with complex nested path and filename pattern`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("**/github/**/*Fixture*.class")
        )

        assertThat(output).describedAs("complex nested pattern should match path with 'github' and filename with 'Fixture'")
            .doesNotContain("FooFixtureClass.class")
            .doesNotContain("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should handle empty exclude set without errors`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )

        val output = diff(
            simple,
            fixtureClassesOutput,
            singleClassJar,
            excludes = emptySet()
        )

        // Should show all files when no exclusions
        assertThat(output).contains("META-INF/MANIFEST.MF")
            .contains("META-INF/jardiff-differ_testFixtures.kotlin_module")
            .contains("io/github/bric3/jardiff/FooFixtureClass.class")
    }

    @Test
    fun `should exclude all files from both sides with specific patterns`() {
        val jarWithClass = createJarWithClass()

        val output = diff(
            simple,
            fixtureClassesOutput,
            jarWithClass,
            excludes = setOf("META-INF/**", "**/*.class")
        )

        assertThat(output).describedAs("all files should be excluded")
            .isEmpty()
    }

    private fun createJarWithClass(): Path {
        return createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
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
            classTextifierProducer = ClassTextifierProducer.`asm-textifier`,
            left = PathToDiff.of(PathToDiff.LeftOrRight.LEFT, left),
            right = PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, right),
            excludes = excludes,
            coalesceClassFileWithExtensions = coalesceClassFileWithExts,
        ).use { it.diff() }
        return output.toString().trim()
    }

    private fun verbosity(level: Int) = BooleanArray(level)
}