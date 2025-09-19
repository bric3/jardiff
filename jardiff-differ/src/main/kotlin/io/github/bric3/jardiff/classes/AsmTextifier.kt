package io.github.bric3.jardiff.classes

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

class AsmTextifier(private val skipDebug: Boolean = false) : ClassTextifier() {
    override fun toLines(inputStream: InputStream): List<String> {
        return StringWriter().use { writer ->
            InputStreamTextifier.textify(writer, inputStream, skipDebug)
            writer.toString().lines()
        }
    }

    internal object InputStreamTextifier : Textifier(Opcodes.ASM9) {
        /**
         * Directly replace [Textifier.main] / [org.objectweb.asm.util.Printer.main] to read from [InputStream].
         *
         * Closes the [inputStream] when done.
         */
        internal fun textify(output: Writer, inputStream: InputStream, skipDebug: Boolean) {
            val traceClassVisitor = TraceClassVisitor(null, PrintWriter(output, true))

            inputStream.use {
                ClassReader(it).accept(
                    traceClassVisitor,
                    if (skipDebug) ClassReader.SKIP_DEBUG else 0
                )
            }
        }
    }
}