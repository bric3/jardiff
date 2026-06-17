/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.jcod

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream

/**
 * Produces a JCod-compatible class-file listing.
 *
 * This implementation is written from the JVM class-file format and public JCod examples. It does
 * not copy OpenJDK AsmTools code. Attribute bodies are emitted as raw bytes so the listing remains
 * compact and round-trip oriented without reimplementing the OpenJDK attribute decoder.
 */
class JcodTextifier {
    fun toLines(inputStream: InputStream): List<String> = toText(inputStream).lines()

    fun toText(inputStream: InputStream): String {
        return JcodClassFile.parse(inputStream.readBytes()).toJcod()
    }
}

private data class JcodClassFile(
    val minorVersion: Int,
    val majorVersion: Int,
    val constantPool: List<CpEntry?>,
    val accessFlags: Int,
    val thisClass: Int,
    val superClass: Int,
    val interfaces: List<Int>,
    val fields: List<MemberInfo>,
    val methods: List<MemberInfo>,
    val attributes: List<AttributeInfo>
) {
    fun toJcod(): String = buildString {
        val className = className(thisClass)
        val kind = if (accessFlags and ACC_MODULE != 0 && className == "module-info") "module" else "class"

        appendLine("$kind $className {")
        appendLine("  0xCAFEBABE;")
        appendLine("  $minorVersion; // minor version")
        appendLine("  $majorVersion; // version")
        appendConstantPool()
        appendLine()
        appendLine("  ${accessFlags.toHex(4)}; // access")
        appendLine("  #$thisClass; // this_cpx")
        appendLine("  #$superClass; // super_cpx")
        appendLine()
        appendIndexArray("Interfaces", interfaces, "interface")
        appendLine()
        appendMembers("Fields", "field", fields)
        appendLine()
        appendMembers("Methods", "method", methods)
        appendLine()
        appendAttributes(attributes, "Attributes", "  ")
        appendLine("}")
    }

    private fun StringBuilder.appendConstantPool() {
        appendLine("  [${constantPool.size}] { // Constant Pool")
        appendLine("    ; // first element is empty")

        var index = 1
        while (index < constantPool.size) {
            when (val entry = constantPool[index]) {
                null -> index++
                is CpEntry.Utf8 -> {
                    appendLine("    Utf8 \"${entry.value.jcodEscape()}\"; // #$index")
                    index++
                }
                is CpEntry.IntegerInfo -> {
                    appendLine("    Integer ${entry.value.toHex(8)}; // #$index")
                    index++
                }
                is CpEntry.FloatInfo -> {
                    appendLine("    Float ${entry.bits.toHex(8)}; // #$index")
                    index++
                }
                is CpEntry.LongInfo -> {
                    appendLine("    Long ${entry.value.toHex(16)};; // #$index")
                    index += 2
                }
                is CpEntry.DoubleInfo -> {
                    appendLine("    Double ${entry.bits.toHex(16)};; // #$index")
                    index += 2
                }
                is CpEntry.ClassInfo -> {
                    appendLine("    Class #${entry.nameIndex}; // #$index")
                    index++
                }
                is CpEntry.StringInfo -> {
                    appendLine("    String #${entry.stringIndex}; // #$index")
                    index++
                }
                is CpEntry.MemberRef -> {
                    appendLine("    ${entry.token} #${entry.classIndex} #${entry.nameAndTypeIndex}; // #$index")
                    index++
                }
                is CpEntry.NameAndTypeInfo -> {
                    appendLine("    NameAndType #${entry.nameIndex} #${entry.descriptorIndex}; // #$index")
                    index++
                }
                is CpEntry.MethodHandleInfo -> {
                    appendLine("    MethodHandle ${entry.referenceKind}b #${entry.referenceIndex}; // #$index")
                    index++
                }
                is CpEntry.MethodTypeInfo -> {
                    appendLine("    MethodType #${entry.descriptorIndex}; // #$index")
                    index++
                }
                is CpEntry.DynamicInfo -> {
                    appendLine("    ${entry.token} ${entry.bootstrapMethodAttributeIndex}s #${entry.nameAndTypeIndex}; // #$index")
                    index++
                }
                is CpEntry.ModuleInfo -> {
                    appendLine("    Module #${entry.nameIndex}; // #$index")
                    index++
                }
                is CpEntry.PackageInfo -> {
                    appendLine("    Package #${entry.nameIndex}; // #$index")
                    index++
                }
            }
        }

        appendLine("  } // end of Constant Pool")
    }

    private fun StringBuilder.appendIndexArray(name: String, indices: List<Int>, comment: String) {
        appendLine("  [${indices.size}] { // $name")
        indices.forEach { appendLine("    #$it; // $comment") }
        appendLine("  } // end of $name")
    }

    private fun StringBuilder.appendMembers(name: String, elementName: String, members: List<MemberInfo>) {
        appendLine("  [${members.size}] { // $name")
        members.forEach { member ->
            appendLine("    { // $elementName")
            appendLine("      ${member.accessFlags.toHex(4)}; // access")
            appendLine("      #${member.nameIndex}; // name_index")
            appendLine("      #${member.descriptorIndex}; // descriptor_index")
            appendAttributes(member.attributes, "Attributes", "      ")
            appendLine("    }")
        }
        appendLine("  } // end of $name")
    }

    private fun StringBuilder.appendAttributes(attributes: List<AttributeInfo>, name: String, indent: String) {
        appendLine("$indent[${attributes.size}] { // $name")
        attributes.forEachIndexed { index, attribute ->
            appendLine("$indent  Attr(#${attribute.nameIndex}, ${attribute.info.size}) {")
            appendRawBytes(attribute.info, "$indent    ")
            appendLine("$indent  }")
            if (index != attributes.lastIndex) {
                appendLine("$indent  ;")
            }
        }
        appendLine("$indent} // end of $name")
    }

    private fun StringBuilder.appendRawBytes(bytes: ByteArray, indent: String) {
        bytes.asIterable().chunked(12).forEach { chunk ->
            append(indent)
            append(chunk.joinToString(" ") { byte -> (byte.toInt() and 0xff).toHex(2) })
            appendLine(";")
        }
    }

    private fun className(index: Int): String {
        val classEntry = constantPool.getOrNull(index) as? CpEntry.ClassInfo
            ?: return "class-$index"
        val nameEntry = constantPool.getOrNull(classEntry.nameIndex) as? CpEntry.Utf8
            ?: return "class-$index"
        return nameEntry.value
    }

    companion object {
        private const val JAVA_MAGIC = 0xCAFEBABE.toInt()
        private const val ACC_MODULE = 0x8000

        fun parse(bytes: ByteArray): JcodClassFile {
            val cursor = Cursor(bytes)
            val magic = cursor.readInt()
            require(magic == JAVA_MAGIC) { "Not a Java class file: ${magic.toHex(8)}" }

            val minorVersion = cursor.readUnsignedShort()
            val majorVersion = cursor.readUnsignedShort()
            val constantPool = cursor.readConstantPool()
            val accessFlags = cursor.readUnsignedShort()
            val thisClass = cursor.readUnsignedShort()
            val superClass = cursor.readUnsignedShort()
            val interfaces = List(cursor.readUnsignedShort()) { cursor.readUnsignedShort() }
            val fields = cursor.readMembers()
            val methods = cursor.readMembers()
            val attributes = cursor.readAttributes()

            return JcodClassFile(
                minorVersion = minorVersion,
                majorVersion = majorVersion,
                constantPool = constantPool,
                accessFlags = accessFlags,
                thisClass = thisClass,
                superClass = superClass,
                interfaces = interfaces,
                fields = fields,
                methods = methods,
                attributes = attributes
            )
        }
    }
}

