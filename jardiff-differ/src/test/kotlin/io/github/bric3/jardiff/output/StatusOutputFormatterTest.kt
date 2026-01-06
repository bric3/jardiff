/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.output

import io.github.bric3.jardiff.ColorMode
import io.github.bric3.jardiff.Logger
import io.github.bric3.jardiff.green
import io.github.bric3.jardiff.red
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter

class StatusOutputFormatterTest {
    @Test
    fun `should show D  when file missing on left`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf(), ColorMode.always)

        formatter.onFileProcessed(
            logger,
            FileComparisonData("test.txt", leftExists = false, rightExists = true, unifiedDiff = emptyList())
        )

        assertThat(output.toString()).isEqualTo("${red("D ")} test.txt\n")
    }

    @Test
    fun `should show  D when file missing on right`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf(), ColorMode.always)

        formatter.onFileProcessed(
            logger,
            FileComparisonData("test.txt", leftExists = true, rightExists = false, unifiedDiff = emptyList())
        )

        assertThat(output.toString()).isEqualTo("${red(" D")} test.txt\n")
    }

    @Test
    fun `should show M  when file modified`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf(), ColorMode.always)

        formatter.onFileProcessed(
            logger,
            FileComparisonData("test.txt", leftExists = true, rightExists = true, unifiedDiff = listOf("--- a/test.txt", "+++ b/test.txt", "@@ -1,1 +1,1 @@", "-old", "+new"))
        )

        assertThat(output.toString()).isEqualTo("${red("M ")} test.txt\n")
    }

    @Test
    fun `should show    when file unchanged`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf(), ColorMode.always)

        formatter.onFileProcessed(
            logger,
            FileComparisonData("test.txt", leftExists = true, rightExists = true, unifiedDiff = emptyList())
        )

        assertThat(output.toString()).isEqualTo("${green("  ")} test.txt\n")
    }

    @Test
    fun `should show multiple files correctly`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf(), ColorMode.always)

        formatter.onFileProcessed(
            logger,
            FileComparisonData("file1.txt", leftExists = false, rightExists = true, unifiedDiff = emptyList())
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("file2.txt", leftExists = true, rightExists = false, unifiedDiff = emptyList())
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("file3.txt", leftExists = true, rightExists = true, unifiedDiff = listOf("+change"))
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("file4.txt", leftExists = true, rightExists = true, unifiedDiff = emptyList())
        )

        assertThat(output.toString()).isEqualTo(
            """
            ${red("D ")} file1.txt
            ${red(" D")} file2.txt
            ${red("M ")} file3.txt
            ${green("  ")} file4.txt

            """.trimIndent()
        )
    }

    @Test
    fun `onComplete should not output anything`() {
        val formatter = StatusOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onComplete(logger)

        assertThat(output.toString()).isEmpty()
    }
}