plugins {
    id("buildlogic.kotlin-application-conventions")
}

dependencies {
    implementation(project(":utilities"))
    implementation(libs.picocli)
}

application {
    mainClass = "io.github.bric3.jardiff.app.Main"
}


tasks {
    // use ./gradlew :app:run --args="arg1 arg2"
    val run by existing(JavaExec::class) {
    }
}