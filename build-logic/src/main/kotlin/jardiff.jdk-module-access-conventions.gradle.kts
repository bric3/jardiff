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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

abstract class JdkModuleAccessExtension {
    abstract val modules: SetProperty<String>
    abstract val exports: SetProperty<String>
    abstract val extraManifestEntries: MapProperty<String, String>
}

val jdkModuleAccess = extensions.create<JdkModuleAccessExtension>("jdkModuleAccess")
jdkModuleAccess.modules.convention(emptySet())
jdkModuleAccess.exports.convention(emptySet())
jdkModuleAccess.extraManifestEntries.convention(emptyMap())

fun compileArgumentProvider() = objects.newInstance(JdkModuleCompileArgumentProvider::class.java).also { provider ->
    provider.modules.set(jdkModuleAccess.modules)
    provider.exports.set(jdkModuleAccess.exports)
}

fun runtimeArgumentProvider() = objects.newInstance(JdkModuleRuntimeArgumentProvider::class.java).also { provider ->
    provider.exports.set(jdkModuleAccess.exports)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgumentProviders.add(compileArgumentProvider())
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(runtimeArgumentProvider())
}

tasks.withType<JavaExec>().configureEach {
    jvmArgumentProviders.add(runtimeArgumentProvider())
}

val jdkModuleAccessManifestFile = layout.buildDirectory.file("generated/jdk-module-access/jdk-module-access.properties")
val generateJdkModuleAccessManifest by tasks.registering(GenerateJdkModuleAccessManifest::class) {
    description = "Generate manifest metadata required for JDK module access."
    exports.set(jdkModuleAccess.exports)
    extraManifestEntries.set(jdkModuleAccess.extraManifestEntries)
    outputFile.set(jdkModuleAccessManifestFile)
}

configurations.register(JDK_MODULE_ACCESS_MANIFEST_ELEMENTS_CONFIGURATION_NAME) {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(JDK_MODULE_ACCESS_MANIFEST_USAGE))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, JDK_MODULE_ACCESS_MANIFEST_ARTIFACT_TYPE)
    }
    outgoing.artifact(jdkModuleAccessManifestFile) {
        builtBy(generateJdkModuleAccessManifest)
    }
}
