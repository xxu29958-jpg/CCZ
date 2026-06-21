plugins {
    kotlin("jvm")
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
    // SaveCodec / SaveEnvelope live in game-core; kotlinx-serialization reaches us
    // transitively on the runtime classpath through game-core (this module never
    // touches serialization directly — it only persists the codec's text output).
    implementation(project(":game-core"))
    testImplementation(kotlin("test-junit5"))
}

tasks.register<JavaExec>("runSelfTest") {
    group = "verification"
    description = "Runs the save-io atomic write/read smoke self-test."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ccz.saveio.selftest.SaveIoSelfTestKt")
}

tasks.test {
    useJUnitPlatform()
}
