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
    id("com.gradle.develocity") version "4.1.1"
}

rootProject.name = "jardiff"

include(
    "jardiff",
    "jardiff-differ"
)

develocity {
    val gradleStartParameter = gradle.startParameter

    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing {
            onlyIf {
                (System.getenv("CI") != null) or
                        it.buildResult.failures.isNotEmpty() or
                        gradleStartParameter.isBuildScan
            }
        }
    }
}