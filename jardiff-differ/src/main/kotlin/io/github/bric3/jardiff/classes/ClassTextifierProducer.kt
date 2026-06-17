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

import io.github.bric3.jardiff.jcod.JcodTextifier
import io.github.bric3.jardiff.javap.JavapTextifier
import java.io.InputStream

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
    `class-outline`({ options -> ClassOutline(options) }),

    /**
     * [JcodTextifier] - produces a JCod-compatible class-file listing
     */
    jcod({ JcodClassTextifier }),

    /**
     * [JavapTextifier] - produces a javap verbose class-file listing using the JDK running jardiff
     */
    javap({ JavapClassTextifier })
    ;

    /** Create a [ClassTextifier] configured for one comparison run. */
    fun create(options: ClassTextOptions = ClassTextOptions()) = producer(options)
}

private data object JcodClassTextifier : ClassTextifier() {
    private val textifier = JcodTextifier()

    override fun toLines(inputStream: InputStream): List<String> = textifier.toLines(inputStream)
}

private data object JavapClassTextifier : ClassTextifier() {
    private val textifier = JavapTextifier()

    override fun toLines(inputStream: InputStream): List<String> = textifier.toLines(inputStream)
}
