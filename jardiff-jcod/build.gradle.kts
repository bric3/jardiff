
import de.undercouch.gradle.tasks.download.Download

/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

plugins {
    id("de.undercouch.download") version "5.7.0"
    id("jardiff.kotlin-library-conventions")
    id("jardiff.maven-publication-conventions")
}

// OpenJDK AsmTools is downloaded only for the opt-in compatibility test.
// It is not copied into this MPL module or included in published artifacts.
val asmtoolsCommit = "dbecba9cd85e2e7709f6088b1cac3484749f69f3"
val asmtoolsArchive = layout.buildDirectory.file("asmtools/asmtools-$asmtoolsCommit.tar.gz")
val asmtoolsExtractDir = layout.buildDirectory.dir("asmtools/source")
val asmtoolsSourceDir = asmtoolsExtractDir.map { it.dir("asmtools-$asmtoolsCommit") }
val asmtoolsAntBuildDir = layout.buildDirectory.dir("asmtools/build")
val asmtoolsJar = layout.buildDirectory.file("asmtools/asmtools.jar")

tasks {
    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }

    val downloadAsmToolsArchive by registering(Download::class) {
        description = "Download the pinned OpenJDK AsmTools archive for compatibility testing."
        src("https://github.com/openjdk/asmtools/archive/$asmtoolsCommit.tar.gz")
        dest(asmtoolsArchive)
        onlyIfModified(true)
        overwrite(false)
        tempAndMove(true)
    }

    val extractAsmToolsArchive by registering(Copy::class) {
        description = "Extract the pinned OpenJDK AsmTools archive for compatibility testing."
        dependsOn(downloadAsmToolsArchive)
        from(tarTree(resources.gzip(downloadAsmToolsArchive.map { it.dest  })))
        into(asmtoolsExtractDir)
        notCompatibleWithConfigurationCache("Extracts an external source archive for an opt-in compatibility test.")
    }

    val buildAsmToolsJar by registering(BuildAsmToolsJar::class) {
        description = "Build the external OpenJDK AsmTools jar used by compatibility tests."
        dependsOn(extractAsmToolsArchive)
        sourceDir.set(asmtoolsSourceDir)
        antBuildDir.set(asmtoolsAntBuildDir)
        outputJar.set(asmtoolsJar)
        notCompatibleWithConfigurationCache("Uses Gradle's AntBuilder to build an external source archive.")
    }
}

testing {
    suites {
        val asmtoolsCompatibilityTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit5)

            dependencies {
                implementation(project())
                implementation(libs.assertj.core)
            }

            targets {
                all {
                    testTask.configure {
                        description = "Verifies emitted JCod with external OpenJDK AsmTools."
                        shouldRunAfter(tasks.test)
                        dependsOn(tasks.named("buildAsmToolsJar"))
                        inputs.file(asmtoolsJar)
                            .withPropertyName("asmtoolsJar")
                            .withPathSensitivity(PathSensitivity.NONE)
                        systemProperty("jardiff.asmtools.jar", asmtoolsJar.get().asFile.absolutePath)
                    }
                }
            }
        }
    }
}

abstract class BuildAsmToolsJar : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val antBuildDir: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun buildJar() {
        val sourceDir = sourceDir.get().asFile
        val buildDir = antBuildDir.get().asFile
        ant.withGroovyBuilder {
            "ant"(
                "antfile" to sourceDir.resolve("build/build.xml").absolutePath,
                "dir" to sourceDir.resolve("build").absolutePath,
                "target" to "devbuild"
            ) {
                "property"(
                    "name" to "BUILD_DIR",
                    "location" to buildDir.absolutePath
                )
            }
        }

        val output = outputJar.get().asFile
        val builtJar = buildDir.resolve("binaries/lib/asmtools.jar")
        check(builtJar.isFile) {
            "Could not find built asmtools jar at $builtJar"
        }
        fileSystemOperations.copy {
            from(builtJar)
            into(output.parentFile)
        }
    }
}
