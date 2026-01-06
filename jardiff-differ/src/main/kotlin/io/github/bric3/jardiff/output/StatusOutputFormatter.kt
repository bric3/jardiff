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
 * Status output formatter that shows a two-column XY status for each file.
 *
 * Similar to `git status --short`:
 * - "D " - File deleted from/missing on left (exists on right only)
 * - " D" - File deleted from/missing on right (exists on left only)
 * - "M " - File modified (exists on both sides with changes)
 * - "  " - File unchanged (exists on both sides without changes)
 */
class StatusOutputFormatter : OutputFormatter() {
    override fun onFileProcessed(logger: Logger, data: FileComparisonData) {
        val status = when {
            !data.leftExists && data.rightExists -> "${logger.red("D ")} ${data.path}"
            data.leftExists && !data.rightExists -> "${logger.red(" D")} ${data.path}"
            data.unifiedDiff.isNotEmpty() -> "${logger.red("M ")} ${data.path}"
            else -> "${logger.green("  ")} ${data.path}"
        }
        logger.stdout(status)
    }
}