pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.1.1"
}

rootProject.name = "jardiff"

include("app", "utilities")

develocity {
    buildScan {
        termsOfUseAgree.set("https://gradle.com/terms-of-service")
        termsOfUseAgree.set("yes")
    }
}