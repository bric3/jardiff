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
import io.github.bric3.jardiff.OutputMode.simple
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

class Differ(
    private val logger: Logger,
    private val outputMode: OutputMode,
    private val classTextifierProducer: ClassTextifierProducer,
    private val left: PathToDiff,
    private val right: PathToDiff,
    private val excludes: Set<String> = emptySet(),
    private val coalesceClassFileWithExtensions: Set<String> = emptySet(),
) : AutoCloseable {
    private val childCloseables = mutableSetOf<Closeable>()

    fun diff() {
        val leftEntries = fileEntries(left)
        val rightEntries = fileEntries(right)

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

            when (outputMode) {
                simple -> logger.stdout(
                    if (unifiedDiff.size > 0) {
                        "${red("⨯")} ${it.path}"
                    } else {
                        "${green("✔")}️ ${it.path}"
                    }
                )

                diff -> unifiedDiff.forEach(logger::stdout)
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
                            path.parent.pathString,
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
        val excludeMatchers = excludes.map {
            fileSystem.getPathMatcher("glob:$it")
        }

        return { file ->
            excludeMatchers.none {
                it.matches(file.relativePath)
            }.also { accepted ->
                if (!accepted) {
                    logger.verbose2("Excluded: ${file.relativePath}")
                }
            }
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
