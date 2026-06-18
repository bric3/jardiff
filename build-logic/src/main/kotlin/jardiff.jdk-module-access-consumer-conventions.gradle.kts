/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Consumer side of the JDK module-access metadata flow.
 *
 * Projects should add producer projects with:
 * ```
 * dependencies {
 *     jdkModuleAccessManifests(project(":jardiff-javap"))
 * }
 * ```
 * This configuration asks for the custom usage/artifactType published by
 * `jardiff.jdk-module-access-conventions`. Gradle then selects the matching
 * producer variant by attributes; no target configuration is named here.
 */
val jdkModuleAccessManifests = configurations.register(JDK_MODULE_ACCESS_MANIFESTS_CONFIGURATION_NAME) {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(JDK_MODULE_ACCESS_MANIFEST_USAGE))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, JDK_MODULE_ACCESS_MANIFEST_ARTIFACT_TYPE)
    }
}

// Application jars are launched with `java -jar`, so dependency jar manifests are not enough.
// Consumers that build a final jar can merge this generated manifest into that jar.
val mergeJdkModuleAccessManifest by tasks.registering(MergeJdkModuleAccessManifest::class) {
    description = "Merge runtime manifest entries required by dependency modules."
    inputFiles.from(jdkModuleAccessManifests)
    outputFile.set(layout.buildDirectory.file("generated/jdk-module-access/MANIFEST.MF"))
}

// Test and JavaExec do not read jar manifests from their classpath, so they need
// JVM arguments derived from the resolved metadata files.
fun moduleAccessArgumentProvider() =
    objects.newInstance(JdkModuleAccessManifestArgumentProvider::class.java).also { provider ->
        provider.inputFiles.from(jdkModuleAccessManifests)
    }

tasks {
    withType<Test>().configureEach {
        jvmArgumentProviders.add(moduleAccessArgumentProvider())
    }

    withType<JavaExec>().configureEach {
        jvmArgumentProviders.add(moduleAccessArgumentProvider())
    }
}
