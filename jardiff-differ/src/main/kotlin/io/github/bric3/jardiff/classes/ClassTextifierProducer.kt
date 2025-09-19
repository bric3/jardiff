package io.github.bric3.jardiff.classes

@Suppress("EnumEntryName", "unused")
enum class ClassTextifierProducer(producer: () -> ClassTextifier) {
    `asm-textifier`({ AsmTextifier() }),
    `class-file-version`({ ClassFileMajorVersion })
    // javap, outline
    ;

    val instance by lazy { producer() }
}