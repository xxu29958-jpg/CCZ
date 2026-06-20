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
    testImplementation(kotlin("test-junit5"))
}

tasks.register<JavaExec>("runSelfTest") {
    group = "verification"
    description = "Runs the deterministic game-core smoke self-test."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ccz.core.selftest.SelfTestKt")
}

tasks.test {
    useJUnitPlatform()
}
