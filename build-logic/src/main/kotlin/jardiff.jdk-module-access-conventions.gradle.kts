/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.jvm.tasks.Jar

/**
 * Configures the JDK module access extension.
 *
 * The `jdkModuleAccess` extension is responsible for managing the JDK modules
 * and internal package exports that are used by the project and need to be available
 * to downstream projects. This extension prepares module access data, and it can also
 * define extra manifest entries.
 */
abstract class JdkModuleAccessExtension {
    abstract val modules: SetProperty<String>
    abstract val exports: SetProperty<String>
    abstract val extraManifestEntries: MapProperty<String, String>
}

val jdkModuleAccess = extensions.create<JdkModuleAccessExtension>("jdkModuleAccess").apply {
    modules.convention(emptySet())
    exports.convention(emptySet())
    extraManifestEntries.convention(emptyMap())
}

fun compileArgumentProvider() = objects.newInstance(JdkModuleCompileArgumentProvider::class.java).also { provider ->
    provider.modules = jdkModuleAccess.modules
    provider.exports = jdkModuleAccess.exports
}

fun runtimeArgumentProvider() = objects.newInstance(JdkModuleRuntimeArgumentProvider::class.java).also { provider ->
    provider.exports = jdkModuleAccess.exports
}

val generateJdkModuleAccessManifest by tasks.registering(GenerateJdkModuleAccessManifest::class) {
    description = "Generate manifest metadata required for JDK module access."
    exports = jdkModuleAccess.exports
    extraManifestEntries = jdkModuleAccess.extraManifestEntries
    outputFile = layout.buildDirectory.file("generated/jdk-module-access/MANIFEST.MF")
}

tasks {
    // Keep the producer artifact self-describing for direct jar consumers.
    named<Jar>(JavaPlugin.JAR_TASK_NAME) {
        manifest.attributes(
            "Add-Exports" to jdkModuleAccess.exports.map { exportedPackages ->
                exportedPackages.joinToString(" ")
            }
        )
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgumentProviders.add(compileArgumentProvider())
    }

    withType<Test>().configureEach {
        jvmArgumentProviders.add(runtimeArgumentProvider())
    }

    withType<JavaExec>().configureEach {
        jvmArgumentProviders.add(runtimeArgumentProvider())
    }
}

// This is intentionally separate from runtimeElements: the file is metadata to merge/read,
// not a jar to put on a runtime classpath or expand into a Shadow jar.
configurations.register(JDK_MODULE_ACCESS_MANIFEST_ELEMENTS_CONFIGURATION_NAME) {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(JDK_MODULE_ACCESS_MANIFEST_USAGE))
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, JDK_MODULE_ACCESS_MANIFEST_ARTIFACT_TYPE)
    }
    outgoing.artifact(generateJdkModuleAccessManifest.flatMap { it.outputFile })
}
