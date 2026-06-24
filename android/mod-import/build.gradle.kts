plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("dev.detekt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

detekt {
    toolVersion = "2.0.0-alpha.3"
    config.setFrom(files("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
}

dependencies {
    implementation(project(":native-content"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(project(":game-core"))
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
