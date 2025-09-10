/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.app

import io.github.bric3.jardiff.Differ
import io.github.bric3.jardiff.Logger
import io.github.bric3.jardiff.PathToDiff
import io.github.bric3.jardiff.PathToDiff.LeftOrRight.LEFT
import io.github.bric3.jardiff.PathToDiff.LeftOrRight.RIGHT
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

@Command(
    name = "jardiff",
    mixinStandardHelpOptions = true,
    version = ["jardiff 1.0"],
    description = ["Compares two JAR files or directories and reports differences."]
)
class Main : Runnable {
    @Parameters(
        arity = "2",
        paramLabel = "<left> <right>",
        description = ["The JAR file or directory to compare."]
    )
    lateinit var files: List<String>

    @Option(
        names = ["-e", "--exclude"],
        arity = "1",
        paramLabel = "<glob>",
        description = ["A glob exclude pattern, like **/raw*/**, or **/*.bin"],
        defaultValue = ""
    )
    lateinit var excludes: Set<String>

    override fun run() {
        val left = PathToDiff.of(LEFT, makePath(files[0]))
        val right = PathToDiff.of(RIGHT, makePath(files[1]))

        Logger.stdout(
            """
            Comparing:
            * ${left.path}
            * ${right.path}
            
            """.trimIndent()
        )

        Differ(
            left = left,
            right = right,
            excludes = excludes
        ).use {
            it.diff()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            exitProcess(CommandLine(Main()).execute(*args))
        }

        fun makePath(it: String) : Path = Path.of(it).also {
            if (Files.exists(it).not()) {
                Logger.stderr("File or directory does not exist: $it")
                exitProcess(1)
            }
        }
    }
}

