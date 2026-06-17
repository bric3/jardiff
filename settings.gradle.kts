import com.javiersc.gradle.version.GradleVersion
import com.javiersc.semver.project.gradle.plugin.VersionMapper

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
    id("com.gradle.develocity") version "4.4.2"
    id("com.gradleup.nmcp.settings") version "1.5.0"
    id("com.javiersc.semver") version "0.9.0"
}

class JardiffVersionMapper(private val rootDirPath: String) : VersionMapper {
    @Transient
    private var shortHeadHash: String? = null

    override fun map(version: GradleVersion): String =
        if (version.commits == 0 && version.metadata.isNullOrBlank()) {
            "${version.major}.${version.minor}.${version.patch}"
        } else if (version.hash == null && version.metadata == "DIRTY") {
            version.copy(hash = shortHeadHash(), metadata = "DIRTY").toString()
        } else {
            version.toString()
        }

    // Canot use a provider or value source here, as this runs inside semver's
    // own VersionValueSource, and having a provider here would prevent serialization
    private fun shortHeadHash(): String =
        shortHeadHash ?: run {
            val process = ProcessBuilder("git", "-C", rootDirPath, "rev-parse", "--short=7", "HEAD")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "git rev-parse --short=7 HEAD failed with exit code $exitCode: $output"
            }
            output.also { shortHeadHash = it }
        }
}

semver {
    // Workaround to avoid having commit count and metadata in the version on the tagged commit
    mapVersion(JardiffVersionMapper(rootDir.absolutePath))
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "jardiff"

include(
    "jardiff-cli",
    "jardiff-differ",
    "jardiff-javap",
    "jardiff-jcod",
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