private sealed interface CpEntry {
    data class Utf8(val value: String) : CpEntry
    data class IntegerInfo(val value: Int) : CpEntry
    data class FloatInfo(val bits: Int) : CpEntry
    data class LongInfo(val value: Long) : CpEntry
    data class DoubleInfo(val bits: Long) : CpEntry
    data class ClassInfo(val nameIndex: Int) : CpEntry
    data class StringInfo(val stringIndex: Int) : CpEntry
    data class MemberRef(val token: String, val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry
    data class NameAndTypeInfo(val nameIndex: Int, val descriptorIndex: Int) : CpEntry
    data class MethodHandleInfo(val referenceKind: Int, val referenceIndex: Int) : CpEntry
    data class MethodTypeInfo(val descriptorIndex: Int) : CpEntry
    data class DynamicInfo(val token: String, val bootstrapMethodAttributeIndex: Int, val nameAndTypeIndex: Int) : CpEntry
    data class ModuleInfo(val nameIndex: Int) : CpEntry
    data class PackageInfo(val nameIndex: Int) : CpEntry
}

private data class MemberInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>
)

private data class AttributeInfo(
    val nameIndex: Int,
    val info: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is AttributeInfo &&
            nameIndex == other.nameIndex &&
            info.contentEquals(other.info)
    }

    override fun hashCode(): Int = 31 * nameIndex + info.contentHashCode()
}

