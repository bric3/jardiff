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
        // removes the `-all` classifier from the artifact name
        archiveClassifier = ""
        // Note: This JAR should not be executable (Maven Central requirement)
    }

    // Create executable JAR with -app suffix for distribution
    val executableShadowJar by registering(ExecutableJarTask::class) {
        dependsOn(shadowJar)
        inputJar = shadowJar.flatMap { it.archiveFile }
        archiveBaseName = project.name
        // archiveVersion, archiveClassifier, and archiveExtension use conventions
        // Output: {project.name}-{version}-app.jar
    }

    // Make build depend on both regular and executable JARs
    named(JavaBasePlugin.BUILD_TASK_NAME) {
        dependsOn(executableShadowJar)
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
