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

import io.github.bric3.gradle.executableArchive.MakeJarExecutableAction.Companion.DEFAULT_SHELL_HEADER
import io.github.bric3.gradle.executableArchive.MakeJarExecutableAction.Companion.makeZipExecutable
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A Gradle task that creates an executable JAR by prepending a shell script to an existing JAR.
 *
 * Note: Maven Central does not allow executable JARs, so this task creates a separate
 * executable version with an `-app` suffix for distribution.
 *
 * @see MakeJarExecutableAction
 */
abstract class ExecutableJarTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:Internal("Represented as part of archiveFile")
    abstract val destinationDirectory: DirectoryProperty

    @get:Internal("Represented as part of archiveFile")
    abstract val archiveBaseName: Property<String>

    @get:Internal("Represented as part of archiveFile")
    abstract val archiveVersion: Property<String>

    @get:Internal("Represented as part of archiveFile")
    abstract val archiveClassifier: Property<String>

    @get:Internal("Represented as part of archiveFile")
    abstract val archiveExtension: Property<String>

    /**
     * The shell script header to prepend to the JAR.
     * A double newline separator is automatically appended as a safeguard.
     */
    @get:Input
    abstract val shellHeader: Property<String>

    @get:Input
    val archiveFileName: Provider<String>
        get() = archiveBaseName.zip(archiveVersion) { base, version ->
            "$base-$version"
        }.zip(archiveClassifier) { nameWithVersion, classifier ->
            "$nameWithVersion-$classifier"
        }.zip(archiveExtension) { nameWithClassifier, ext ->
            "$nameWithClassifier.$ext"
        }

    @get:OutputFile
    val executableArchiveFile: Provider<org.gradle.api.file.RegularFile>
        get() = destinationDirectory.file(archiveFileName)

    init {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Create an executable JAR with shell script header"

        // Set conventions (similar to AbstractArchiveTask)
        val basePluginExtension = project.extensions.getByType(BasePluginExtension::class.java)
        destinationDirectory.convention(basePluginExtension.libsDirectory)
        archiveClassifier.convention("app")
        archiveExtension.convention("jar")
        archiveVersion.convention(project.provider { project.version.toString() })
        shellHeader.convention(DEFAULT_SHELL_HEADER)
    }

    @TaskAction
    fun createExecutableJar() {
        val input = inputJar.get().asFile
        val output = executableArchiveFile.get().asFile
        val header = shellHeader.get()

        makeZipExecutable(input, output, header)

        logger.info("Created executable JAR: ${output.name}")
    }
}