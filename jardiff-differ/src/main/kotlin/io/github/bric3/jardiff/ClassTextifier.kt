package io.github.bric3.jardiff.classes

import java.io.InputStream

/**
 * Abstract class for producing a text representation from a Class [java.io.InputStream].
 */
sealed class ClassTextifier() {
    /**
     * Convert the given [inputStream] representing a class file into a list of lines.
     */
    abstract fun toLines(inputStream: InputStream): List<String>

    /**
     * Convert the given [inputStream] representing a class file into a single text block.
     */
    open fun toText(inputStream: InputStream) = toLines(inputStream).joinToString("\n")
}