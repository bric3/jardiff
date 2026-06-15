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
import io.github.bric3.jardiff.classes.ClassTextifierProducer
import io.github.bric3.jardiff.output.FileComparisonData
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
        val formatter = outputMode.formatter

        var hasDifferences = false

        // leftEntries and rightEntries may not be symmetric
        makeListOfFilesToDiff(leftEntries, rightEntries).forEach {
            val comparisonData = comparisonData(it)

            if (comparisonData.hasDifferences) {
                hasDifferences = true
            }

            formatter.onFileProcessed(logger, comparisonData)
        }

        formatter.onComplete(logger)

        return hasDifferences
    }

    private fun comparisonData(entry: FileEntryToDiff): FileComparisonData {
        val left = entry.left
        val right = entry.right

        if (left != null && right != null && haveSameBytes(left, right)) {
            return unchangedComparisonData(entry)
        }

        // In status mode, when a file exists only on one side (added/removed),
        // we can skip text comparison and report it immediately.
        val canSkipTextComparison = outputMode == OutputMode.status && (left == null || right == null)

        if (!canSkipTextComparison) {
            val leftLines = readFileAsTextIfPossible(left, classTextifierProducer, coalesceClassFileWithExtensions)
            val rightLines = readFileAsTextIfPossible(right, classTextifierProducer, coalesceClassFileWithExtensions)

            if (left != null && right != null && leftLines == rightLines) {
                return unchangedComparisonData(entry)
            }

            if (outputMode != OutputMode.status) {
                return diffComparisonData(entry, leftLines, rightLines)
            }
        }

        return statusComparisonData(entry, changed = !canSkipTextComparison)
    }

    private fun diffComparisonData(
        entry: FileEntryToDiff,
        leftLines: List<String>,
        rightLines: List<String>
    ): FileComparisonData {
        val patch = DiffUtils.diff(
            leftLines,
            rightLines
        )

        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            entry.left?.relativePath?.toString(),
            entry.right?.relativePath?.toString(),
            leftLines,
            patch,
            4
        )

        return FileComparisonData(
            path = entry.path,
            leftExists = entry.left != null,
            rightExists = entry.right != null,
            unifiedDiff = unifiedDiff
        )
    }

    private fun unchangedComparisonData(entry: FileEntryToDiff): FileComparisonData {
        return FileComparisonData(
            path = entry.path,
            leftExists = entry.left != null,
            rightExists = entry.right != null,
            unifiedDiff = emptyList()
        )
    }

    private fun statusComparisonData(entry: FileEntryToDiff, changed: Boolean): FileComparisonData {
        return FileComparisonData(
            path = entry.path,
            leftExists = entry.left != null,
            rightExists = entry.right != null,
            unifiedDiff = emptyList(),
            changed = changed
        )
    }

    private fun haveSameBytes(left: FileAccess, right: FileAccess): Boolean {
        val leftSize = left.size
        val rightSize = right.size
        if (leftSize != null && rightSize != null && leftSize != rightSize) {
            return false
        }

        left.openBufferedInputStream().use { leftInput ->
            right.openBufferedInputStream().use { rightInput ->
                val leftBuffer = ByteArray(8192)
                val rightBuffer = ByteArray(8192)

                while (true) {
                    val leftRead = leftInput.read(leftBuffer)
                    val rightRead = rightInput.read(rightBuffer)

                    if (leftRead != rightRead) {
                        return false
                    }
                    if (leftRead == -1) {
                        return true
                    }
                    for (i in 0 until leftRead) {
                        if (leftBuffer[i] != rightBuffer[i]) {
                            return false
                        }
                    }
                }
            }
        }
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
                    .map { FileAccess.FromJar(pathToDiff.path, Path.of(it.name), jf, it) }
                    .filter(pathFilter)
                    .associateBy { it.relativePath }
            }

            is PathToDiff.Directory -> {
                Files.walk(pathToDiff.path).use {
                    it.asSequence()
                        .filter { Files.isRegularFile(it) }
                        .map { FileAccess.FromDirectory(pathToDiff.path, pathToDiff.path.relativize(it)) }
                        .filter(pathFilter)
                        .associateBy { it.relativePath }
                }
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
