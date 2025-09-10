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
    id("buildlogic.kotlin-application-conventions")
}

dependencies {
    implementation(project(":utilities"))
    implementation(libs.picocli)
}

application {
    mainClass = "io.github.bric3.jardiff.app.Main"
}


tasks {
    // use ./gradlew :app:run --args="arg1 arg2"
    val run by existing(JavaExec::class) {
    }
}