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
 * Diff output formatter that shows the full unified diff.
 *
 * This is the most detailed output mode, showing the actual line-by-line
 * differences in unified diff format.
 */
class DiffOutputFormatter : OutputFormatter() {
    override fun onFileProcessed(logger: Logger, data: FileComparisonData) {
        data.unifiedDiff.forEach(logger::stdout)
    }
}