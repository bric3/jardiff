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

object Logger {
    const val GREEN = "\u001B[32m"
    const val RED = "\u001B[31m"
    const val RESET = "\u001B[0m"

    private val isDebugging by lazy {
        ProcessHandle.current().info()
            .arguments().map { args ->
                args.any { it.startsWith("-agentlib:jdwp") }
            }.get()
    }

    fun stdout(message: String) {
        println(message)
    }
    fun stderr(message: String) {
        System.err.println(message)
    }

    fun verbose(msg: String) {
        if (isDebugging) {
            stdout(msg)
        }
    }
}