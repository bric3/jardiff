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
import java.io.IOException
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
        val bytes = inputStream.readBytes()
        return try {
            JcodClassFile.parse(bytes).toJcod()
        } catch (_: IllegalArgumentException) {
            RawJcodClassFile(bytes).toJcod()
        } catch (_: IllegalStateException) {
            RawJcodClassFile(bytes).toJcod()
        } catch (_: IOException) {
            RawJcodClassFile(bytes).toJcod()
        }
    }
}

private data class RawJcodClassFile(
    val bytes: ByteArray
) {
    fun toJcod(): String = buildString {
        appendLine("// JCodTextifier could not parse this class structurally.")
        appendLine("// Raw bytes preserve the original class file for AsmTools round-trip.")
        appendLine("file \"class-bytes.class\" Bytes[${bytes.size}]z {")
        appendRawBytes(bytes, "  ")
        appendLine("}")
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is RawJcodClassFile && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
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
        appendLine(topLevelDeclaration(className))
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

    private fun topLevelDeclaration(className: String): String {
        return when {
            isModuleDeclaration(className) -> "module ${moduleName(className)} {"
            className.canBeBareJcodClassName() -> "class $className {"
            else -> "file \"${className.jcodEscape()}.class\" {"
        }
    }

    private fun isModuleDeclaration(className: String): Boolean {
        return accessFlags and ACC_MODULE != 0 &&
            majorVersion >= 53 &&
            className.endsWith("module-info")
    }

    private fun moduleName(className: String): String {
        return if (className.endsWith("/module-info")) {
            className.removeSuffix("/module-info")
        } else {
            ""
        }
    }

    private fun StringBuilder.appendConstantPool() {
        appendLine("  [${constantPool.size}] { // Constant Pool")
        appendLine("    ; // first element is empty")

        var index = 1
        while (index < constantPool.size) {
            when (val entry = constantPool[index]) {
                null -> index++
                is CpEntry.Utf8 -> {
                    appendLine("    Utf8 \"${entry.value.jcodEscape()}\"; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.IntegerInfo -> {
                    appendLine("    int ${entry.value.toHex(8)}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.FloatInfo -> {
                    appendLine("    float ${entry.bits.toHex(8)}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.LongInfo -> {
                    appendLine("    long ${entry.value.toHex(16)};; ${entry.comment(index)}")
                    index += 2
                }
                is CpEntry.DoubleInfo -> {
                    appendLine("    double ${entry.bits.toHex(16)};; ${entry.comment(index)}")
                    index += 2
                }
                is CpEntry.ClassInfo -> {
                    appendLine("    class #${entry.nameIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.StringInfo -> {
                    appendLine("    String #${entry.stringIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.MemberRef -> {
                    appendLine("    ${entry.token} #${entry.classIndex} #${entry.nameAndTypeIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.NameAndTypeInfo -> {
                    appendLine("    NameAndType #${entry.nameIndex} #${entry.descriptorIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.MethodHandleInfo -> {
                    appendLine("    MethodHandle ${entry.referenceKind}b #${entry.referenceIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.MethodTypeInfo -> {
                    appendLine("    MethodType #${entry.descriptorIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.DynamicInfo -> {
                    appendLine(
                        "    ${entry.token} ${entry.bootstrapMethodAttributeIndex}s #${entry.nameAndTypeIndex}; " +
                            entry.comment(index)
                    )
                    index++
                }
                is CpEntry.ModuleInfo -> {
                    appendLine("    Module #${entry.nameIndex}; ${entry.comment(index)}")
                    index++
                }
                is CpEntry.PackageInfo -> {
                    appendLine("    Package #${entry.nameIndex}; ${entry.comment(index)}")
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
            appendAttribute(attribute, "$indent  ")
            if (index != attributes.lastIndex) {
                appendLine("$indent  ;")
            }
        }
        appendLine("$indent} // end of $name")
    }

    private fun StringBuilder.appendAttribute(attribute: AttributeInfo, indent: String) {
        val attributeName = utf8(attribute.nameIndex)
        appendLine("$indent${attribute.header(attributeName)}")

        val code = if (attributeName == "Code") CodeInfo.parseOrNull(attribute.info) else null
        if (code == null) {
            appendRawBytes(attribute.info, "$indent  ")
        } else {
            appendCode(code, "$indent  ")
        }

        appendLine("$indent}${attribute.endComment(attributeName)}")
    }

    private fun StringBuilder.appendCode(code: CodeInfo, indent: String) {
        appendLine("$indent${code.maxStack}; // max_stack")
        appendLine("$indent${code.maxLocals}; // max_locals")
        appendLine("${indent}Bytes[${code.bytecode.size}]{")
        appendRawBytes(code.bytecode, "$indent  ")
        appendLine("$indent}")
        appendLine("$indent[${code.traps.size}] { // Traps")
        code.traps.forEach { trap ->
            appendLine("$indent  ${trap.startPc} ${trap.endPc} ${trap.handlerPc} ${trap.catchType};")
        }
        appendLine("$indent} // end Traps")
        appendAttributes(code.attributes, "Attributes", indent)
    }

    private fun AttributeInfo.header(attributeName: String?): String {
        return "Attr(#$nameIndex, ${info.size}) {${attributeName.commentPrefix()}"
    }

    private fun AttributeInfo.endComment(attributeName: String?): String {
        return attributeName?.let { " // end $it" }.orEmpty()
    }

    private fun String?.commentPrefix(): String {
        return this?.let { " // $it" }.orEmpty()
    }

    private fun className(index: Int): String {
        val classEntry = constantPool.getOrNull(index) as? CpEntry.ClassInfo
            ?: error("this_class does not reference a CONSTANT_Class entry: #$index")
        val nameEntry = constantPool.getOrNull(classEntry.nameIndex) as? CpEntry.Utf8
            ?: error("this_class name does not reference a CONSTANT_Utf8 entry: #${classEntry.nameIndex}")
        return nameEntry.value
    }

    private fun utf8(index: Int): String? {
        return (constantPool.getOrNull(index) as? CpEntry.Utf8)?.value
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
    val offset: Int

    data class Utf8(override val offset: Int, val value: String) : CpEntry
    data class IntegerInfo(override val offset: Int, val value: Int) : CpEntry
    data class FloatInfo(override val offset: Int, val bits: Int) : CpEntry
    data class LongInfo(override val offset: Int, val value: Long) : CpEntry
    data class DoubleInfo(override val offset: Int, val bits: Long) : CpEntry
    data class ClassInfo(override val offset: Int, val nameIndex: Int) : CpEntry
    data class StringInfo(override val offset: Int, val stringIndex: Int) : CpEntry
    data class MemberRef(
        override val offset: Int,
        val token: String,
        val classIndex: Int,
        val nameAndTypeIndex: Int
    ) : CpEntry
    data class NameAndTypeInfo(override val offset: Int, val nameIndex: Int, val descriptorIndex: Int) : CpEntry
    data class MethodHandleInfo(override val offset: Int, val referenceKind: Int, val referenceIndex: Int) : CpEntry
    data class MethodTypeInfo(override val offset: Int, val descriptorIndex: Int) : CpEntry
    data class DynamicInfo(
        override val offset: Int,
        val token: String,
        val bootstrapMethodAttributeIndex: Int,
        val nameAndTypeIndex: Int
    ) : CpEntry
    data class ModuleInfo(override val offset: Int, val nameIndex: Int) : CpEntry
    data class PackageInfo(override val offset: Int, val nameIndex: Int) : CpEntry
}

private data class MemberInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>
)

private data class CodeInfo(
    val maxStack: Int,
    val maxLocals: Int,
    val bytecode: ByteArray,
    val traps: List<TrapInfo>,
    val attributes: List<AttributeInfo>
) {
    companion object {
        fun parseOrNull(bytes: ByteArray): CodeInfo? {
            return runCatching {
                val cursor = Cursor(bytes)
                val maxStack = cursor.readUnsignedShort()
                val maxLocals = cursor.readUnsignedShort()
                val codeLength = cursor.readInt()
                require(codeLength >= 0) { "Negative Code bytecode length: $codeLength" }
                val bytecode = cursor.readBytes(codeLength)
                val traps = List(cursor.readUnsignedShort()) {
                    TrapInfo(
                        startPc = cursor.readUnsignedShort(),
                        endPc = cursor.readUnsignedShort(),
                        handlerPc = cursor.readUnsignedShort(),
                        catchType = cursor.readUnsignedShort()
                    )
                }
                val attributes = cursor.readAttributes()
                require(cursor.isAtEnd) { "Unexpected trailing bytes in Code attribute" }
                CodeInfo(maxStack, maxLocals, bytecode, traps, attributes)
            }.getOrNull()
        }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is CodeInfo &&
            maxStack == other.maxStack &&
            maxLocals == other.maxLocals &&
            bytecode.contentEquals(other.bytecode) &&
            traps == other.traps &&
            attributes == other.attributes
    }

    override fun hashCode(): Int {
        var result = maxStack
        result = 31 * result + maxLocals
        result = 31 * result + bytecode.contentHashCode()
        result = 31 * result + traps.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }
}

private data class TrapInfo(
    val startPc: Int,
    val endPc: Int,
    val handlerPc: Int,
    val catchType: Int
)

private data class AttributeInfo(
    val offset: Int,
    val nameIndex: Int,
    val info: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is AttributeInfo &&
            offset == other.offset &&
            nameIndex == other.nameIndex &&
            info.contentEquals(other.info)
    }

    override fun hashCode(): Int = 31 * (31 * offset + nameIndex) + info.contentHashCode()
}

private class Cursor(private val bytes: ByteArray) {
    private var offset = 0

    val isAtEnd: Boolean
        get() = offset == bytes.size

    private val position: Int
        get() = offset

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
            val entryOffset = position
            entries[index] = when (val tag = readUnsignedByte()) {
                1 -> CpEntry.Utf8(entryOffset, readModifiedUtf8())
                3 -> CpEntry.IntegerInfo(entryOffset, readInt())
                4 -> CpEntry.FloatInfo(entryOffset, readInt())
                5 -> CpEntry.LongInfo(entryOffset, readLong()).also { index++ }
                6 -> CpEntry.DoubleInfo(entryOffset, readLong()).also { index++ }
                7 -> CpEntry.ClassInfo(entryOffset, readUnsignedShort())
                8 -> CpEntry.StringInfo(entryOffset, readUnsignedShort())
                9 -> readMemberRef(entryOffset, "Field")
                10 -> readMemberRef(entryOffset, "Method")
                11 -> readMemberRef(entryOffset, "InterfaceMethod")
                12 -> CpEntry.NameAndTypeInfo(entryOffset, readUnsignedShort(), readUnsignedShort())
                15 -> CpEntry.MethodHandleInfo(entryOffset, readUnsignedByte(), readUnsignedShort())
                16 -> CpEntry.MethodTypeInfo(entryOffset, readUnsignedShort())
                17 -> readDynamic(entryOffset, "Dynamic")
                18 -> readDynamic(entryOffset, "InvokeDynamic")
                19 -> CpEntry.ModuleInfo(entryOffset, readUnsignedShort())
                20 -> CpEntry.PackageInfo(entryOffset, readUnsignedShort())
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
            val offset = position
            val nameIndex = readUnsignedShort()
            val length = readInt()
            require(length >= 0) { "Attribute length does not fit in a signed Int: $length" }
            AttributeInfo(offset, nameIndex, readBytes(length))
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

    private fun readMemberRef(offset: Int, token: String): CpEntry.MemberRef {
        return CpEntry.MemberRef(
            offset = offset,
            token = token,
            classIndex = readUnsignedShort(),
            nameAndTypeIndex = readUnsignedShort()
        )
    }

    private fun readDynamic(offset: Int, token: String): CpEntry.DynamicInfo {
        return CpEntry.DynamicInfo(
            offset = offset,
            token = token,
            bootstrapMethodAttributeIndex = readUnsignedShort(),
            nameAndTypeIndex = readUnsignedShort()
        )
    }
}

private fun Int.toHex(width: Int): String = "0x${toUInt().toString(16).uppercase().padStart(width, '0')}"

private fun Long.toHex(width: Int): String = "0x${toULong().toString(16).uppercase().padStart(width, '0')}"

private fun CpEntry.comment(index: Int): String = "// #$index     at ${offset.toOffsetHex()}"

private fun Int.toOffsetHex(): String {
    val width = if (this <= 0xff) 2 else 4
    return "0x${toUInt().toString(16).uppercase().padStart(width, '0')}"
}

private fun StringBuilder.appendRawBytes(bytes: ByteArray, indent: String) {
    bytes.asIterable().chunked(12).forEach { chunk ->
        append(indent)
        append(chunk.joinToString(" ") { byte -> (byte.toInt() and 0xff).toHex(2) })
        appendLine(";")
    }
}

private fun String.canBeBareJcodClassName(): Boolean {
    if (this in openJdkFileDeclarationClassNames) {
        return false
    }
    if (isEmpty() || !Character.isJavaIdentifierStart(first())) {
        return false
    }

    for (char in this) {
        if (char != '/' && char != '.' && char != '-' && !Character.isJavaIdentifierPart(char)) {
            return false
        }
    }

    return true
}

// OpenJDK's CheckedExceptions.jcod declares this class as: file "I.class".
private val openJdkFileDeclarationClassNames = setOf(
    "I"
)

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
