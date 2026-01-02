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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a path to be diffed, either a JAR file or a directory.
 *
 * @param leftOrRight Indicates whether the path is for the left or right side of the diff
 * @param path The file system path
 */
sealed class PathToDiff(val leftOrRight: LeftOrRight, val path: Path) {
    /**
     * Indicates whether the path is for the left or right side of the diff.
     */
    enum class LeftOrRight { LEFT, RIGHT }

    /**
     * Represents a JAR file path to be diffed.
     */
    class Jar(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)
    /**
     * Represents a directory path to be diffed.
     */
    class Directory(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)

    override fun toString(): String {
        return "$leftOrRight: $path"
    }

    companion object {
        /**
         * Factory method to create a [PathToDiff] instance based on the file type.
         *
         * @param leftOrRight Indicates whether the path is for the left or right side of the diff
         * @param path The file system path
         * @return A [PathToDiff] instance representing either a JAR file or a directory
         * @throws IllegalArgumentException if the file type is unsupported
         */
        fun of(leftOrRight: LeftOrRight, path: Path): PathToDiff = when {
            Files.isDirectory(path) -> Directory(leftOrRight, path)
            path.toString().endsWith(".jar", ignoreCase = true) -> Jar(leftOrRight, path)
            else -> throw IllegalArgumentException("Unsupported file type: $path")
        }
    }
}