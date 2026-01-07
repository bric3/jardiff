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
    id("jardiff.kotlin-application-conventions")
    id("org.graalvm.buildtools.native")
}

val graalvmLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
    nativeImageCapable = true
}

fun getTargetTriple(): String {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()

    val arch = when (osArch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> osArch
    }

    return when {
        osName.contains("mac") || osName.contains("darwin") -> "$arch-apple-darwin"
        osName.contains("win") -> "$arch-windows-msvc"
        osName.contains("nux") -> "$arch-linux-gnu"
        else -> "$arch-unknown-unknown"
    }
}

val isCI = providers.environmentVariable("CI")
    .orElse(providers.environmentVariable("GITHUB_ACTIONS"))
    .isPresent

graalvmNative {
    toolchainDetection = true

    binaries {
        named("main") {
            javaLauncher.set(graalvmLauncher)

            imageName = if (isCI) {
                "${project.name}-${getTargetTriple()}"
            } else {
                project.name
            }
            mainClass.set(application.mainClass)

            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "-H:+PrintClassInitialization",

                // Generic resource patterns (common across projects)
                "-H:IncludeResources=.*\\.properties$",
                "-H:IncludeResources=.*\\.xml$",

                // Optimization flags for fast startup
                "-O3",
                "--gc=serial",
                "-march=compatibility",

                // Debugging and diagnostics
                "-H:+DashboardAll",
                "-H:DashboardDump=${project.layout.buildDirectory.get()}/native-image-dashboard"
            )

            jvmArgs.addAll("-Xmx4g")
        }

        named("test") {
            buildArgs.addAll(
                "--verbose",
                "-H:+ReportExceptionStackTraces"
            )
        }
    }

    metadataRepository {
        enabled = true
    }
}

tasks {
    nativeCompile {
        dependsOn("shadowJar")
    }

    generateResourcesConfigFile {
        dependsOn("shadowJar")
    }

    register("generateNativeImageConfig") {
        group = "graalvm"
        description = "Generate native image configuration using the tracing agent"
        doLast {
            println("""
                To generate native image configuration, run:
                  ./gradlew -Pagent :jardiff:run --args="<jar1> <jar2>"
                  ./gradlew -Pagent :jardiff:test

                Configuration will be merged with existing files in:
                  src/main/resources/META-INF/native-image/
            """.trimIndent())
        }
    }
}