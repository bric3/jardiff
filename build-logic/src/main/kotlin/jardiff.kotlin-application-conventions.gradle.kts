import io.github.bric3.gradle.executableArchive.ExecutableJarTask

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
        archiveClassifier = ""
        // Note: This JAR should not be executable (Maven Central requirement)
    }

    val executableShadowJar by registering(ExecutableJarTask::class) {
        inputJar = shadowJar.flatMap { it.archiveFile }
        archiveBaseName = project.name
    }

    register<Copy>("install") {
        from(executableShadowJar)
        into(File(System.getProperty("user.home"), "bin"))
        rename { "jardiff" }
    }

    // Make build depend on both regular and executable JARs
    named(JavaBasePlugin.BUILD_TASK_NAME) {
        dependsOn(executableShadowJar)
    }

    startScripts {
        dependsOn(shadowJar)
    }

    // empty javadocJar to satisfy maven central requirements
    register<Jar>(JAVADOC_JAR_TASK_NAME) {
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
