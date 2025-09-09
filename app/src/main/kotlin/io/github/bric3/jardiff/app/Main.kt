package io.github.bric3.jardiff.app

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import kotlin.system.exitProcess

@Command(
    name = "jardiff",
    mixinStandardHelpOptions = true,
    version = ["jardiff 1.0"],
    description = ["Compares two JAR files or directories and reports differences."]
)
class Main : Runnable {
    @Parameters(arity = "2", paramLabel = "<jar-or-directory>", description = ["The JAR or directory to compare."])
    lateinit var files: List<String>

    override fun run() {
        println("Comparing:")
        files.forEach { println(it) }
        // TODO: Add actual comparison logic here
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            exitProcess(CommandLine(Main()).execute(*args))
        }
    }
}

