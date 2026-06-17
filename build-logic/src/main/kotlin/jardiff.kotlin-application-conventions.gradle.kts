import io.github.bric3.gradle.executableArchive.ExecutableJarTask
import org.gradle.api.tasks.compile.JavaCompile

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

val jdkModuleAccessManifests = configurations.named(JDK_MODULE_ACCESS_MANIFESTS_CONFIGURATION_NAME)

val mergeJdkModuleAccessManifest by tasks.registering(MergeJdkModuleAccessManifest::class) {
    description = "Merge runtime manifest entries required by dependency modules."
    inputFiles.from(jdkModuleAccessManifests)
    outputFile.set(layout.buildDirectory.file("generated/jdk-module-access/MANIFEST.MF"))
}

// Separate source set for the Java 8 Main class
val java8Main by sourceSets.creating {
    java {
        srcDir("src/java8Main/java")
    }
}

tasks {
    named<JavaCompile>(java8Main.compileJavaTaskName) {
        options.release.set(8)
        classpath = files() // Ensure no dependencies are used
    }

    jar {
        enabled = false
    }

    shadowJar {
        from(java8Main.output)
        manifest.from(mergeJdkModuleAccessManifest.flatMap { it.outputFile })

        archiveBaseName = project.name
        archiveClassifier = ""
        // Note: This JAR should not be executable (Maven Central requirement)
    }

    val executableShadowJar by registering(ExecutableJarTask::class) {
        inputJar = shadowJar.flatMap { it.archiveFile }
        archiveBaseName = project.name
    }

    register<Copy>("install") {
        description = "Install `jardiff` command to `\$HOME/bin`"
        from(executableShadowJar)
        into(File(System.getProperty("user.home"), "bin"))
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

afterEvaluate {
    tasks.named<Copy>("install") {
        rename("^.*$", application.applicationName)
    }
}
