/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import de.undercouch.gradle.tasks.download.Download

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
val openJdkJcodCommit = "e634995278df705be979c3d67b78a4319e32bfcc"
val openJdkJcodArchive = layout.buildDirectory.file("openjdk-jcod/jdk26u-$openJdkJcodCommit.tar.gz")
val openJdkJcodDir = layout.buildDirectory.dir("openjdk-jcod/source")

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

    val downloadOpenJdkJcodArchive by registering(Download::class) {
        description = "Download the pinned OpenJDK JDK 26 update archive for JCod compatibility testing."
        src("https://github.com/openjdk/jdk26u/archive/$openJdkJcodCommit.tar.gz")
        dest(openJdkJcodArchive)
        onlyIfModified(true)
        overwrite(false)
        tempAndMove(true)
    }

    val extractOpenJdkJcodFiles by registering(Copy::class) {
        description = "Extract OpenJDK JCod test files used by compatibility tests."
        dependsOn(downloadOpenJdkJcodArchive)
        from(tarTree(resources.gzip(downloadOpenJdkJcodArchive.map { it.dest  }))) {
            include("jdk26u-$openJdkJcodCommit/test/**/*.jcod")
        }
        into(openJdkJcodDir)
        includeEmptyDirs = false
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
                implementation(libs.junit.jupiter.params)
            }

            targets {
                all {
                    testTask.configure {
                        description = "Verifies emitted JCod with external OpenJDK AsmTools."
                        shouldRunAfter(tasks.test)
                        dependsOn(tasks.named("buildAsmToolsJar"))
                        dependsOn(tasks.named("extractOpenJdkJcodFiles"))
                        inputs.file(asmtoolsJar)
                            .withPropertyName("asmtoolsJar")
                            .withPathSensitivity(PathSensitivity.NONE)
                        inputs.dir(openJdkJcodDir)
                            .withPropertyName("openJdkJcodDir")
                            .withPathSensitivity(PathSensitivity.RELATIVE)
                        inputs.dir(asmtoolsSourceDir)
                            .withPropertyName("asmtoolsSourceDir")
                            .withPathSensitivity(PathSensitivity.RELATIVE)
                        systemProperty("jardiff.asmtools.jar", asmtoolsJar.get().asFile.absolutePath)
                        systemProperty("jardiff.asmtools.jcod.dir", asmtoolsSourceDir.get().asFile.absolutePath)
                        systemProperty("jardiff.openjdk.jcod.dir", openJdkJcodDir.get().asFile.absolutePath)
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
