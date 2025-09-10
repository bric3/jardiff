package io.github.bric3.jardiff

import org.apache.tika.parser.txt.CharsetDetector
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
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
            return asmTextifier(fileLines.bufferedInputStream)
        }

        return fileLines.bufferedInputStream.use {
            // detect charset
            // if confidence is low, then assume binary
            val detector = CharsetDetector().setText(it)
            val match = detector.detect()
            if (match.confidence > 80) {
                it.reader(Charset.forName(match.name)).readLines()
            } else {
                // For now just return the sha1 of the binary file
                // Next show binary diff? E.g.
                //  │00000000│ 23 21 2f 62 69 6e 2f 73 ┊ 68 0a 0a 23 0a 23 20 43 │#!/bin/s┊h__#_# C│
                return listOf("BINARY FILE SHA-1: ${sha1Hex(fileLines.bufferedInputStream)}")
            }
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