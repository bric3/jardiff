package io.github.bric3.jardiff.classes

import java.io.InputStream

sealed class ClassTextifier() {
    abstract fun toLines(inputStream: InputStream): List<String>
    open fun toText(inputStream: InputStream) = toLines(inputStream).joinToString("\n")
}

