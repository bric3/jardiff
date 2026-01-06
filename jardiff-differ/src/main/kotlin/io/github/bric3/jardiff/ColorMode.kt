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

/**
 * Color output modes for terminal display.
 *
 * Controls when ANSI color codes should be used in output.
 */
@Suppress("EnumEntryName") // used for cli
enum class ColorMode {
    /**
     * Always use color output, regardless of terminal capabilities.
     */
    always,

    /**
     * Automatically detect terminal capabilities and use color if supported.
     *
     * Colors are enabled when:
     * - stdout is connected to a terminal (TTY)
     * - TERM environment variable is set and not "dumb"
     * - NO_COLOR environment variable is not set
     */
    auto,

    /**
     * Never use color output.
     */
    never;

    /**
     * Determine if color should be enabled based on this mode and the environment.
     *
     * @return true if color should be enabled, false otherwise
     */
    fun shouldUseColor(): Boolean {
        return when (this) {
            always -> true
            never -> false
            auto -> {
                // Check if stdout is a TTY
                val isTty = System.console() != null

                // Check TERM environment variable
                val term = System.getenv("TERM")
                val termSupportsColor = term != null && term != "dumb"

                // Check NO_COLOR environment variable (https://no-color.org/)
                val noColor = System.getenv("NO_COLOR")
                val noColorSet = noColor != null && noColor.isNotEmpty()

                isTty && termSupportsColor && !noColorSet
            }
        }
    }
}