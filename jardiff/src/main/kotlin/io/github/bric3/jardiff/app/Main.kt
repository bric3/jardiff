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
import io.github.bric3.jardiff.OutputMode
import io.github.bric3.jardiff.PathToDiff
import io.github.bric3.jardiff.PathToDiff.LeftOrRight.LEFT
import io.github.bric3.jardiff.PathToDiff.LeftOrRight.RIGHT
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Parameters
import picocli.CommandLine.RunLast
import picocli.CommandLine.Spec
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
        index = "0",
        description = ["The JAR file or directory to compare."]
    )
    lateinit var left: String

    @Parameters(
        index = "1",
        description = ["The JAR file or directory to compare."]
    )
    lateinit var right: String

    @Option(
        names = ["-e", "--exclude"],
        arity = "1",
        paramLabel = "<glob>",
        description = [
            "A glob exclude pattern, e.g. ",
            "'**/raw*/**', or '**/*.bin'."
        ],
        defaultValue = ""
    )
    var excludes = emptySet<String>()

    @Option(
        names = ["-m", "--output-mode"],
        arity = "1",
        paramLabel = "<mode>",
        description = [
            "Output mode, default: \${DEFAULT-VALUE})",
            "Possible outputs: \${COMPLETION-CANDIDATES}."
        ]
    )
    var outputMode = OutputMode.diff

    @Option(
        names = ["-ce", "--class-exts", "--coalesce-classe-exts"],
        arity = "1",
        paramLabel = "<extension>",
        description = [
            "Coalesce class files with the given extensions, in",
            "addition to the usual 'class', i.e. makes classes",
            "named 'Foo.class' and 'Foo.bin' aliased to the same",
            "file same entry. Also this enables the file to be",
            "compared on bytecode level Takes a comma separated",
            "list, e.g. 'classdata' or 'raw,bin,clazz'."
        ],
        split = ","
    )
    var coalesceClassFileWithExtensions = emptySet<String>()

    @Option(
        names = ["-v"],
        description = [
            "Specify multiple -v options to increase verbosity.",
            "For example, '-v -v' or '-vv'."
        ]
    )
    var verbosity = booleanArrayOf()

    @Spec
    lateinit var spec: CommandSpec

    private lateinit var logger: Logger

    private fun executionStrategy(parseResult: CommandLine.ParseResult): Int {
        // init logger
        if (verbosity.size > Logger.MAX_VERBOSITY) {
            throw ParameterException(
                spec.commandLine(),
                "Too many '-v', maximum ${Logger.MAX_VERBOSITY}."
            )
        }
        logger = Logger(
            spec.commandLine().out,
            spec.commandLine().err,
            verbosity,
        )
        return RunLast().execute(parseResult)
    }

    override fun run() {
        val left = PathToDiff.of(LEFT, makePath(left))
        val right = PathToDiff.of(RIGHT, makePath(right))

        logger.verbose1(
            """
            Comparing:
            * ${left.path}
            * ${right.path}
            
            """.trimIndent()
        )

        Differ(
            logger = logger,
            outputMode = outputMode,
            left = left,
            right = right,
            excludes = excludes,
            coalesceClassFileWithExtensions = coalesceClassFileWithExtensions
        ).use {
            it.diff()
        }
    }

    private fun makePath(it: String): Path = Path.of(it).also {
        if (Files.exists(it).not()) {
            logger.stderr("File or directory does not exist: $it")
            exitProcess(1)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val main = Main()
            exitProcess(
                CommandLine(main)
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .setExecutionStrategy(main::executionStrategy)
                    .execute(*args)
            )
        }
    }
}

