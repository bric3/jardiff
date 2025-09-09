package io.github.bric3.jardiff

object Logger {
    val GREEN = "\u001B[32m"
    val RED = "\u001B[31m"
    val RESET = "\u001B[0m"

    fun stdout(message: String) {
        println(message)
    }
    fun stderr(message: String) {
        System.err.println(message)
    }
}