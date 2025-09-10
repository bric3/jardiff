/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.InputStream
import java.io.PrintWriter

internal class AnyInputStreamTextifier : Textifier(Opcodes.ASM9) {
    /**
     * Directly replace [Textifier.main] / [org.objectweb.asm.util.Printer.main] to read from [InputStream].
     *
     * Closes the [inputStream] when done.
     */
    fun textify(output: PrintWriter, inputStream: InputStream, noDebug: Boolean = false) {
        val traceClassVisitor = TraceClassVisitor(null, output)

        inputStream.use {
            ClassReader(it).accept(
                traceClassVisitor,
                if (noDebug) ClassReader.SKIP_DEBUG else 0
            )
        }
    }
}