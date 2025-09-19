package io.github.bric3.jardiff.classes

import java.io.InputStream
import kotlin.experimental.and

private const val CLASS_MAJOR_VERSION_OFFSET = 44

data object ClassFileMajorVersion : ClassTextifier() {
    override fun toLines(inputStream: InputStream): List<String> {
        return inputStream.use {
            val classMajorVersion = it.readNBytes(8)[7] and 0xFF.toByte()
            val javaVersion = classMajorVersion.toInt() - CLASS_MAJOR_VERSION_OFFSET
            listOf("class version: $classMajorVersion (Java $javaVersion)")
        }
    }
}