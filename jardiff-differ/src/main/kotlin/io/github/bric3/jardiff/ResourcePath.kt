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

import java.nio.file.Path

/**
 * Relative resource path normalized to '/' separators, independent of the host platform.
 *
 * This is used for both JAR entry names and equivalent paths in exploded directories.
 */
@JvmInline
value class ResourcePath private constructor(val path: String) : Comparable<ResourcePath> {
    val extension: String
        get() = fileName.substringAfterLast('.', missingDelimiterValue = "")

    val fileName: String
        get() = path.substringAfterLast('/')

    val nameWithoutExtension: String
        get() = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)

    val parentPath: String
        get() = path.substringBeforeLast('/', missingDelimiterValue = "")

    fun toPath(): Path = Path.of(path)

    override fun compareTo(other: ResourcePath): Int =
        path.compareTo(other.path)

    override fun toString(): String = path

    companion object {
        fun of(path: String): ResourcePath {
            require(path.isNotEmpty()) { "Resource path must not be empty" }
            require(path[0] != '/') { "Resource path must be relative: $path" }
            require('\\' !in path) { "Resource path must use '/' separators: $path" }
            return ResourcePath(path)
        }

        fun of(parentPath: String, fileName: String): ResourcePath =
            of(if (parentPath.isEmpty()) fileName else "$parentPath/$fileName")

        fun fromPath(path: Path): ResourcePath =
            of(path.joinToString("/") { it.toString() })
    }
}
