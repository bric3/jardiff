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

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import io.github.bric3.jardiff.FileReader.readFileAsTextIfPossible
import io.github.bric3.jardiff.Logger.Companion.green
import io.github.bric3.jardiff.Logger.Companion.red
import io.github.bric3.jardiff.OutputMode.diff
import io.github.bric3.jardiff.OutputMode.stat
import io.github.bric3.jardiff.OutputMode.`status`
import io.github.bric3.jardiff.classes.ClassTextifierProducer
import java.io.Closeable
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.streams.asSequence

/**
 * Build a diff engine to compares two paths (JAR files or directories) and reports differences.
 *
 * @param logger Logger for output messages.
 * @param outputMode Mode for outputting differences.
 * @param classTextifierProducer Producer for class textifiers.
 * @param left Path to the left side of the diff.
 * @param right Path to the right side of the diff.
 * @param includes Set of glob patterns to include files.
 * @param excludes Set of glob patterns to exclude files.
 * @param coalesceClassFileWithExtensions Set of file extensions to coalesce with .class files.
 */
class Differ @JvmOverloads constructor(
    private val logger: Logger,
    private val outputMode: OutputMode,
    private val classTextifierProducer: ClassTextifierProducer,
    private val left: PathToDiff,
    private val right: PathToDiff,
    private val includes: Set<String> = emptySet(),
    private val excludes: Set<String> = emptySet(),
    private val coalesceClassFileWithExtensions: Set<String> = emptySet(),
) : AutoCloseable {
    private val childCloseables = mutableSetOf<Closeable>()

    /**
     * Perform the diff operation between the left and right paths.
     *
     * @return True if differences were found, false otherwise.
     */
    fun diff(): Boolean {
        val leftEntries = fileEntries(left)
        val rightEntries = fileEntries(right)

        var hasDifferences = false
        val fileStats = mutableListOf<FileStat>()

        // leftEntries and rightEntries may not be symmetric
        makeListOfFilesToDiff(leftEntries, rightEntries).forEach {
            val leftLines = readFileAsTextIfPossible(it.left, classTextifierProducer, coalesceClassFileWithExtensions)
            val rightLines = readFileAsTextIfPossible(it.right, classTextifierProducer, coalesceClassFileWithExtensions)

            val patch = DiffUtils.diff(
                leftLines,
                rightLines
            )

            val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                it.left?.relativePath?.toString(),
                it.right?.relativePath?.toString(),
                leftLines,
                patch,
                4
            )

            if (unifiedDiff.isNotEmpty()) {
                hasDifferences = true
            }

            when (outputMode) {
                status -> {
                    val status = when {
                        it.left == null && it.right != null -> "${red("D ")} ${it.path}"
                        it.left != null && it.right == null -> "${red(" D")} ${it.path}"
                        unifiedDiff.isNotEmpty() -> "${red("M ")} ${it.path}"
                        else -> "${green("  ")} ${it.path}"
                    }
                    logger.stdout(status)
                }

                stat -> {
                    // Collect statistics for all files, output at the end
                    val (additions, deletions) = countChanges(unifiedDiff)
                    fileStats.add(FileStat(it.path, additions, deletions))
                }

                diff -> unifiedDiff.forEach(logger::stdout)
            }
        }

        // Output stat mode summary at the end
        if (outputMode == stat) {
            outputStatSummary(fileStats)
        }

        return hasDifferences
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

    private fun outputStatSummary(fileStats: List<FileStat>) {
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

    private fun makeListOfFilesToDiff(
        leftEntries: Map<Path, FileAccess>,
        rightEntries: Map<Path, FileAccess>
    ): List<FileEntryToDiff> {
        val classExtensions = coalesceClassFileWithExtensions + "class"

        val allKeys = (leftEntries + rightEntries).keys
            .asSequence()
            .sorted()
            .map { path ->
                when {
                    path.extension in classExtensions -> {
                        CoalescedClassEntry(
                            path.parent?.pathString ?: "",
                            path.nameWithoutExtension,
                        ).apply {
                            originalPath = path
                        }
                    }

                    else -> RegularEntry(path)
                }

            }

        val coalescedEntries = mutableSetOf<CoalescedClassEntry>()
        return buildList {
            allKeys.forEach { entry ->
                when (entry) {
                    is CoalescedClassEntry -> {
                        val aliasedPaths = classExtensions.map {
                            Path.of(entry.parentPath, "${entry.classFileName}.$it")
                        }

                        val leftClasses = aliasedPaths.mapNotNull { leftEntries[it] }
                        val rightClasses = aliasedPaths.mapNotNull { rightEntries[it] }

                        // do not coalesce if multiple entries on the same side
                        when {
                            (leftClasses.count() <= 1) and (rightClasses.count() <= 1) -> {
                                // entry can be coalesced, but only if it wasn't already added
                                if (coalescedEntries.add(entry)) {
                                    logger.verbose2("Coalesced entry ${entry.originalPath}")
                                    add(
                                        FileEntryToDiff(
                                            Path.of(entry.parentPath, "${entry.classFileName}.class").toString(),
                                            leftClasses.singleOrNull(),
                                            rightClasses.singleOrNull(),
                                        )
                                    )
                                }
                            }
                            else -> {
                                logger.verbose2("Coalescing disabled for ${entry.originalPath} due to multiple files one one side")
                                add(
                                    FileEntryToDiff(
                                        entry.originalPath.toString(),
                                        leftEntries[entry.originalPath],
                                        rightEntries[entry.originalPath],
                                    )
                                )
                            }
                        }
                    }

                    is RegularEntry -> {
                        add(
                            FileEntryToDiff(
                                entry.path.pathString,
                                leftEntries[entry.path],
                                rightEntries[entry.path]
                            )
                        )
                    }
                }
            }
        }
    }

    private fun fileEntries(pathToDiff: PathToDiff): Map<Path, FileAccess> {
        val pathFilter = pathFilter()
        return when (pathToDiff) {
            is PathToDiff.Jar -> {
                val jf = JarFile(pathToDiff.path.toFile()).also {
                    childCloseables.add(it)
                }
                jf.entries().asSequence()
                    .filter { it.isDirectory.not() }
                    .map { FileAccess.FromJar(pathToDiff.path, Path.of(it.name), jf) }
                    .filter(pathFilter)
                    .associateBy { it.relativePath }
            }

            is PathToDiff.Directory -> {
                Files.walk(pathToDiff.path).asSequence()
                    .filter { Files.isRegularFile(it) }
                    .map { FileAccess.FromDirectory(pathToDiff.path, pathToDiff.path.relativize(it)) }
                    .filter(pathFilter)
                    .associateBy { it.relativePath }
            }
        }
    }

    private fun pathFilter(): (FileAccess) -> Boolean {
        val fileSystem = FileSystems.getDefault()

        val includeMatchers = includes.map { pattern ->
            val normalizedPattern = normalizePattern(pattern)
            fileSystem.getPathMatcher("glob:$normalizedPattern")
        }

        val excludeMatchers = excludes.map { pattern ->
            val normalizedPattern = normalizePattern(pattern)
            fileSystem.getPathMatcher("glob:$normalizedPattern")
        }

        return { file ->
            // If includes is specified, file must match at least one include pattern
            val matchesInclude = if (includeMatchers.isEmpty()) {
                true
            } else {
                includeMatchers.any { it.matches(file.relativePath) }
            }

            // Then check if file is excluded
            val notExcluded = excludeMatchers.none {
                it.matches(file.relativePath)
            }

            val accepted = matchesInclude && notExcluded

            if (!accepted) {
                if (!matchesInclude) {
                    logger.verbose2("Not included: ${file.relativePath}")
                } else {
                    logger.verbose2("Excluded: ${file.relativePath}")
                }
            }

            accepted
        }
    }

    private fun normalizePattern(pattern: String): String {
        // If pattern doesn't contain / or **, treat it as a filename pattern
        // Use glob syntax {pattern,**/pattern} to match files at root level AND in subdirectories
        return when {
            pattern.contains('/') -> pattern
            pattern.startsWith("**") -> pattern
            else -> "{$pattern,**/$pattern}"
        }
    }

    override fun close() {
        childCloseables.forEach {
            try {
                it.close()
            } catch (e: IOException) {
                logger.stderr("Error closing resource: ${e.message}")
            }
        }
    }

    sealed class FileEntry
    data class CoalescedClassEntry(
        val parentPath: String,
        val classFileName: String,
    ) : FileEntry() {
        lateinit var originalPath: Path
    }

    data class RegularEntry(
        val path: Path
    ) : FileEntry()

    data class FileEntryToDiff(
        val path: String,
        val left: FileAccess?,
        val right: FileAccess?,
    )
}
