package com.ccz.modimport

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyResourceImporterTest {
    private val key = "Dgm5Y54yp9p5nMzU".toByteArray(Charsets.US_ASCII)

    @Test
    fun decryptsBgt1TablesFromOwnedApkWithKey() {
        val heroJson = "﻿[{\"hid\":1,\"name\":\"刘备\"}]".toByteArray(Charsets.UTF_8)
        val terrainJson = "﻿[[0,3]]".toByteArray(Charsets.UTF_8)
        val apk = legacyApk(
            "assets/GameResources/trssgshz/json/dic_hero.json" to bgt1(heroJson, klen = 19),
            "assets/GameResources/trssgshz/terrainJson/terrainMap_1.json" to bgt1(terrainJson, klen = 23),
            "assets/GameResources/trssgshz/img/Items/1.png" to "png".toByteArray(),
        )

        val resources = LegacyResourceImporter.importResources(apk, key)

        assertEquals(2, resources.size, "only BGT1 tables are imported, png is skipped")
        val byKind = resources.associateBy { it.kind }
        assertEquals("[{\"hid\":1,\"name\":\"刘备\"}]", byKind.getValue(LegacyResourceKind.TABLE_JSON).text())
        assertEquals("[[0,3]]", byKind.getValue(LegacyResourceKind.TERRAIN_JSON).text())
    }

    @Test
    fun filtersByResourceKind() {
        val apk = legacyApk(
            "assets/GameResources/trssgshz/json/dic_hero.json" to bgt1("﻿[1]".toByteArray(), klen = 16),
            "assets/GameResources/trssgshz/terrainJson/terrainMap_1.json" to bgt1("﻿[2]".toByteArray(), klen = 16),
        )

        val tables = LegacyResourceImporter.importResources(apk, key, kinds = setOf(LegacyResourceKind.TABLE_JSON))

        assertEquals(1, tables.size)
        assertEquals(LegacyResourceKind.TABLE_JSON, tables.single().kind)
    }

    @Test
    fun wrongKeySurfacesAsImportError() {
        val apk = legacyApk(
            "assets/GameResources/trssgshz/json/dic_hero.json" to bgt1("﻿[1]".toByteArray(), klen = 19),
        )
        assertFailsWith<LegacyApkInspectionException> {
            LegacyResourceImporter.importResources(apk, "wrongkeywrongkey".toByteArray())
        }
    }

    @Test
    fun emptyKeyIsRejected() {
        val apk = legacyApk("assets/GameResources/trssgshz/json/dic_hero.json" to bgt1("﻿[1]".toByteArray(), 16))
        assertFailsWith<IllegalArgumentException> { LegacyResourceImporter.importResources(apk, ByteArray(0)) }
    }

    @Test
    fun singleResourceImportRoundTrips() {
        val json = "﻿[{\"jobid\":1,\"name\":\"群雄\"}]".toByteArray(Charsets.UTF_8)
        val apk = legacyApk("assets/GameResources/trssgshz/json/dic_job.json" to bgt1(json, klen = 21))
        val res = LegacyResourceImporter.importResource(apk, "assets/GameResources/trssgshz/json/dic_job.json", key)
        assertTrue(res.text().contains("群雄"))
    }

    private fun bgt1(plain: ByteArray, klen: Int): ByteArray =
        Bgt1Codec.encrypt(plain, key, ByteArray(klen) { (it * 17 + 3).toByte() })

    private fun functionJson(): String =
        """
        {
          "GameName": "同人圣三国蜀汉传",
          "GamePath": "trssgshz",
          "EncryptResource": true
        }
        """.trimIndent()

    private fun legacyApk(vararg entries: Pair<String, ByteArray>): Path {
        val target = createTempFile(prefix = "legacy-apk-", suffix = ".apk")
        ZipOutputStream(target.toFile().outputStream()).use { zip ->
            zip.writeEntry("assets/GameResources/function.txt", functionJson().toByteArray(Charsets.UTF_8))
            entries.forEach { (name, content) -> zip.writeEntry(name, content) }
        }
        return target
    }

    private fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }
}
