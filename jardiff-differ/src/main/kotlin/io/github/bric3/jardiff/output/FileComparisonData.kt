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

/**
 * Data about a file comparison, passed to output formatters.
 *
 * This is a simplified DTO that provides formatters with only the information
 * they need, without exposing internal FileAccess details.
 *
 * @param path Relative path of the file being compared
 * @param leftExists Whether the file exists in the left side
 * @param rightExists Whether the file exists in the right side
 * @param unifiedDiff The unified diff lines (empty if no changes)
 */
data class FileComparisonData(
    val path: String,
    val leftExists: Boolean,
    val rightExists: Boolean,
    val unifiedDiff: List<String>
)