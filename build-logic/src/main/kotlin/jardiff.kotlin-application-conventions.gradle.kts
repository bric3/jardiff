import org.gradle.kotlin.dsl.support.serviceOf

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
    id("jardiff.kotlin-common-conventions")
    application
    id("com.gradleup.shadow")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveBaseName = project.name
        // removes the `-all` classifier from the artifact name
        archiveClassifier = ""

        val tmpDirProvider = project.layout.buildDirectory.dir("tmp/$name/executable-jar")
        val fs = project.serviceOf<FileSystemOperations>()
        doLast {
            val jarFile = archiveFile.get().asFile
            val tmpDir = tmpDirProvider.get().asFile
            val tmpJarFile = File(tmpDir, "${jarFile.name}.tmp")

            fs.copy {
                from(jarFile)
                into(tmpJarFile.parentFile)
                rename { tmpJarFile.name }
            }
            fs.delete {
                delete(jarFile)
            }

            jarFile.outputStream().use { output ->
                output.write("#!/bin/sh\n\nexec java \$JAVA_OPTS -jar \$0 \"\$@\"\n\n".toByteArray())
                tmpJarFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            jarFile.setExecutable(true, false)

            fs.delete {
                delete(tmpJarFile)
            }
        }
    }

    startScripts {
        dependsOn(shadowJar)
    }

    // empty javadocJar to satisfy maven central requirements
    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
    }

    distTar {
        enabled = false
    }
    distZip {
        enabled = false
    }
    // Disable app distribution tasks as well
    shadowDistTar {
        enabled = false
    }
    shadowDistZip {
        enabled = false
    }
}

