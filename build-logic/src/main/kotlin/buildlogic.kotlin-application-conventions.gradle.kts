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
    id("buildlogic.kotlin-common-conventions")
    application
    id("com.gradleup.shadow")
}



tasks {
    shadowJar {
        archiveBaseName = project.name
        destinationDirectory = project.layout.buildDirectory.dir("shadowed-app")
        // removes the `-all` classifier from the artifact name
        archiveClassifier = ""
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
}

