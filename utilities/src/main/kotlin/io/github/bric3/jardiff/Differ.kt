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
import java.io.BufferedInputStream
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.jar.JarFile
import kotlin.streams.asSequence

class Differ(
    val left: PathToDiff,
    val right: PathToDiff,
    private val classExtensions: List<String> = listOf("class")
) : AutoCloseable {
    private val childCloseables = mutableListOf<Closeable>()

    fun diff() {
        val leftEntries = left.fileEntries(childCloseables::add)
        val rightEntries = right.fileEntries(childCloseables::add)

        // leftEntries and rightEntries may not be symmetric
        makeListOfFilesToDiff(leftEntries, rightEntries).forEach {
            val leftLines = FileReader.readFileAsTextIfPossible(it.left, classExtensions)
            val rightLines = FileReader.readFileAsTextIfPossible(it.right, classExtensions)

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
                Logger.stdout("${Logger.RED}⨯${Logger.RESET}\uFE0F ${it.path}")
                unifiedDiff.forEach { line ->
                    Logger.stdout(line)
                }
            } else {
                Logger.stdout("${Logger.GREEN}✔${Logger.RESET}️ ${it.path}")
            }
        }
    }

    private fun makeListOfFilesToDiff(leftEntries: Map<Path, FileLines>, rightEntries: Map<Path, FileLines>): List<FileEntryToDiff> {
        val allKeys = (leftEntries + rightEntries).keys
        val changes = buildList {
            allKeys.forEach {
                add(FileEntryToDiff(it, leftEntries.get(it), rightEntries.get(it)))
            }
        }
        return changes
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
}

data class FileEntryToDiff(
    val path: Path,
    val left: FileLines?,
    val right: FileLines?,
)

sealed class FileLines(
    val parentPath: Path,
    val relativePath: Path,
) : Comparable<FileLines> {
    abstract val bufferedInputStream: BufferedInputStream
    override fun compareTo(other: FileLines): Int {
        return relativePath.compareTo(other.relativePath)
    }
    class FromJar(parentPath: Path, relativePath: Path, private val jarFile: JarFile) : FileLines(parentPath, relativePath) {
        override val bufferedInputStream: BufferedInputStream by lazy {
            jarFile.getInputStream(jarFile.getEntry(relativePath.toString())).buffered()
        }
    }
    class FromDirectory(parentPath: Path, relativePath: Path) : FileLines(parentPath, relativePath) {
        override val bufferedInputStream: BufferedInputStream by lazy {
            Files.newInputStream(parentPath.resolve(relativePath)).buffered()
        }
    }
}

sealed class PathToDiff(val leftOrRight: LeftOrRight, val path: Path) {
    enum class LeftOrRight { LEFT, RIGHT }
    abstract fun fileEntries(closeable: Consumer<Closeable>): Map<Path, FileLines>
    class Jar(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path) {
        override fun fileEntries(closeable: Consumer<Closeable>): Map<Path, FileLines> {
            val jf = JarFile(path.toFile()).also {
                closeable.accept(it)
            }
            return jf.entries().asSequence()
                .filter { it.isDirectory.not() }
                .map { FileLines.FromJar(path, Path.of(it.name), jf) }
                .associateBy { it.relativePath }
        }
    }

    class Directory(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path) {
        override fun fileEntries(closeable: Consumer<Closeable>): Map<Path, FileLines> {
            return Files.walk(path).asSequence()
                .filter { Files.isRegularFile(it) }
                .map { FileLines.FromDirectory(path, path.relativize(it)) }
                .associateBy { it.relativePath }
        }
    }

    companion object {
        fun of(leftOrRight: LeftOrRight, path: Path): PathToDiff = when {
            Files.isDirectory(path) -> Directory(leftOrRight, path)
            path.toString().endsWith(".jar", ignoreCase = true) -> Jar(leftOrRight, path)
            else -> throw IllegalArgumentException("Unsupported file type: $path")
        }
    }
}