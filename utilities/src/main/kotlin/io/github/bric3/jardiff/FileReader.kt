package io.github.bric3.jardiff

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import kotlin.io.path.extension

object FileReader {
    fun readFileAsTextIfPossible(fileLines: FileLines?): List<String> {
        if (fileLines == null) {
            return emptyList()
        }
        
        // need to handle
        // * is class file?
        // * is text file?
        // * is other binary file? then limit to checksum
        if (fileLines.relativePath.extension == "class") {
            return asmTextifier(fileLines.inputStream)
        }

        try {
            // Only handles UTF-8 at this time
            return fileLines.lines
        } catch (e: IOException) {
            // If this fails resort to hash
            return listOf("BINARY FILE SHA-1: ${sha1Hex(fileLines.inputStream)}")
        }
    }

    private fun sha1Hex(input: InputStream): String {
        return input.use {
            val buffer = ByteArray(8192)
            val digest = MessageDigest.getInstance("SHA-1")
            var read: Int
            while (it.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun asmTextifier(inputStream: InputStream): List<String> {
        return StringWriter().use { writer ->
            inputStream.use {
                AnyInputStreamTextifier().textify(PrintWriter(writer), it)
            }
        }.toString().lines()
    }

    private class AnyInputStreamTextifier : Textifier(/* latest api = */ Opcodes.ASM9) {
        /**
         * Directly replace [Textifier.main] / [org.objectweb.asm.util.Printer.main] to read from [InputStream].
         *
         * Closes the [inputStream] when done.
         */
        fun textify(output: PrintWriter, inputStream: InputStream, nodebug: Boolean = false) {
            val traceClassVisitor = TraceClassVisitor(null, output)

            inputStream.use {
                ClassReader(it).accept(
                    traceClassVisitor,
                    if (nodebug) ClassReader.SKIP_DEBUG else 0
                )
            }
        }
    }
}