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
    id("jardiff.kotlin-application-conventions")
    id("jardiff.maven-publication-conventions")
    id("jardiff.graalvm-native-image-conventions")
}

val commandName = "jardiff"

dependencies {
    implementation(project(":jardiff-differ"))
    implementation(libs.picocli)

    add(
        "jdkModuleAccessManifests",
        project(mapOf("path" to ":jardiff-javap", "configuration" to "jdkModuleAccessManifestElements"))
    )
}

application {
    mainClass = "io.github.bric3.jardiff.app.Main"
    applicationName = commandName
}


tasks {
    // use ./gradlew :jardiff-cli:run --args="arg1 arg2"
    val run by existing(JavaExec::class) {
    }

    shadowJar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }
}
