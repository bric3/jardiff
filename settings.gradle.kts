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
    id("org.kordamp.gradle.insight") version "0.54.0"
}

rootProject.name = "jardiff"

include(
    "jardiff",
    "jardiff-differ"
)

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