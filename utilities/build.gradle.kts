plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    implementation(libs.bundles.asm)
    implementation(libs.javadiffutils)
}
