/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import javax.inject.Inject

abstract class MakeJarExecutableAction @Inject constructor(
    project: Project,
    private val fileSystemOperations: FileSystemOperations
) : Action<Task> {

    @get:InputDirectory
    abstract val buildDirectory: DirectoryProperty

    init {
        buildDirectory.convention(project.layout.buildDirectory)
    }

    override fun execute(task: Task) {
        require(task is Zip) { "Task must be a Zip task" }
        val jarFile = task.archiveFile.get().asFile
        val tmpDir = buildDirectory.dir("tmp/${task.name}/executable-jar").get().asFile
        val tmpJarFile = File(tmpDir, "${jarFile.name}.tmp")

        fileSystemOperations.copy {
            from(jarFile)
            into(tmpDir)
            rename { tmpJarFile.name }
        }
        fileSystemOperations.delete {
            delete(jarFile)
        }

        jarFile.outputStream().use { output ->
            output.write("#!/bin/sh\n\nexec java \$JAVA_OPTS -jar \$0 \"\$@\"\n\n".toByteArray())
            tmpJarFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        jarFile.setExecutable(true, false)

        // Clean up temporary file
        fileSystemOperations.delete {
            delete(tmpJarFile)
        }
    }

    companion object {
        fun Zip.makeExecutable(): Task = this.doLast(project.objects.newInstance<MakeJarExecutableAction>())
    }
}