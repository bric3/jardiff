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
import io.github.bric3.jardiff.Logger.Companion.green
import io.github.bric3.jardiff.Logger.Companion.red

/**
 * Statistics output formatter that shows file-by-file statistics with additions/deletions.
 *
 * Similar to `git diff --stat`, displays each file with a visual bar showing the proportion
 * of additions (+) and deletions (-), followed by a summary line with totals.
 *
 * This formatter is stateful - it collects statistics during onFileProcessed calls
 * and outputs the complete report in onComplete.
 */
class StatisticsOutputFormatter : OutputFormatter() {
    private val fileStats = mutableListOf<FileStat>()

    override fun onFileProcessed(logger: Logger, data: FileComparisonData) {
        // Collect statistics for all files, output at the end
        val (additions, deletions) = countChanges(data.unifiedDiff)
        fileStats.add(FileStat(data.path, additions, deletions))
    }

    override fun onComplete(logger: Logger) {
        outputStatSummary(logger, fileStats)
    }

    private data class FileStat(
        val path: String,
        val additions: Int,
        val deletions: Int
    )

    private fun countChanges(unifiedDiff: List<String>): Pair<Int, Int> {
        var additions = 0
        var deletions = 0

        unifiedDiff.forEach { line ->
            when {
                line.startsWith("+") && !line.startsWith("+++") -> additions++
                line.startsWith("-") && !line.startsWith("---") -> deletions++
            }
        }

        return Pair(additions, deletions)
    }

    private fun outputStatSummary(logger: Logger, fileStats: List<FileStat>) {
        if (fileStats.isEmpty()) {
            return
        }

        val maxPathLength = fileStats.maxOf { it.path.length }
        val maxChanges = fileStats.maxOf { it.additions + it.deletions }
        val maxBarWidth = 50 // Maximum width for the visual bar

        fileStats.forEach { stat ->
            val totalChanges = stat.additions + stat.deletions
            if (totalChanges == 0) {
                // File exists on both sides but no changes
                logger.stdout(" ${stat.path.padEnd(maxPathLength)} | 0")
                return@forEach
            }

            // Calculate bar width proportional to changes
            val barWidth = if (maxChanges > maxBarWidth) {
                ((totalChanges.toDouble() / maxChanges) * maxBarWidth).toInt().coerceAtLeast(1)
            } else {
                totalChanges
            }

            val additionsBar = "+".repeat((stat.additions.toDouble() / totalChanges * barWidth).toInt())
            val deletionsBar = "-".repeat(barWidth - additionsBar.length)

            val changesStr = "$totalChanges ${green(additionsBar)}${red(deletionsBar)}"
            logger.stdout(" ${stat.path.padEnd(maxPathLength)} | $changesStr")
        }

        // Summary line
        val totalFiles = fileStats.count { it.additions + it.deletions > 0 }
        val totalAdditions = fileStats.sumOf { it.additions }
        val totalDeletions = fileStats.sumOf { it.deletions }

        logger.stdout(" $totalFiles files changed, $totalAdditions insertions(+), $totalDeletions deletions(-)")
    }
}