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

import io.github.bric3.jardiff.output.DiffOutputFormatter
import io.github.bric3.jardiff.output.OutputFormatter
import io.github.bric3.jardiff.output.StatisticsOutputFormatter
import io.github.bric3.jardiff.output.StatusOutputFormatter

/**
 * Output modes for the application.
 *
 * Each mode produces a different [OutputFormatter] implementation.
 */
@Suppress("EnumEntryName") // used for cli
enum class OutputMode(producer: () -> OutputFormatter) {
    /**
     * Short status output mode.
     *
     * Shows two-column XY status for each file (like git status --short).
     */
    status({ StatusOutputFormatter() }),

    /**
     * Statistics output mode.
     *
     * Shows file-by-file statistics with additions/deletions (like git diff --stat).
     */
    stat({ StatisticsOutputFormatter() }),

    /**
     * Detailed output mode.
     *
     * Show detailed differences between files.
     */
    diff({ DiffOutputFormatter() })
    ;

    /** The formatter instance produced by this mode */
    val formatter by lazy { producer() }
}