package io.github.bric3.jardiff.classes

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.InputStream

/**
 * Produces a class outline showing only the ABI (Application Binary Interface).
 * This includes class structure, fields, and method signatures, but excludes
 * implementation details like method bodies, line numbers, and local variables.
 */
data object ClassOutline : ClassTextifier() {
    override fun toLines(inputStream: InputStream): List<String> {
        val visitor = OutlineClassVisitor()
        inputStream.use {
            ClassReader(it).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        return visitor.lines
    }

    private class OutlineClassVisitor : ClassVisitor(Opcodes.ASM9) {
        val lines = mutableListOf<String>()
        private var simpleClassName: String = ""
        private var isKotlin = false
        private var isGroovy = false
        private var hasPackage = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            val fullClassName = name.replace('/', '.')
            val packageName = fullClassName.substringBeforeLast('.', "")
            simpleClassName = fullClassName.substringAfterLast('.')

            // Check for Groovy by interface
            isGroovy = interfaces?.any { it.startsWith("groovy/") } == true

            // Add package declaration if present
            if (packageName.isNotEmpty()) {
                lines.add("package $packageName;")
                lines.add("")
                hasPackage = true
            }

            val modifiers = accessToModifiers(access, isClass = true)
            val classType = when {
                (access and Opcodes.ACC_INTERFACE) != 0 -> "interface"
                (access and Opcodes.ACC_ENUM) != 0 -> "enum"
                (access and Opcodes.ACC_ANNOTATION) != 0 -> "@interface"
                (access and Opcodes.ACC_ABSTRACT) != 0 && (access and Opcodes.ACC_INTERFACE) == 0 -> "abstract class"
                else -> "class"
            }

            val extendsClause = if (superName != null && superName != "java/lang/Object") {
                " extends ${superName.replace('/', '.')}"
            } else ""

            val implementsClause = if (!interfaces.isNullOrEmpty()) {
                " implements ${interfaces.joinToString(", ") { it.replace('/', '.') }}"
            } else ""

            lines.add("$modifiers$classType $simpleClassName$extendsClause$implementsClause {")
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
            // Detect Kotlin metadata annotation
            if (descriptor == "Lkotlin/Metadata;") {
                isKotlin = true
                // Insert comment before class declaration
                val classLineIndex = lines.indexOfLast { it.contains("class ") || it.contains("interface ") }
                if (classLineIndex >= 0) {
                    lines.add(classLineIndex, "// Kotlin class")
                }
            }
            return null
        }

        override fun visitEnd() {
            // Add Groovy comment if detected but not yet added
            if (isGroovy && !isKotlin) {
                val classLineIndex = lines.indexOfLast { it.contains("class ") || it.contains("interface ") }
                if (classLineIndex >= 0 && (classLineIndex == 0 || !lines[classLineIndex - 1].startsWith("//"))) {
                    lines.add(classLineIndex, "// Groovy class")
                }
            }
            lines.add("}")
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            val modifiers = accessToModifiers(access, isClass = false)
            val type = Type.getType(descriptor).className
            val valueStr = if (value != null) " = $value" else ""
            lines.add("  $modifiers$type $name$valueStr")
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            // Skip synthetic methods like bridges and accessors
            if ((access and Opcodes.ACC_SYNTHETIC) != 0 && (access and Opcodes.ACC_BRIDGE) != 0) {
                return null
            }

            val modifiers = accessToModifiers(access, isClass = false)
            val methodType = Type.getType(descriptor)
            val returnType = methodType.returnType.className
            val params = methodType.argumentTypes.joinToString(", ") { it.className }

            val throwsClause = if (!exceptions.isNullOrEmpty()) {
                " throws ${exceptions.joinToString(", ") { it.replace('/', '.') }}"
            } else ""

            val methodDecl = if (name == "<init>") {
                // Constructor
                "$modifiers$simpleClassName($params)$throwsClause"
            } else if (name == "<clinit>") {
                // Static initializer - usually skip
                return null
            } else {
                "$modifiers$returnType $name($params)$throwsClause"
            }

            lines.add("  $methodDecl")
            return null
        }

        private fun accessToModifiers(access: Int, isClass: Boolean): String {
            val modifiers = mutableListOf<String>()

            if ((access and Opcodes.ACC_PUBLIC) != 0) modifiers.add("public")
            if ((access and Opcodes.ACC_PRIVATE) != 0) modifiers.add("private")
            if ((access and Opcodes.ACC_PROTECTED) != 0) modifiers.add("protected")

            if ((access and Opcodes.ACC_STATIC) != 0) modifiers.add("static")
            if ((access and Opcodes.ACC_FINAL) != 0) modifiers.add("final")

            if (isClass) {
                // Abstract is handled in class type for classes
            } else {
                if ((access and Opcodes.ACC_ABSTRACT) != 0) modifiers.add("abstract")
                if ((access and Opcodes.ACC_SYNCHRONIZED) != 0) modifiers.add("synchronized")
                if ((access and Opcodes.ACC_NATIVE) != 0) modifiers.add("native")
                if ((access and Opcodes.ACC_TRANSIENT) != 0) modifiers.add("transient")
                if ((access and Opcodes.ACC_VOLATILE) != 0) modifiers.add("volatile")
            }

            return if (modifiers.isEmpty()) "" else modifiers.joinToString(" ") + " "
        }
    }
}