plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
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
