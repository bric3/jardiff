/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.classes

import java.io.InputStream

/**
 * Abstract class for producing a text representation from a Class [java.io.InputStream].
 */
sealed class ClassTextifier() {
    /**
     * Convert the given [inputStream] representing a class file into a list of lines.
     */
    abstract fun toLines(inputStream: InputStream): List<String>

    /**
     * Convert the given [inputStream] representing a class file into a single text block.
     */
    open fun toText(inputStream: InputStream) = toLines(inputStream).joinToString("\n")
}
