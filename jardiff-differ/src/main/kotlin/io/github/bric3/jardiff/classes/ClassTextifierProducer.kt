package io.github.bric3.jardiff.classes

/**
 * Enum of available [ClassTextifier] producers.
 *
 * These are used to produce a text output from class files
 */
@Suppress("EnumEntryName", "unused")
enum class ClassTextifierProducer(private val producer: (ClassTextOptions) -> ClassTextifier) {
    /**
     * [AsmTextifier] - produces a text representation of the class using ASM's Textifier
     */
    `asm-textifier`({ options -> AsmTextifier(options = options) }),

    /**
     * [ClassFileMajorVersion] - produces a text representation of the class file major version
     */
    `class-file-version`({ ClassFileMajorVersion }),

    /**
     * [ClassOutline] - produces a text outline of the class structure
     */
    `class-outline`({ options -> ClassOutline(options) })
    // javap
    ;

    /** Create a [ClassTextifier] configured for one comparison run. */
    fun create(options: ClassTextOptions = ClassTextOptions()) = producer(options)
}
