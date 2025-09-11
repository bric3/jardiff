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
    id("jardiff.kotlin-library-conventions")
    `java-test-fixtures`
}

dependencies {
    implementation(libs.bundles.asm)
    implementation(libs.javadiffutils)
    implementation(libs.tika)
}

tasks.test {
    val fixtureJarPath = tasks.testFixturesJar.map { it.archiveFile.get().asFile.absolutePath }
    val fixturesKotlinClassesPath = sourceSets.testFixtures.map { it.kotlin.classesDirectory.get().asFile.absolutePath }
    doFirst {
        systemProperties("text-fixtures.jar.path" to fixtureJarPath.get())
        systemProperties("text-fixtures.kotlin.classes.path" to fixturesKotlinClassesPath.get())
    }
}