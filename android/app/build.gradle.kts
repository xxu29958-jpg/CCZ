plugins {
    // AGP 9.0+ has built-in Kotlin support, so the kotlin-android plugin is intentionally
    // NOT applied (applying it is an error on AGP 9). The Compose compiler plugin still
    // applies separately and matches AGP's bundled Kotlin compiler.
    id("com.android.application")
    kotlin("plugin.compose")
    id("dev.detekt")
}

android {
    namespace = "com.ccz.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ccz.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // "gray" = 灰度内部分发渠道; the [machine-gated] Android :app gates run on this
        // variant (detektGrayDebug / lintGrayDebug / assembleGrayRelease — see
        // CCZ_ENGINE_RULES §Android App Gates).
        create("gray") {
            dimension = "distribution"
        }
    }

    buildTypes {
        getByName("release") {
            // R8/minify is a future gate (see CCZ_ENGINE_RULES §Android App Gates, the
            // still-[aspirational] sub-list); the shell assembles an unsigned release
            // without shrinking for now.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        // Kotlin's jvmTarget defaults to this under AGP built-in Kotlin.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

detekt {
    toolVersion = "2.0.0-alpha.3"
    config.setFrom(files("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
}

dependencies {
    implementation(project(":game-core"))

    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}

// :app test-count gate — mirrors the root assertTestCountEqualsBaseline but scoped to the
// app's own test sources and its own baseline (matches the Android future-gate task name).
tasks.register("assertAndroidTestCountEqualsBaseline") {
    group = "verification"
    description = "Fails if the :app @Test method count drifts from config/android-test-count-baseline.txt."
    val baselineFile = file("config/android-test-count-baseline.txt")
    val testRoots = listOf(file("src/test"), file("src/androidTest"))
    val testAnnotation = Regex("""(?m)^\s*@Test\b""")
    doLast {
        val actual = testRoots
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sumOf { testAnnotation.findAll(it.readText()).count() }
        val baseline = baselineFile.readText().trim().toInt()
        check(actual == baseline) {
            "android test count drift: actual=$actual baseline=$baseline. " +
                "If intentional, update ${baselineFile.path} in the same diff."
        }
        println("OK android test count == baseline ($actual)")
    }
}
