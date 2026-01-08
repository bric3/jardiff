/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.gradle.executableArchive

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import javax.inject.Inject

/**
 * An action that makes a JAR file executable by prepending a shell script to it.
 *
 * Note, Maven Central does not allow executable jars. Instead, create a different artifact, with [ExecutableJarTask].
 *
 * @see ExecutableJarTask
 */
abstract class MakeJarExecutableAction @Inject constructor(
    project: Project,
    private val fileSystemOperations: FileSystemOperations
) : Action<Task> {

    @get:InputDirectory
    abstract val buildDirectory: DirectoryProperty

    /**
     * The shell script header to prepend to the JAR.
     * A double newline separator is automatically appended as a safeguard.
     */
    @get:Input
    abstract val shellHeader: Property<String>

    init {
        buildDirectory.convention(project.layout.buildDirectory)
        shellHeader.convention(DEFAULT_SHELL_HEADER)
    }

    override fun execute(task: Task) {
        require(task is Zip) { "Task must be a Zip task" }
        val jarFile = task.archiveFile.get().asFile
        val tmpDir = buildDirectory.dir("tmp/${task.name}/executable-jar").get().asFile
        val tmpJarFile = File(tmpDir, "${jarFile.name}.tmp")
        val header = shellHeader.get()

        fileSystemOperations.copy {
            from(jarFile)
            into(tmpDir)
            rename { tmpJarFile.name }
        }
        fileSystemOperations.delete {
            delete(jarFile)
        }

        makeZipExecutable(tmpJarFile, jarFile, header)

        fileSystemOperations.delete {
            delete(tmpJarFile)
        }
    }

    companion object {
        const val DEFAULT_SHELL_HEADER = "#!/bin/sh\n\nexec java \$JAVA_OPTS -jar \$0 \"\$@\""

        /**
         * Creates an executable JAR by prepending a shell script header.
         *
         * @param inputFile The source JAR file
         * @param outputFile The destination file for the executable JAR
         * @param shellHeader The shell script header to prepend (defaults to a basic sh script).
         *                    A double newline separator is automatically appended.
         */
        fun makeZipExecutable(
            inputFile: File,
            outputFile: File,
            shellHeader: String = DEFAULT_SHELL_HEADER
        ) {
            require(inputFile.exists()) { "Input JAR does not exist: ${inputFile.absolutePath}" }
            require(inputFile.isFile) { "Input JAR is not a file: ${inputFile.absolutePath}" }
            require(shellHeader.isNotBlank()) { "Shell header must not be blank" }

            outputFile.parentFile.mkdirs()
            outputFile.outputStream().use { output ->
                // Always append double newline as safeguard separator between script and ZIP content
                val headerWithSeparator = if (shellHeader.endsWith("\n\n")) {
                    shellHeader
                } else {
                    shellHeader + "\n\n"
                }
                output.write(headerWithSeparator.toByteArray())
                inputFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            outputFile.setExecutable(true, false)
        }

        fun Zip.makeExecutable(): Task = this.doLast(project.objects.newInstance<MakeJarExecutableAction>())
    }
}