package io.github.bric3.jardiff.classes

import java.io.InputStream
import kotlin.experimental.and

/**
 * [ClassTextifier] that extracts and describes the class file major version.
 */
data object ClassFileMajorVersion : ClassTextifier() {
    private const val CLASS_MAJOR_VERSION_OFFSET = 44
    
    override fun toLines(inputStream: InputStream): List<String> {
        return inputStream.use {
            val classMajorVersion = it.readNBytes(8)[7] and 0xFF.toByte()
            listOf(describeClassVersion(classMajorVersion.toInt()))
        }
    }

    /**
     * Describe the class major version in terms of Java version.
     */
    fun describeClassVersion(classMajorVersion: Int): String {
        val javaVersion = classMajorVersion - CLASS_MAJOR_VERSION_OFFSET
        return "class version: $classMajorVersion (Java $javaVersion)"
    }
}