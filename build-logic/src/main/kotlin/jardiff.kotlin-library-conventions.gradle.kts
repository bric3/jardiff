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
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("jardiff.kotlin-common-conventions")
    id("jardiff.jdk-module-access-consumer-conventions")
    `java-library`
    `jvm-test-suite`
    id("org.jetbrains.dokka")
}

tasks {
    val dokkaGenerate by existing

    register<Jar>(JAVADOC_JAR_TASK_NAME) {
        from(dokkaGenerate)
        archiveClassifier = "javadoc"
    }

    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit5)

            dependencies {
                implementation(libs.assertj.core)
            }
        }
    }
}
