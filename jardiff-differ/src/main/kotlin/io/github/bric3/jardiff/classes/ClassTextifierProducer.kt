package io.github.bric3.jardiff.classes

/**
 * Enum of available [ClassTextifier] producers.
 *
 * These are used to produce a text output from class files
 */
@Suppress("EnumEntryName", "unused")
enum class ClassTextifierProducer(producer: () -> ClassTextifier) {
    /**
     * [AsmTextifier] - produces a text representation of the class using ASM's Textifier
     */
    `asm-textifier`({ AsmTextifier() }),

    /**
     * [ClassFileMajorVersion] - produces a text representation of the class file major version
     */
    `class-file-version`({ ClassFileMajorVersion }),

    /**
     * [ClassOutline] - produces a text outline of the class structure
     */
    `class-outline`({ ClassOutline })
    // javap
    ;

    /** The instance of the [ClassTextifier] produced by this producer */
    val instance by lazy { producer() }
}