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

// Module dependency-direction gate — machine-enforces the high-cohesion/low-coupling 总纲
// (GENERAL_ENGINEERING_RULES §Module Boundaries) for the inter-module DAG: game-core is the pure
// authority core and may depend on NO other module; native-content / save-io / app may depend only
// on game-core. The module set is read from settings.gradle.kts (the topology source of truth), so a
// module added there but NOT registered in `allowed` fails closed ("unregistered module") rather than
// being silently un-gated; a registered module whose build script declares an out-of-DAG project(":...")
// edge — especially a reverse edge into game-core — also fails. Coverage boundary (honest): it
// recognizes the literal include(":x") / project(":x") string forms CCZ uses today; it does NOT see
// type-safe `projects.*` accessors, project(path = ...), version-catalog, or convention-plugin edges —
// migrating to any of those means updating this gate. Parses build-script TEXT (same style as
// assertTestCountEqualsBaseline above), not cross-project Gradle configurations, to stay
// configuration-cache-friendly and API-stable.
tasks.register("assertModuleDependencyDirection") {
    group = "verification"
    description = "Fails if a settings.gradle.kts module is unregistered or has a project(\":...\") edge " +
        "violating the allowed native-content/save-io/app -> game-core DAG (game-core depends on nothing)."
    val allowed = mapOf(
        "game-core" to emptySet<String>(),
        "native-content" to setOf("game-core"),
        "save-io" to setOf("game-core"),
        "app" to setOf("game-core"),
    )
    val settingsFile = file("settings.gradle.kts")
    val includeRe = Regex("""include\(["']:([\w-]+)["']\)""")
    val projectDep = Regex("""project\(["']:([\w-]+)["']\)""")
    doLast {
        val modules = includeRe.findAll(settingsFile.readText()).map { it.groupValues[1] }.toList()
        val violations = mutableListOf<String>()
        modules.forEach { module ->
            val permitted = allowed[module]
            if (permitted == null) {
                violations += "unregistered module '$module' (register it in the allowed DAG in build.gradle.kts)"
                return@forEach
            }
            val buildFile = file("$module/build.gradle.kts")
            if (buildFile.exists()) {
                val deps = projectDep.findAll(buildFile.readText()).map { it.groupValues[1] }.toSet()
                (deps - permitted).forEach { bad -> violations += "$module -> $bad" }
            }
        }
        (allowed.keys - modules.toSet()).forEach { stale ->
            violations += "stale allowed entry '$stale' (no such module in settings.gradle.kts)"
        }
        check(violations.isEmpty()) {
            "module dependency-direction violations (allowed DAG: native-content/save-io/app -> " +
                "game-core; game-core depends on nothing): $violations"
        }
        println("OK module dependency direction (${modules.size} modules from settings, DAG enforced)")
    }
}

// Text-encoding gate — machine-enforces §Windows / PowerShell Rules: every git-tracked text file must
// be valid UTF-8, every .ps1 must be UTF-8 with BOM (PowerShell 5.1 reads a BOM-less file as ANSI →
// 中文乱码), and no file may contain a U+FFFD replacement char (the tell-tale of mojibake already
// baked into the file by a bad decode-then-save). Scans `git ls-files` — tracked content only, so
// tool-chain / build / .tools dirs are out of scope — and runs cross-platform (CI ubuntu + Windows
// local both have git). Coverage boundary: the text extensions listed below; binaries and untracked
// files are not checked.
tasks.register("verifyTextEncoding") {
    group = "verification"
    description = "Fails if a git-tracked text file is not valid UTF-8, a .ps1 lacks a UTF-8 BOM, " +
        "or contains a U+FFFD replacement char (mojibake)."
    val repoRoot = rootDir.parentFile
    val exts = setOf("md", "kt", "kts", "txt", "yml", "yaml", "ps1", "properties")
    doLast {
        // -z NUL-delimits output and -c core.quotepath=false keeps non-ASCII paths (e.g. Chinese
        // filenames) verbatim, so they are NOT octal-escaped and then silently skipped — those are
        // exactly the files an encoding gate must inspect. Exit code is checked so a broken git /
        // non-checkout fails loudly instead of yielding an empty (vacuous-pass) scan.
        val proc = ProcessBuilder("git", "-c", "core.quotepath=false", "ls-files", "-z")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        val raw = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        check(proc.waitFor() == 0) { "git ls-files failed (exit ${proc.exitValue()}): $raw" }
        val tracked = raw.split(Char(0)).filter { it.isNotEmpty() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val violations = mutableListOf<String>()
        var checked = 0
        tracked.forEach { rel ->
            val ext = rel.substringAfterLast('.', "").lowercase()
            if (ext !in exts) return@forEach
            val target = repoRoot.resolve(rel)
            if (!target.isFile) return@forEach
            checked++
            val bytes = target.readBytes()
            val text = try {
                decoder.reset().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                violations += "$rel: not valid UTF-8 (${e.message})"
                return@forEach
            }
            if (text.any { it.code == 0xFFFD }) violations += "$rel: contains U+FFFD replacement char (mojibake)"
            val hasBom = bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(bom)
            if (ext == "ps1" && !hasBom) violations += "$rel: .ps1 must be UTF-8 with BOM"
        }
        // Fail closed on a vacuous scan: a discipline gate must not pass green having inspected zero files.
        check(checked > 0) { "verifyTextEncoding scanned 0 files — git ls-files returned nothing (broken checkout?)" }
        check(violations.isEmpty()) { "text-encoding violations:\n  " + violations.joinToString("\n  ") }
        println("OK text encoding ($checked tracked text files: UTF-8 valid, .ps1 BOM, no mojibake)")
    }
}
