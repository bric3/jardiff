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
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            // Add javadoc JAR to publication
            artifact(tasks.named("javadocJar"))

            // Ensure version ends with -SNAPSHOT for Maven Central snapshots compatibility
            // Convert versions like "0.1.0.2+abc" or "0.1.0.0+abc+DIRTY" to "0.1.0-SNAPSHOT"
            // Keep clean tagged releases like "0.1.0" as-is
            version = project.version.toString()
                .let { v ->
                    if (!v.endsWith("-SNAPSHOT") && (v.contains('+') || v.matches(Regex(".*\\.\\d+\\.\\d+\\.\\d+\\.\\d+.*")))) {
                        // Remove build metadata (everything after +) and the extra commit count
                        v.substringBefore('+').replace(Regex("\\.\\d+$"), "") + "-SNAPSHOT"
                    } else {
                        v
                    }
                }

            pom {
                name = "jardiff"
                description = "A tool to compare JAR files and report differences"
                url = "https://github.com/bric3/jardiff"

                licenses {
                    license {
                        name = "Mozilla Public License Version 2.0"
                        url = "https://www.mozilla.org/en-US/MPL/2.0/"
                    }
                }

                developers {
                    developer {
                        id = "bric3"
                        name = "Brice Dutheil"
                        email = "brice.dutheil@gmail.com"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/bric3/jardiff.git"
                    developerConnection = "scm:git:ssh://github.com:bric3/jardiff.git"
                    url = "https://github.com/bric3/jardiff"
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project

    // Only sign if we have signing credentials
    val hasFileSigning = project.hasProperty("signing.keyId") &&
                        project.hasProperty("signing.password") &&
                        project.hasProperty("signing.secretKeyRingFile")

    when {
        // Configure in-memory signing if credentials are provided
        signingKey != null && signingPassword != null -> {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["maven"])
        }
        // File-based signing: plugin will automatically use signing.* properties
        hasFileSigning -> {
            sign(publishing.publications["maven"])
        }
    }
}

pluginManager.withPlugin("com.gradleup.shadow") {
    afterEvaluate {
        publishing {
            publications {
                named<MavenPublication>("maven") {
                    artifact(tasks.named("shadowJar"))

                    // Fix packaging in POM
                    pom {
                        packaging = "jar"
                    }
                }
            }
        }
    }
}

pluginManager.withPlugin("java-test-fixtures") {
    afterEvaluate {
        publishing {
            publications {
                named<MavenPublication>("maven") {
                    // Test-fixtures capabilities are published via Gradle module metadata
                    suppressPomMetadataWarningsFor("testFixturesApiElements")
                    suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
                }
            }
        }
    }
}