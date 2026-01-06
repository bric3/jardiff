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

import io.github.bric3.jardiff.Logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter

class StatisticsOutputFormatterTest {
    @Test
    fun `should collect and format statistics for multiple files`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("file1.txt", true, true, listOf("+line1", "+line2", "-line3"))
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("file2.txt", true, true, listOf("+line4"))
        )

        formatter.onComplete(logger)

        val result = output.toString()
        assertThat(result).contains("2 files changed, 3 insertions(+), 1 deletions(-)")
    }

    @Test
    fun `should show file with no changes as 0`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("unchanged.txt", true, true, emptyList())
        )

        formatter.onComplete(logger)

        val result = output.toString()
        assertThat(result).contains(" unchanged.txt")
        assertThat(result).contains("| 0")
    }

    @Test
    fun `should count additions and deletions correctly`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData(
                "test.txt",
                true,
                true,
                listOf(
                    "--- a/test.txt",
                    "+++ b/test.txt",
                    "@@ -1,3 +1,4 @@",
                    " unchanged",
                    "-deleted1",
                    "-deleted2",
                    "+added1",
                    "+added2",
                    "+added3"
                )
            )
        )

        formatter.onComplete(logger)

        val result = output.toString()
        assertThat(result).contains("1 files changed, 3 insertions(+), 2 deletions(-)")
    }

    @Test
    fun `should ignore diff headers when counting changes`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData(
                "test.txt",
                true,
                true,
                listOf(
                    "--- a/test.txt",  // Should be ignored
                    "+++ b/test.txt",  // Should be ignored
                    "@@ -1,1 +1,1 @@",
                    "-old",
                    "+new"
                )
            )
        )

        formatter.onComplete(logger)

        val result = output.toString()
        assertThat(result).contains("1 files changed, 1 insertions(+), 1 deletions(-)")
    }

    @Test
    fun `should format visual bars proportionally`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("file1.txt", true, true, listOf("+added"))
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("file2.txt", true, true, listOf("-deleted"))
        )

        formatter.onComplete(logger)

        val result = output.toString()
        // Should show bars for both files
        assertThat(result).contains("file1.txt")
        assertThat(result).contains("file2.txt")
        assertThat(result).contains("+")
        assertThat(result).contains("-")
    }

    @Test
    fun `should not output anything when no files processed`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onComplete(logger)

        assertThat(output.toString()).isEmpty()
    }

    @Test
    fun `should align file paths with padding`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("short.txt", true, true, listOf("+a"))
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("very/long/path/file.txt", true, true, listOf("+b"))
        )

        formatter.onComplete(logger)

        val result = output.toString()
        val lines = result.lines()
        // Both lines should have '|' at similar positions due to padding
        val pipe1 = lines[0].indexOf('|')
        val pipe2 = lines[1].indexOf('|')
        assertThat(pipe1).isEqualTo(pipe2)
    }

    @Test
    fun `should only count files with changes in summary`() {
        val formatter = StatisticsOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("changed.txt", true, true, listOf("+change"))
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("unchanged.txt", true, true, emptyList())
        )

        formatter.onComplete(logger)

        val result = output.toString()
        // Only 1 file changed (the one with actual changes)
        assertThat(result).contains("1 files changed")
    }
}