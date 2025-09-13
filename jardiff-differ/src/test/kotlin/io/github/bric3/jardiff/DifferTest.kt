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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path

class DifferTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should detect no differences interface same jar`() {
        val singleClassJar = createJarFromResources(
            tempDir,
            FooFixtureClass::class.java.classLoader,
            FooFixtureClass::class.path
        )
        val output = StringWriter()

        Differ(
            Logger(output, StringWriter(), verbosity(1)),
            PathToDiff.of(PathToDiff.LeftOrRight.LEFT, singleClassJar),
            PathToDiff.of(PathToDiff.LeftOrRight.RIGHT, singleClassJar),
        ).diff()

        assertThat(output.toString().trim()).isEqualTo(
            """
            ${green("✔")}️ META-INF/MANIFEST.MF
            ${green("✔")}️ io/github/bric3/jardiff/FooFixtureClass.class
            """.trimIndent()
        )
    }

    private fun verbosity(level: Int) = BooleanArray(level)
}