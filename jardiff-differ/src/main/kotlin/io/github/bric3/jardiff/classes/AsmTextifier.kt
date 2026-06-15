/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.classes

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

/**
 * [ClassTextifier] implementation using ASM's [Textifier] to produce a text representation of a class file.
 *
 * @param skipDebug Whether to skip debug information (like line numbers and local variables)
 */
class AsmTextifier @JvmOverloads constructor(
    private val skipDebug: Boolean = false,
    options: ClassTextOptions = ClassTextOptions()
) : ClassTextifier() {
    private val inputStreamTextifier = InputStreamTextifier(skipDebug, options.memberOrder)

    override fun toLines(inputStream: InputStream): List<String> {
        return StringWriter().use { writer ->
            inputStreamTextifier.textify(writer, inputStream)
            writer.toString().lines()
        }
    }

    private class InputStreamTextifier(
        private val skipDebug: Boolean,
        private val memberOrder: ClassMemberOrder
    ) {
        /**
         * Directly replace [Textifier.main] / [org.objectweb.asm.util.Printer.main] to read from [InputStream].
         *
         * Closes the [inputStream] when done.
         */
        fun textify(output: Writer, inputStream: InputStream) {
            val traceClassVisitor = TraceClassVisitor(null, newPrinter(), PrintWriter(output, true))

            inputStream.use {
                ClassReader(it).accept(
                    traceClassVisitor,
                    if (skipDebug) ClassReader.SKIP_DEBUG else 0
                )
            }
        }

        private fun newPrinter(): Textifier {
            return when (memberOrder) {
                ClassMemberOrder.Declaration -> Textifier()
                ClassMemberOrder.Sorted -> MemberSortingTextifier()
            }
        }
    }

    private class MemberSortingTextifier : Textifier(Opcodes.ASM9) {
        private val members = mutableListOf<MemberText>()
        private var nextSequence = 0

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?
        ): Textifier {
            return captureMember(
                MemberKind.RecordComponent,
                key = listOf(name, descriptor, signature.orEmpty())
            ) {
                super.visitRecordComponent(name, descriptor, signature) as Textifier
            }
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): Textifier {
            return captureMember(
                MemberKind.Field,
                key = listOf(name, descriptor, signature.orEmpty(), access.toString())
            ) {
                super.visitField(access, name, descriptor, signature, value)
            }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): Textifier {
            return captureMember(
                MemberKind.Method,
                key = buildList {
                    add(name)
                    add(descriptor)
                    add(signature.orEmpty())
                    add(exceptions?.joinToString(",").orEmpty())
                    add(access.toString())
                }
            ) {
                super.visitMethod(access, name, descriptor, signature, exceptions) as Textifier
            }
        }

        override fun visitClassEnd() {
            members
                .sortedWith(compareBy<MemberText> { it.kind.order }
                    .thenBy { it.key.joinToString("\u0000") }
                    .thenBy { it.sequence })
                .forEach { text.addAll(it.text) }
            super.visitClassEnd()
        }

        private fun captureMember(
            kind: MemberKind,
            key: List<String>,
            visit: () -> Textifier
        ): Textifier {
            val startIndex = text.size
            val memberPrinter = visit()
            val memberText = text.subList(startIndex, text.size).toList()
            text.subList(startIndex, text.size).clear()
            members.add(MemberText(kind, key, nextSequence++, memberText))
            return memberPrinter
        }

        private data class MemberText(
            val kind: MemberKind,
            val key: List<String>,
            val sequence: Int,
            val text: List<Any>
        )

        private enum class MemberKind(val order: Int) {
            RecordComponent(0),
            Field(1),
            Method(2)
        }
    }
}
