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

/**
 * Enum of available [ClassTextifier] producers.
 *
 * These are used to produce a text output from class files
 */
@Suppress("EnumEntryName", "unused")
enum class ClassTextifierProducer(private val producer: (ClassTextOptions) -> ClassTextifier) {
    /**
     * [AsmTextifier] - produces a text representation of the class using ASM's Textifier
     */
    `asm-textifier`({ options -> AsmTextifier(options = options) }),

    /**
     * [ClassFileMajorVersion] - produces a text representation of the class file major version
     */
    `class-file-version`({ ClassFileMajorVersion }),

    /**
     * [ClassOutline] - produces a text outline of the class structure
     */
    `class-outline`({ options -> ClassOutline(options) })
    // javap
    ;

    /** Create a [ClassTextifier] configured for one comparison run. */
    fun create(options: ClassTextOptions = ClassTextOptions()) = producer(options)
}
