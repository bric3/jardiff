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

/**
 * Simple logger facade with verbosity levels.
 *
 * @param stdout Standard output writer
 * @param stderr Standard error writer
 * @param verbosity Verbosity levels as a boolean array
 * @param colorMode Color output mode for terminal display
 */
class Logger @JvmOverloads constructor(
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,
    verbosity: BooleanArray = booleanArrayOf(),
    private val colorMode: ColorMode = ColorMode.auto,
) {

    /**
     * Secondary constructor accepting [StringWriter]s for stdout and stderr.
     */
    constructor(
        stdout: StringWriter,
        stderr: StringWriter,
        verbosity: BooleanArray,
        colorMode: ColorMode = ColorMode.auto
    ) : this(
        PrintWriter(stdout), PrintWriter(stderr), verbosity, colorMode
    )

    private var level: Int
    private val useColor: Boolean = colorMode.shouldUseColor()

    init {
        require(verbosity.size <= MAX_VERBOSITY)
        level = verbosity.size
    }

    /**
     * Log a message to standard output.
     *
     * @param message The message to log
     */
    fun stdout(message: String) {
        // level 0
        stdout.println(message)
        debugLog(message)
    }

    /**
     * Log a message to standard error.
     *
     * @param message The message to log
     */
    fun stderr(message: String) {
        stderr.println(message)
        debugLog(message)
    }

    /**
     * Log a message if verbosity level is at least 1.
     *
     * @param msg The message to log
     */
    fun verbose1(msg: String) {
        if (level >= 1) {
            stderr(msg)
        }
        debugLog(msg)
    }

    /**
     * Log a message if verbosity level is at least 2.
     *
     * @param msg The message to log
     */
    fun verbose2(msg: String) {
        if (level >= 2) {
            stderr(msg)
        }
        debugLog(msg)
    }

    /**
     * Produce an ANSI red colorized text for terminal output.
     *
     * @param text The text to colorize
     * @return The colorized text, or plain text if color is disabled
     */
    fun red(text: String) = if (useColor) "$RED$text$RESET" else text

    /**
     * Produce an ANSI green colorized text for terminal output.
     *
     * @param text The text to colorize
     * @return The colorized text, or plain text if color is disabled
     */
    fun green(text: String) = if (useColor) "$GREEN$text$RESET" else text

    companion object {
        const val MAX_VERBOSITY = 2
        const val GREEN = "\u001B[32m"
        const val RED = "\u001B[31m"
        const val RESET = "\u001B[0m"

        private val isDebugging by lazy {
            ProcessHandle.current().info()
                .arguments().map { args ->
                    args.any { it.startsWith("-agentlib:jdwp") }
                }.get()
        }

        /**
         * Log a debug on message if debugging is enabled.
         *
         * This method is not using the standard output or error writers to avoid polluting test outputs.
         *
         * @param msg The message to log
         */
        fun debugLog(msg: String) {
            if (isDebugging) {
                println(msg)
            }
        }
    }
}