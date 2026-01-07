import MakeJarExecutableAction.Companion.makeExecutable

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

        makeExecutable()
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
