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

graalvmNative {
    toolchainDetection = true

    binaries {
        named("main") {
            javaLauncher.set(graalvmLauncher)

            imageName = project.name
            mainClass.set(application.mainClass)

            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "-H:+PrintClassInitialization",

                // Required for picocli - initialize at build time for faster startup
                "--initialize-at-build-time=picocli",
                
                // Resource configuration for Tika and properties files
                "--enable-url-protocols=http,https",
                "-H:IncludeResources=.*\\.properties$",
                "-H:IncludeResources=.*\\.xml$",
                "-H:IncludeResources=org/apache/tika/.*",

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