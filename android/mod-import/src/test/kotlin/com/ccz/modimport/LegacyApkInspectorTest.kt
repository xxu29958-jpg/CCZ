package com.ccz.modimport

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyApkInspectorTest {
    @Test
    fun inspectsPlainLegacyApkMetadataWithoutExtractingResources() {
        val apk = legacyApk(
            functionJson(encryptResource = false),
            "assets/GameResources/trssgshz/json/dic_hero.json" to "{}",
            "assets/GameResources/trssgshz/terrainJson/terrainMap_1.json" to "[]",
            "assets/GameResources/trssgshz/img/Items/1.png" to "png",
        )

        val report = LegacyApkInspector.inspect(apk)

        assertEquals("同人圣三国蜀汉传", report.gameName)
        assertEquals("trssgshz", report.gamePath)
        assertFalse(report.encryptResource)
        assertEquals(3, report.counts.gameResourceEntries)
        assertEquals(1, report.counts.directoryCounts.getValue("json"))
        assertEquals(1, report.counts.directoryCounts.getValue("terrainJson"))
        assertEquals(2, report.counts.extensionCounts.getValue(".json"))
        assertEquals(emptyList(), report.encryptedResources)
        report.requirePlainContent()
    }

    @Test
    fun declaredEncryptedResourcesRejectContentImportEvenWithoutSamples() {
        val apk = legacyApk(
            functionJson(encryptResource = true),
            "assets/GameResources/trssgshz/img/Items/1.png" to "png",
        )

        val report = LegacyApkInspector.inspect(apk)

        assertTrue(report.encryptResource)
        assertTrue(report.hasEncryptedResources)
        assertFailsWith<LegacyImportRejected> { report.requirePlainContent() }
    }

    @Test
    fun bgt1ResourcesAreSampledAndClassifiedFailClosed() {
        val apk = legacyApk(
            functionJson(encryptResource = false),
            "assets/GameResources/trssgshz/json/dic_hero.json" to bgt1Payload(),
            "assets/GameResources/trssgshz/terrainJson/terrainMap_1.json" to bgt1Payload(),
            "assets/GameResources/trssgshz/Scenes/S_00.eex_new" to bgt1Payload(),
            "assets/GameResources/trssgshz/Hexzmap.e5" to bgt1Payload(),
        )

        val report = LegacyApkInspector.inspect(apk)

        assertFalse(report.encryptResource)
        assertEquals(
            listOf(
                LegacyResourceKind.MAP_BUNDLE,
                LegacyResourceKind.SCENE_SCRIPT,
                LegacyResourceKind.TABLE_JSON,
                LegacyResourceKind.TERRAIN_JSON,
            ),
            report.encryptedResources.map { it.kind }.sortedBy { it.name },
        )
        assertFailsWith<LegacyImportRejected> { report.requirePlainContent() }
    }

    @Test
    fun missingFunctionConfigFailsClosed() {
        val apk = legacyApk(
            function = null,
            "assets/GameResources/trssgshz/json/dic_hero.json" to "{}",
        )

        assertFailsWith<LegacyApkInspectionException> {
            LegacyApkInspector.inspect(apk)
        }
    }

    private fun functionJson(encryptResource: Boolean): String =
        """
        {
          "GameName": "同人圣三国蜀汉传",
          "GamePath": "trssgshz",
          "EncryptResource": $encryptResource
        }
        """.trimIndent()

    private fun bgt1Payload(): String = "BGT1\u0001\u0000opaque"

    private fun legacyApk(
        function: String?,
        vararg entries: Pair<String, String>,
    ): Path {
        val target = createTempFile(prefix = "legacy-apk-", suffix = ".apk")
        ZipOutputStream(target.toFile().outputStream()).use { zip ->
            if (function != null) zip.writeEntry("assets/GameResources/function.txt", function)
            entries.forEach { (name, content) -> zip.writeEntry(name, content) }
        }
        return target
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
