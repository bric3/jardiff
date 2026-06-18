/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import java.util.jar.Attributes
import java.util.jar.Manifest

const val JDK_MODULE_ACCESS_MANIFEST_ARTIFACT_TYPE = "jardiff-jdk-module-access-manifest"
const val JDK_MODULE_ACCESS_MANIFEST_USAGE = "jardiff-jdk-module-access-manifest"
const val JDK_MODULE_ACCESS_MANIFEST_ELEMENTS_CONFIGURATION_NAME = "jdkModuleAccessManifestElements"
const val JDK_MODULE_ACCESS_MANIFESTS_CONFIGURATION_NAME = "jdkModuleAccessManifests"

abstract class JdkModuleCompileArgumentProvider : CommandLineArgumentProvider {
    @get:Input
    abstract val modules: SetProperty<String>

    @get:Input
    abstract val exports: SetProperty<String>

    override fun asArguments(): Iterable<String> = buildList {
        val moduleNames = modules.get()
        if (moduleNames.isNotEmpty()) {
            add("--add-modules")
            add(moduleNames.joinToString(","))
        }
        exports.get().forEach { exportedPackage ->
            add("--add-exports")
            add("$exportedPackage=ALL-UNNAMED")
        }
    }
}

abstract class JdkModuleRuntimeArgumentProvider : CommandLineArgumentProvider {
    @get:Input
    abstract val exports: SetProperty<String>

    override fun asArguments(): Iterable<String> = exports.get()
        .map { exportedPackage -> "--add-exports=$exportedPackage=ALL-UNNAMED" }
}

abstract class JdkModuleAccessManifestArgumentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFiles: ConfigurableFileCollection

    // Convert resolved Add-Exports/Add-Opens manifest metadata into JVM flags
    // for tasks that do not launch from a jar manifest.
    override fun asArguments(): Iterable<String> {
        val attributes = parseManifestAttributes(inputFiles)
        return buildList {
            attributes["Add-Exports"]
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotEmpty() }
                ?.forEach { exportedPackage ->
                    add("--add-exports=$exportedPackage=ALL-UNNAMED")
                }
            attributes["Add-Opens"]
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotEmpty() }
                ?.forEach { openedPackage ->
                    add("--add-opens=$openedPackage=ALL-UNNAMED")
                }
        }
    }
}

/**
 * Produces the metadata artifact published by the producer convention.
 *
 * It is a real MANIFEST.MF so it can also be merged directly into application jars.
 */
abstract class GenerateJdkModuleAccessManifest : DefaultTask() {
    @get:Input
    abstract val exports: SetProperty<String>

    @get:Input
    abstract val extraManifestEntries: MapProperty<String, String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        val exportedPackages = exports.get()
        if (exportedPackages.isNotEmpty()) {
            manifest.mainAttributes.putValue("Add-Exports", exportedPackages.joinToString(" "))
        }
        extraManifestEntries.get().forEach { (name, value) ->
            manifest.mainAttributes.putValue(name, value)
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.outputStream().use(manifest::write)
    }
}

/**
 * Merges all resolved producer metadata into one MANIFEST.MF for the final application jar.
 */
abstract class MergeJdkModuleAccessManifest : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun merge() {
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        parseManifestAttributes(inputFiles).forEach { (name, value) ->
            manifest.mainAttributes.putValue(name, value)
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.outputStream().use(manifest::write)
    }
}

private fun parseManifestAttributes(inputFiles: ConfigurableFileCollection): Map<String, String> {
    val attributes = linkedMapOf<String, MutableList<String>>()
    inputFiles.files.sortedBy { it.absolutePath }.forEach { file ->
        val manifest = file.inputStream().use(::Manifest)
        manifest.mainAttributes.entries
            .filter { (name, _) -> name != Attributes.Name.MANIFEST_VERSION }
            .forEach { (name, value) ->
                attributes.getOrPut(name.toString()) { mutableListOf() }.add(value.toString())
            }
    }
    return attributes.mapValues { (_, values) ->
        values.flatMap { it.split(Regex("\\s+")) }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
    }
}
