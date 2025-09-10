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
import java.io.Closeable
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.streams.asSequence

class Differ(
    private val left: PathToDiff,
    private val right: PathToDiff,
    private val excludes: Set<String>,
    private val addtionalClassExtensions: Set<String> = emptySet(),
) : AutoCloseable {
    private val childCloseables = mutableSetOf<Closeable>()

    fun diff() {
        val leftEntries = fileEntries(left)
        val rightEntries = fileEntries(right)

        // leftEntries and rightEntries may not be symmetric
        makeListOfFilesToDiff(leftEntries, rightEntries).forEach {
            val leftLines = readFileAsTextIfPossible(it.left, addtionalClassExtensions)
            val rightLines = readFileAsTextIfPossible(it.right, addtionalClassExtensions)

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

            if (unifiedDiff.size > 0) {
                Logger.stdout("${Logger.RED}⨯${Logger.RESET} ${it.path}")
                unifiedDiff.forEach(Logger::stdout)
            } else {
                Logger.stdout("${Logger.GREEN}✔${Logger.RESET}️ ${it.path}")
            }
        }
    }

    private fun makeListOfFilesToDiff(
        leftEntries: Map<Path, FileAccess>,
        rightEntries: Map<Path, FileAccess>
    ): List<FileEntryToDiff> {
        val allKeys = (leftEntries + rightEntries).keys
        val changes = buildList {
            allKeys.forEach {
                add(FileEntryToDiff(it, leftEntries[it], rightEntries[it]))
            }
        }
        return changes
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
        val excludeMatchers = excludes.map {
            fileSystem.getPathMatcher("glob:$it")
        }

        return { file ->
            excludeMatchers.none {
                it.matches(file.relativePath)
            }.also { accepted ->
                if (!accepted) {
                    Logger.verbose("Excluded: ${file.relativePath}")
                }
            }
        }
    }

    override fun close() {
        childCloseables.forEach {
            try {
                it.close()
            } catch (e: Exception) {
                Logger.stderr("Error closing resource: ${e.message}")
            }
        }
    }
    data class FileEntryToDiff(
        val path: Path,
        val left: FileAccess?,
        val right: FileAccess?,
    )
}
