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

sealed class PathToDiff(val leftOrRight: LeftOrRight, val path: Path) {
    enum class LeftOrRight { LEFT, RIGHT }

    class Jar(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)
    class Directory(leftOrRight: LeftOrRight, path: Path) : PathToDiff(leftOrRight, path)

    override fun toString(): String {
        return "$leftOrRight: $path"
    }

    companion object {
        fun of(leftOrRight: LeftOrRight, path: Path): PathToDiff = when {
            Files.isDirectory(path) -> Directory(leftOrRight, path)
            path.toString().endsWith(".jar", ignoreCase = true) -> Jar(leftOrRight, path)
            else -> throw IllegalArgumentException("Unsupported file type: $path")
        }
    }
}