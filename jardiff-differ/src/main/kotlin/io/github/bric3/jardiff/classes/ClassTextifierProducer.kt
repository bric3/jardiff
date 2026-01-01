package io.github.bric3.jardiff.classes

@Suppress("EnumEntryName", "unused")
enum class ClassTextifierProducer(producer: () -> ClassTextifier) {
    `asm-textifier`({ AsmTextifier() }),
    `class-file-version`({ ClassFileMajorVersion }),
    `class-outline`({ ClassOutline })
    // javap
    ;

    val instance by lazy { producer() }
}