package io.github.bric3.jardiff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.streams.asSequence

class Differ(
    val left: PathToDiff,
    val right: PathToDiff,
) {
    fun diff() {
        val leftEntries = lineStream(left)
        val rightEntries = lineStream(right)

        // leftLines or rightLines may not be symmetric
        val listOfFilesToDiff = makeListOfFilesToDiff(leftEntries, rightEntries)

        listOfFilesToDiff.forEach {
            val leftLines = FileReader.readFileAsTextIfPossible(it.left)
            val rightLines = FileReader.readFileAsTextIfPossible(it.right)

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
                Logger.stdout(
                    "${Logger.GREEN}✔${Logger.RESET}️ ${it.path}"
                )
            }
        }
    }

    private fun makeListOfFilesToDiff(leftEntries: Map<Path, FileLines>, rightEntries: Map<Path, FileLines>): List<FileEntryToDiff> {
        val allKeys = (leftEntries + rightEntries).keys
        val changes = buildList<FileEntryToDiff> {
            allKeys.forEach {
                add(FileEntryToDiff(it, leftEntries.get(it), rightEntries.get(it)))
            }
        }
        return changes
    }

    companion object {
        private fun readDirectoryEntries(path: Path): Map<Path, FileLines> =
            Files.walk(path).asSequence()
                .filter { Files.isRegularFile(it) }
                .map { FileLines(path, path.relativize(it)) }
                .associateBy { it.relativePath }

        private fun lineStream(pathToDiff: PathToDiff): Map<Path, FileLines> {
            return when (pathToDiff) {
                is PathToDiff.Jar -> TODO("JarUtils.readJarEntries(left.path)")
                is PathToDiff.Directory -> readDirectoryEntries(pathToDiff.path)
            }
        }
    }
}

data class FileEntryToDiff(
    val path: Path,
    val left: FileLines?,
    val right: FileLines?,
)

data class FileLines(
    val parentPath: Path,
    val relativePath: Path,
) : Comparable<FileLines> {
    val lines by lazy { Files.readAllLines(parentPath.resolve(relativePath)) }
    val inputStream by lazy { Files.newInputStream(parentPath.resolve(relativePath)).buffered() }
    override fun compareTo(other: FileLines): Int {
        return relativePath.compareTo(other.relativePath)
    }
}

sealed class PathToDiff(val leftOrRight: LeftOrRight, val path: Path) {
    enum class LeftOrRight { LEFT, RIGHT }

    class Jar(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)
    class Directory(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)
    companion object {
        fun of(leftOrRight: LeftOrRight, path: Path): PathToDiff {
            return when {
                Files.isDirectory(path) -> Directory(leftOrRight, path)
                path.toString().endsWith(".jar", ignoreCase = true) -> Jar(leftOrRight, path)
                else -> throw IllegalArgumentException("Unsupported file type: $path")
            }
        }
    }
}