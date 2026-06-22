plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
    // AGP 9.0+ ships built-in Kotlin (the kotlin-android plugin is removed); :app compiles
    // Kotlin via AGP's bundled compiler (2.3.10). The Compose compiler plugin must match that
    // compiler version, so it is pinned to 2.3.10 here (the JVM modules stay on 2.2.21 — a
    // uniform Kotlin bump is a separate slice per the version-baseline discipline).
    kotlin("plugin.compose") version "2.3.10" apply false
    id("com.android.application") version "9.2.0" apply false
    id("dev.detekt") version "2.0.0-alpha.3" apply false
}

// Strict-equality test-count gate (mirrors xiaopiaojia's assertAndroidTestCountEqualsBaseline).
// Counts @Test methods across module test sources; fails on any drift from the committed
// baseline so adding/removing tests is a deliberate, reviewed change.
tasks.register("assertTestCountEqualsBaseline") {
    group = "verification"
    description = "Fails if the @Test method count drifts from config/test-count-baseline.txt."
    val baselineFile = file("config/test-count-baseline.txt")
    val testRoots = listOf("game-core", "native-content", "save-io").map { file("$it/src/test") }
    val testAnnotation = Regex("""(?m)^\s*@Test\b""")
    doLast {
        val actual = testRoots
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sumOf { testAnnotation.findAll(it.readText()).count() }
        val baseline = baselineFile.readText().trim().toInt()
        check(actual == baseline) {
            "test count drift: actual=$actual baseline=$baseline. " +
                "If intentional, update ${baselineFile.path} in the same diff."
        }
        println("OK test count == baseline ($actual)")
    }
}
