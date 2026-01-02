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
 * Output modes for the application.
 */
@Suppress("EnumEntryName") // used for cli
enum class OutputMode {
    /**
     * Simple output mode.
     *
     * Only show whether files are identical or different.
     */
    simple,

    /**
     * Detailed output mode.
     *
     * Show detailed differences between files.
     */
    diff,
}