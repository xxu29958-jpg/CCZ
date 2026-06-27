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

// Offline converter entrypoint (NOT a gate): reads the user's local decrypted legacy tables and writes a
// playable real-data battle pack. Run e.g.:
//   ./gradlew :mod-import:generateLegacyBattle -PextractedDir=<dir> -PoutPath=<file>
tasks.register<JavaExec>("generateLegacyBattle") {
    group = "ccz"
    description = "Generate a real-data battle content pack from local legacy tables (offline converter)."
    mainClass.set("com.ccz.modimport.LegacyPackGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    args((project.findProperty("extractedDir") as String?).orEmpty(), (project.findProperty("outPath") as String?).orEmpty())
}

// Faithful FULL-STAGE variant: the real dispatched enemy + allied roster on the complete 23x16 map (vs the
// curated crop above). Run e.g.:
//   ./gradlew :mod-import:generateLegacyFullStage -PextractedDir=<dir> -PoutPath=<file>
tasks.register<JavaExec>("generateLegacyFullStage") {
    group = "ccz"
    description = "Generate the faithful full-stage (real dispatch roster) battle pack (offline converter)."
    mainClass.set("com.ccz.modimport.LegacyPackGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    args((project.findProperty("extractedDir") as String?).orEmpty(), (project.findProperty("outPath") as String?).orEmpty(), "full")
}
