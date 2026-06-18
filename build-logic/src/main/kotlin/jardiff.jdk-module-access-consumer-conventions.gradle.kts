/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

val jdkModuleAccessManifests = configurations.register(JDK_MODULE_ACCESS_MANIFESTS_CONFIGURATION_NAME) {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(JDK_MODULE_ACCESS_MANIFEST_USAGE))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, JDK_MODULE_ACCESS_MANIFEST_ARTIFACT_TYPE)
    }
}

fun moduleAccessArgumentProvider() =
    objects.newInstance(JdkModuleAccessManifestArgumentProvider::class.java).also { provider ->
        provider.inputFiles.from(jdkModuleAccessManifests)
    }

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(moduleAccessArgumentProvider())
}

tasks.withType<JavaExec>().configureEach {
    jvmArgumentProviders.add(moduleAccessArgumentProvider())
}
