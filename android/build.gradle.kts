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
// authority core and may depend on NO other module; native-content / save-io depend only on game-core,
// and :app (the composition root) depends on native-content + game-core (app -> native-content ->
// game-core, still acyclic and single-direction). The module set is read from settings.gradle.kts (the topology source of truth), so a
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
        "violating the allowed DAG: native-content/save-io -> game-core, app -> {game-core, native-content} " +
        "(game-core depends on nothing)."
    val allowed = mapOf(
        "game-core" to emptySet<String>(),
        "native-content" to setOf("game-core"),
        "save-io" to setOf("game-core"),
        // :app is the composition root — it loads + assembles the native-content campaign pack, so it
        // may depend on native-content as well as game-core. The DAG stays acyclic and single-direction
        // (app -> native-content -> game-core); game-core still depends on nothing. See ADR-0005.
        "app" to setOf("game-core", "native-content"),
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
            "module dependency-direction violations (allowed DAG: native-content/save-io -> game-core, " +
                "app -> {game-core, native-content}; game-core depends on nothing): $violations"
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

// Dependency-version stability gate — machine-enforces §Dependency Governance's ban on alpha / beta /
// rc / snapshot (and other pre-release) dependency, plugin, or build-tool versions on mainline
// ("唯一例外必须有 ADR"). Scans build-script TEXT (same configuration-cache-friendly, API-stable style as
// the sibling gates above) across every Gradle file — root build.gradle.kts, settings.gradle.kts, each
// settings-declared module's build.gradle.kts (module set read from settings.gradle.kts so a NEW module
// is gated automatically, fail-closed), and gradle/wrapper/gradle-wrapper.properties — for the version
// forms CCZ uses: the plugin-DSL `version` literal, a named-arg `version =` literal, the detekt
// `toolVersion` literal, Maven coordinate strings (group:artifact:version — robust to a trailing
// classifier, the 4th segment is ignored), and the wrapper's gradle-<v>-bin/all.zip distributionUrl. (The
// examples here are deliberately phrased so they do NOT match these regexes — the gate scans its own
// source too, mirroring how assertModuleDependencyDirection uses non-matching `...` placeholders. NB: the
// scan is over raw TEXT including comments, so do NOT write a matchable version literal in prose — it will
// be checked like a real declaration and FAIL the build if it looks pre-release.)
// A version is STABLE iff it is purely numeric/dotted (1.7.3, 2026.05.00) or its final qualifier is exactly
// a release marker (RELEASE / FINAL / GA); ANY other qualifier (alpha, beta, rc, snapshot, M-milestone, eap,
// dev, a -jre/-android build variant, …) is treated as pre-release and fails — UNLESS its exact version
// string is in `adrSanctioned` below, which mirrors the ADR-0003 detekt-2.0-alpha exception. Bumping the
// sanctioned pre-release (e.g. to detekt alpha.5, which ADR-0003 permits in a separate slice) must update
// BOTH the ADR and this allow-set in lockstep. Coverage boundary (honest): recognizes the literal forms
// above; does NOT see version catalogs (libs.versions.toml — absent today), dynamic/range versions (1.+,
// latest.release, [1,2)), BOM-managed transitive versions, or versions hidden behind indirection (a
// `val`/property reference, `id(...).version(...)` method form) — a pre-release in any of those is NOT
// caught. Introducing any of those forms means extending this gate.
tasks.register("assertStableDependencyVersions") {
    group = "verification"
    description = "Fails if a Gradle build script or the wrapper declares an alpha/beta/rc/snapshot " +
        "(pre-release) version that is not an ADR-sanctioned exception (see docs/DECISIONS/0003)."
    // Exact pre-release version strings allowed by an ADR. Keep in lockstep with docs/DECISIONS/.
    val adrSanctioned = mapOf(
        "2.0.0-alpha.3" to "ADR-0003 (detekt 2.0 type-resolution)",
    )
    val settingsFile = file("settings.gradle.kts")
    val wrapperFile = file("gradle/wrapper/gradle-wrapper.properties")
    val includeRe = Regex("""include\(["']:([\w-]+)["']\)""")
    // Capture only up to the closing quote ([^"]+ stops there) — patterns deliberately do NOT end with a
    // literal " to avoid Kotlin's raw-string trailing-quote lexer ambiguity.
    val pluginVersionRe = Regex("""version\s+"([^"]+)""")          // plugin-DSL space form
    val namedArgVersionRe = Regex("""\bversion\s*=\s*"([^"]+)""")  // named-arg = form; \b skips versionName / versionCode
    val toolVersionRe = Regex("""toolVersion\s*=\s*"([^"]+)""")
    val coordinateRe = Regex(""""([\w.\-]+:[\w.\-]+:[\w.\-]+)""")
    val distributionRe = Regex("""distributionUrl=.*?gradle-([\w.\-]+)-(?:bin|all)\.zip""")
    // STABLE iff purely numeric/dotted (1.7.3, 2026.05.00) OR the final qualifier is exactly a release
    // marker (1.0.0.RELEASE / x.Final / x.GA). Everything else is treated as pre-release and FAILS — this
    // is deliberately fail-closed: the RELEASE/FINAL/GA test is anchored to the trailing qualifier, not a
    // loose substring, so neither a pre-release whose qualifier merely CONTAINS those letters (2.0.0-omega
    // has "GA", 1.0.0.RELEASE-SNAPSHOT, 1.0-release-candidate-1) slips through as stable, NOR is a real
    // build-variant classifier (Guava's 33.0.0-jre / -android) silently accepted — such a classifier would
    // FAIL here and must be handled deliberately when introduced (add a recognized-classifier rule or an
    // adrSanctioned entry in that slice), never papered over.
    val numericRe = Regex("""^[0-9]+([.\-_][0-9]+)*$""")
    val releaseQualifierRe = Regex("""^[0-9]+([.\-_][0-9]+)*[.\-_](RELEASE|FINAL|GA)$""", RegexOption.IGNORE_CASE)
    fun isStable(v: String): Boolean = numericRe.matches(v) || releaseQualifierRe.matches(v)
    doLast {
        val modules = includeRe.findAll(settingsFile.readText()).map { it.groupValues[1] }.toList()
        // Relative paths kept as the source label so a violation names which module's build script —
        // every module file is "build.gradle.kts", so file.name alone would be ambiguous.
        val buildScripts = (listOf("build.gradle.kts", "settings.gradle.kts") +
            modules.map { "$it/build.gradle.kts" }).filter { file(it).exists() }
        val violations = mutableListOf<String>()
        var versionsChecked = 0
        fun checkVersion(source: String, v: String) {
            versionsChecked++
            if (!isStable(v) && v !in adrSanctioned) {
                violations += "$source: pre-release version '$v' (only ADR-sanctioned exceptions allowed)"
            }
        }
        buildScripts.forEach { rel ->
            val text = file(rel).readText()
            pluginVersionRe.findAll(text).forEach { checkVersion(rel, it.groupValues[1]) }
            namedArgVersionRe.findAll(text).forEach { checkVersion(rel, it.groupValues[1]) }
            toolVersionRe.findAll(text).forEach { checkVersion(rel, it.groupValues[1]) }
            coordinateRe.findAll(text).forEach { checkVersion(rel, it.groupValues[1].substringAfterLast(':')) }
        }
        // Gradle wrapper distribution: a pre-release Gradle (gradle-9.5-rc-1-bin.zip) is exactly what this
        // gate must catch. Fail closed if a distributionUrl is present but its version is unparseable,
        // rather than silently skipping the build tool's own version.
        if (wrapperFile.exists()) {
            val wrapperText = wrapperFile.readText()
            val match = distributionRe.find(wrapperText)
            when {
                match != null -> checkVersion(wrapperFile.name, match.groupValues[1])
                wrapperText.contains("distributionUrl=") ->
                    violations += "${wrapperFile.name}: distributionUrl present but Gradle version unparseable"
            }
        }
        // Fail closed on a vacuous scan: a discipline gate must not pass having checked zero versions.
        check(versionsChecked > 0) {
            "assertStableDependencyVersions checked 0 versions — no build scripts parsed (broken checkout?)"
        }
        check(violations.isEmpty()) {
            "dependency-version violations (no alpha/beta/rc/snapshot except ADR-sanctioned):\n  " +
                violations.joinToString("\n  ")
        }
        println("OK dependency versions ($versionsChecked checked: stable or ADR-sanctioned pre-release)")
    }
}
