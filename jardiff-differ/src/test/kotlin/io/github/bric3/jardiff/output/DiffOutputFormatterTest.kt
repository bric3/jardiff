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

class DiffOutputFormatterTest {
    @Test
    fun `should output unified diff lines`() {
        val formatter = DiffOutputFormatter()
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
                    "@@ -1,1 +1,1 @@",
                    "-old line",
                    "+new line"
                )
            )
        )

        assertThat(output.toString()).isEqualTo(
            """
            --- a/test.txt
            +++ b/test.txt
            @@ -1,1 +1,1 @@
            -old line
            +new line

            """.trimIndent()
        )
    }

    @Test
    fun `should output nothing when diff is empty`() {
        val formatter = DiffOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("test.txt", true, true, emptyList())
        )

        assertThat(output.toString()).isEmpty()
    }

    @Test
    fun `should output diffs for multiple files`() {
        val formatter = DiffOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData(
                "file1.txt",
                true,
                true,
                listOf("--- a/file1.txt", "+++ b/file1.txt", "+added line")
            )
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData(
                "file2.txt",
                true,
                true,
                listOf("--- a/file2.txt", "+++ b/file2.txt", "-deleted line")
            )
        )

        assertThat(output.toString()).isEqualTo(
            """
            --- a/file1.txt
            +++ b/file1.txt
            +added line
            --- a/file2.txt
            +++ b/file2.txt
            -deleted line

            """.trimIndent()
        )
    }

    @Test
    fun `should handle mixed empty and non-empty diffs`() {
        val formatter = DiffOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onFileProcessed(
            logger,
            FileComparisonData("unchanged.txt", true, true, emptyList())
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData(
                "changed.txt",
                true,
                true,
                listOf("--- a/changed.txt", "+++ b/changed.txt", "+change")
            )
        )
        formatter.onFileProcessed(
            logger,
            FileComparisonData("unchanged2.txt", true, true, emptyList())
        )

        assertThat(output.toString()).isEqualTo(
            """
            --- a/changed.txt
            +++ b/changed.txt
            +change

            """.trimIndent()
        )
    }

    @Test
    fun `onComplete should not output anything`() {
        val formatter = DiffOutputFormatter()
        val output = StringWriter()
        val logger = Logger(output, StringWriter(), booleanArrayOf())

        formatter.onComplete(logger)

        assertThat(output.toString()).isEmpty()
    }
}