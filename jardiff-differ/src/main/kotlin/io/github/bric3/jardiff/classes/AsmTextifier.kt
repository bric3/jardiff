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

    /**
     * Textifier variant that normalizes member declaration order within one class.
     *
     * For a given class, `m` is the number of captured members. Sorting those members costs
     * `O(m log m)` comparisons: comparison-based sorting repeatedly splits or merges the member
     * list through about `log2(m)` levels, and each level touches the `m` members. If `n` classes
     * are compared in one run, each class is sorted independently, for a total cost of
     * `sum(m_i log m_i)`.
     */
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
                key = MemberKey.of(name, descriptor, signature.orEmpty())
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
                key = MemberKey.of(name, descriptor, signature.orEmpty(), access.toString())
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
                key = MemberKey.of(buildList {
                    add(name)
                    add(descriptor)
                    add(signature.orEmpty())
                    addAll(exceptions.orEmpty())
                    add(access.toString())
                })
            ) {
                super.visitMethod(access, name, descriptor, signature, exceptions) as Textifier
            }
        }

        override fun visitClassEnd() {
            members.sortWith(memberComparator)
            members.forEach { text.addAll(it.text) }
            members.clear()
            super.visitClassEnd()
        }

        private fun captureMember(
            kind: MemberKind,
            key: MemberKey,
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
            val key: MemberKey,
            val sequence: Int,
            val text: List<Any>
        )

        private enum class MemberKind(val order: Int) {
            RecordComponent(0),
            Field(1),
            Method(2)
        }

        private class MemberKey private constructor(
            private val components: List<String>
        ) : Comparable<MemberKey> {
            override fun compareTo(other: MemberKey): Int {
                repeat(minOf(components.size, other.components.size)) { index ->
                    val comparison = components[index].compareTo(other.components[index])
                    if (comparison != 0) {
                        return comparison
                    }
                }
                return components.size.compareTo(other.components.size)
            }

            companion object {
                fun of(vararg components: String): MemberKey {
                    return MemberKey(components.asList())
                }

                fun of(components: List<String>): MemberKey {
                    return MemberKey(components.toList())
                }
            }
        }

        private companion object {
            val memberComparator = compareBy<MemberText> { it.kind.order }
                .thenBy { it.key }
                .thenBy { it.sequence }
        }
    }
}
