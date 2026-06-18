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
    implementation(project(":jardiff-javap"))
    implementation(project(":jardiff-jcod"))
    implementation(libs.bundles.asm)
    implementation(libs.javadiffutils)
    implementation(libs.tika)

    testImplementation(libs.asm.tree)

    jdkModuleAccessManifests(project(":jardiff-javap"))
}

tasks {
    test {
        jvmArgumentProviders.add(
            objects.newInstance(TestFixturesArgumentProvider::class.java).also {
                it.fixtureJar = testFixturesJar.flatMap { task -> task.archiveFile }
                it.fixtureKotlinClasses.from(sourceSets.testFixtures.flatMap { it.kotlin.classesDirectory })
                it.fixtureResources.from(sourceSets.testFixtures.map { it.output.resourcesDir })
            }
        )
    }
}

abstract class TestFixturesArgumentProvider : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixtureJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtureKotlinClasses: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtureResources: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "-Dtext-fixtures.jar.path=${fixtureJar.get().asFile.absolutePath}",
        "-Dtext-fixtures.kotlin.classes.path=${fixtureKotlinClasses.singleFile.absolutePath}",
        "-Dtext-fixtures.resources.path=${fixtureResources.singleFile.absolutePath}"
    )
}