private class Cursor(private val bytes: ByteArray) {
    private var offset = 0

    fun readUnsignedByte(): Int {
        require(offset < bytes.size) { "Unexpected end of class file" }
        return bytes[offset++].toInt() and 0xff
    }

    fun readUnsignedShort(): Int {
        val value = (readUnsignedByte() shl 8) or readUnsignedByte()
        return value
    }

    fun readInt(): Int {
        return (readUnsignedByte() shl 24) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 8) or
            readUnsignedByte()
    }

    fun readLong(): Long {
        val high = readInt().toLong() and 0xffffffffL
        val low = readInt().toLong() and 0xffffffffL
        return (high shl 32) or low
    }

    fun readBytes(length: Int): ByteArray {
        require(length >= 0) { "Negative byte count: $length" }
        require(offset + length <= bytes.size) { "Unexpected end of class file" }
        return bytes.copyOfRange(offset, offset + length).also {
            offset += length
        }
    }

    fun readConstantPool(): List<CpEntry?> {
        val count = readUnsignedShort()
        val entries = MutableList<CpEntry?>(count) { null }
        var index = 1

        while (index < count) {
            entries[index] = when (val tag = readUnsignedByte()) {
                1 -> CpEntry.Utf8(readModifiedUtf8())
                3 -> CpEntry.IntegerInfo(readInt())
                4 -> CpEntry.FloatInfo(readInt())
                5 -> CpEntry.LongInfo(readLong()).also { index++ }
                6 -> CpEntry.DoubleInfo(readLong()).also { index++ }
                7 -> CpEntry.ClassInfo(readUnsignedShort())
                8 -> CpEntry.StringInfo(readUnsignedShort())
                9 -> readMemberRef("Field")
                10 -> readMemberRef("Method")
                11 -> readMemberRef("InterfaceMethod")
                12 -> CpEntry.NameAndTypeInfo(readUnsignedShort(), readUnsignedShort())
                15 -> CpEntry.MethodHandleInfo(readUnsignedByte(), readUnsignedShort())
                16 -> CpEntry.MethodTypeInfo(readUnsignedShort())
                17 -> readDynamic("Dynamic")
                18 -> readDynamic("InvokeDynamic")
                19 -> CpEntry.ModuleInfo(readUnsignedShort())
                20 -> CpEntry.PackageInfo(readUnsignedShort())
                else -> error("Unsupported constant pool tag $tag at index $index")
            }
            index++
        }

        return entries
    }

    fun readMembers(): List<MemberInfo> {
        return List(readUnsignedShort()) {
            MemberInfo(
                accessFlags = readUnsignedShort(),
                nameIndex = readUnsignedShort(),
                descriptorIndex = readUnsignedShort(),
                attributes = readAttributes()
            )
        }
    }

    fun readAttributes(): List<AttributeInfo> {
        return List(readUnsignedShort()) {
            val nameIndex = readUnsignedShort()
            val length = readInt()
            require(length >= 0) { "Attribute length does not fit in a signed Int: $length" }
            AttributeInfo(nameIndex, readBytes(length))
        }
    }

    private fun readModifiedUtf8(): String {
        val length = readUnsignedShort()
        val utf8Bytes = readBytes(length)
        val prefixed = ByteArray(length + 2)
        prefixed[0] = (length ushr 8).toByte()
        prefixed[1] = length.toByte()
        utf8Bytes.copyInto(prefixed, destinationOffset = 2)
        return DataInputStream(ByteArrayInputStream(prefixed)).readUTF()
    }

    private fun readMemberRef(token: String): CpEntry.MemberRef {
        return CpEntry.MemberRef(
            token = token,
            classIndex = readUnsignedShort(),
            nameAndTypeIndex = readUnsignedShort()
        )
    }

    private fun readDynamic(token: String): CpEntry.DynamicInfo {
        return CpEntry.DynamicInfo(
            token = token,
            bootstrapMethodAttributeIndex = readUnsignedShort(),
            nameAndTypeIndex = readUnsignedShort()
        )
    }
}

private fun Int.toHex(width: Int): String = "0x${toUInt().toString(16).uppercase().padStart(width, '0')}"

private fun Long.toHex(width: Int): String = "0x${toULong().toString(16).uppercase().padStart(width, '0')}"

private fun String.jcodEscape(): String = buildString {
    this@jcodEscape.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\u000C' -> append("\\f")
            '\r' -> append("\\r")
            else -> {
                if (char.code < 0x20 || char.code == 0x7f) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
}
