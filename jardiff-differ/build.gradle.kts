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
    id("jardiff.maven-publication-conventions")
    `java-test-fixtures`
}

dependencies {
    implementation(libs.bundles.asm)
    implementation(libs.javadiffutils)
    implementation(libs.tika)
}

tasks {
    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }

    test {
        val fixtureJarPath = testFixturesJar.map { it.archiveFile.get().asFile.absolutePath }
        val fixturesKotlinClassesPath = sourceSets.testFixtures.map { it.kotlin.classesDirectory.get().asFile.absolutePath }
        val fixturesResourcesPath = sourceSets.testFixtures.map { it.output.resourcesDir }
        doFirst {
            systemProperties("text-fixtures.jar.path" to fixtureJarPath.get())
            systemProperties("text-fixtures.kotlin.classes.path" to fixturesKotlinClassesPath.get())
            systemProperties("text-fixtures.resources.path" to fixturesResourcesPath.get())
        }
    }
}