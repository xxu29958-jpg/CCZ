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

// Production one-stage promotion: emits a validated+assembled native pack for a ready legacy catalog row.
// Optional -PcontentVersion=<version> stamps the generated pack for a bundled campaign release.
//   ./gradlew :mod-import:generateLegacyStage -PextractedDir=<dir> -PoutPath=<file> -PstageId=2
tasks.register<JavaExec>("generateLegacyStage") {
    group = "ccz"
    description = "Generate one promoted native stage pack from local legacy scripts/maps/tables."
    mainClass.set("com.ccz.modimport.LegacyStagePackGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    val contentVersion = project.findProperty("contentVersion") as String?
    args(
        listOfNotNull(
            (project.findProperty("extractedDir") as String?).orEmpty(),
            (project.findProperty("outPath") as String?).orEmpty(),
            (project.findProperty("stageId") as String?).orEmpty(),
            contentVersion?.takeIf { it.isNotBlank() },
        ),
    )
}

// Offline commerce smoke: reads local decrypted legacy tables, verifies a paid product grants its native reward,
// and optionally checks whether that reward unlocks a legacy stage. Run e.g.:
//   ./gradlew :mod-import:verifyLegacyPurchase -PextractedDir=<dir> -PchargeId=trssgshz03 -PgkId=1
tasks.register<JavaExec>("verifyLegacyPurchase") {
    group = "ccz"
    description = "Verify legacy product -> native reward delivery -> optional stage unlock from local decrypted tables."
    mainClass.set("com.ccz.modimport.LegacyCommerceVerifier")
    classpath = sourceSets["main"].runtimeClasspath
    val extractedDir = (project.findProperty("extractedDir") as String?).orEmpty()
    val chargeId = (project.findProperty("chargeId") as String?).orEmpty()
    val gkId = (project.findProperty("gkId") as String?).orEmpty()
    args(listOfNotNull(extractedDir, chargeId, gkId.takeIf { it.isNotBlank() }))
}

// Full catalog migration: emits a native content pack containing every legacy item, product, reward, and stage
// unlock requirement. It validates the generated pack before writing it.
//   ./gradlew :mod-import:generateLegacyCatalog -PextractedDir=<dir> -PoutPath=<file>
tasks.register<JavaExec>("generateLegacyCatalog") {
    group = "ccz"
    description = "Generate a full native catalog pack from local legacy commerce/stage tables."
    mainClass.set("com.ccz.modimport.LegacyCatalogGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    args((project.findProperty("extractedDir") as String?).orEmpty(), (project.findProperty("outPath") as String?).orEmpty())
}

// Stage migration planning: scans dic_gk + Scenes/S_*.eex_new + terrainJson/terrainMap_*.json, validates the
// conservative script/map bindings with the existing map/deployment/objective importers, and writes a report.
//   ./gradlew :mod-import:planLegacyStages -PextractedDir=<dir> -PoutPath=<file>
tasks.register<JavaExec>("planLegacyStages") {
    group = "ccz"
    description = "Plan fail-closed legacy stage migration from local decrypted scripts and maps."
    mainClass.set("com.ccz.modimport.LegacyStageMigrationPlanner")
    classpath = sourceSets["main"].runtimeClasspath
    args((project.findProperty("extractedDir") as String?).orEmpty(), (project.findProperty("outPath") as String?).orEmpty())
}
