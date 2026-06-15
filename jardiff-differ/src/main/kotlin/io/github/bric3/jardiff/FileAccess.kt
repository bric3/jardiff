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

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile

sealed class FileAccess(
    val parentPath: Path,
    val relativePath: Path,
) : Comparable<FileAccess> {
    abstract val size: Long?
    abstract fun openBufferedInputStream(): BufferedInputStream

    override fun compareTo(other: FileAccess): Int {
        return relativePath.compareTo(other.relativePath)
    }

    override fun toString(): String {
        return "FileAccess(parentPath=$parentPath, relativePath=$relativePath)"
    }

    class FromJar(
        parentPath: Path,
        relativePath: Path,
        private val jarFile: JarFile,
        private val jarEntry: JarEntry = jarFile.getJarEntry(relativePath.toString())
            ?: throw IllegalArgumentException("Jar entry not found: $relativePath")
    ) : FileAccess(parentPath, relativePath) {
        override val size: Long? = jarEntry.size.takeIf { it >= 0 }

        override fun openBufferedInputStream(): BufferedInputStream =
            jarFile.getInputStream(jarEntry).buffered()
    }

    class FromDirectory(
        parentPath: Path,
        relativePath: Path
    ) : FileAccess(parentPath, relativePath) {
        override val size: Long? by lazy { Files.size(parentPath.resolve(relativePath)) }

        override fun openBufferedInputStream(): BufferedInputStream =
            Files.newInputStream(parentPath.resolve(relativePath)).buffered()
    }
}
