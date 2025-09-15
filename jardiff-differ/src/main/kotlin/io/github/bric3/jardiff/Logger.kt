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

import java.io.PrintWriter
import java.io.StringWriter

class Logger(
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,
    verbosity: BooleanArray = booleanArrayOf(),
) {
    constructor(
        stdout: StringWriter,
        stderr: StringWriter,
        verbosity: BooleanArray
    ) : this(
        PrintWriter(stdout), PrintWriter(stderr), verbosity
    )

    private var level: Int

    init {
        require(verbosity.size <= MAX_VERBOSITY)
        level = verbosity.size
    }

    fun stdout(message: String) {
        // level 0
        stdout.println(message)
    }

    fun stderr(message: String) {
        stderr.println(message)
    }

    fun verbose1(msg: String) {
        if (level >= 1) {
            stderr(msg)
        }
        if (isDebugging) {
            debugLog(msg)
        }
    }

    fun verbose2(msg: String) {
        if (level >= 2) {
            stderr(msg)
        }
        if (isDebugging) {
            debugLog(msg)
        }
    }

    private fun debugLog(msg: String) {
        println(msg)
    }

    companion object {
        const val MAX_VERBOSITY = 2
        private const val GREEN = "\u001B[32m"
        private const val RED = "\u001B[31m"
        private const val RESET = "\u001B[0m"
        fun red(text: String) = "$RED$text$RESET"

        fun green(text: String) = "$GREEN$text$RESET"

        private val isDebugging by lazy {
            ProcessHandle.current().info()
                .arguments().map { args ->
                    args.any { it.startsWith("-agentlib:jdwp") }
                }.get()
        }
    }
}