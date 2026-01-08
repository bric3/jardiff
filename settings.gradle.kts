/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.3"
    id("com.gradleup.nmcp.settings") version "1.4.3"
    id("com.javiersc.semver") version "0.9.0"
}

semver {
    // Workaround to avoid having commit count and metadata in the version on the tagged commit
    mapVersion { gradleVersion ->
        if (gradleVersion.commits == 0 && gradleVersion.metadata.isNullOrBlank()) {
            "${gradleVersion.major}.${gradleVersion.minor}.${gradleVersion.patch}"
        } else {
            gradleVersion.toString()
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "jardiff-root"

include(
    "jardiff",
    "jardiff-differ"
)

nmcpSettings {
    centralPortal {
        username.set(providers.gradleProperty("mavenCentralUsername"))
        password.set(providers.gradleProperty("mavenCentralPassword"))
    }
}

develocity {
    val isCI = providers.environmentVariable("CI").isPresent
    val isIJSync = providers.systemProperty("idea.sync.active").filter { it.toBoolean() }.isPresent
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        publishing {
            onlyIf {
                isCI or isIJSync or it.buildResult.failures.isNotEmpty()
            }
        }
    }
}