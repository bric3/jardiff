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

/**
 * Strategy for formatting diff output.
 *
 * Implementations format the comparison results in different styles
 * (status, statistics, or full diff).
 */
sealed class OutputFormatter {
    /**
     * Called for each file being compared.
     *
     * @param logger Logger for output
     * @param data File comparison data
     */
    abstract fun onFileProcessed(
        logger: Logger,
        data: FileComparisonData
    )

    /**
     * Called after all files have been processed.
     *
     * Use this for outputting summaries or accumulated statistics.
     *
     * @param logger Logger for output
     */
    open fun onComplete(logger: Logger) { }
}