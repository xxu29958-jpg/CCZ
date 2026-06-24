package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyTerrainMapperTest {
    @Test
    fun mapsMapTerrainRowsToCatalogEntries() {
        val json = """[{"mapid":1,"name":"平原","effect":"0&3","type":971},{"mapid":3,"name":"树林","effect":"0&"}]"""
        val terrain = LegacyTerrainMapper.mapTerrain(json)
        assertEquals(2, terrain.size)
        assertEquals(PackTerrain("terrain_1", "平原", LegacyTerrainMapper.BASE_MOVE_COST), terrain[0])
        assertEquals("terrain_3", terrain[1].id)
    }

    @Test
    fun emitsSnakeCaseTerrainJson() {
        val out = LegacyTerrainMapper.toTerrainJson(listOf(PackTerrain("terrain_1", "平原", 1)))
        assertTrue(out.contains("\"move_cost\""), out)
        assertTrue(out.contains("平原"), out)
    }

    @Test
    fun rejectsDuplicateMapidFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            LegacyTerrainMapper.mapTerrain("""[{"mapid":1,"name":"a"},{"mapid":1,"name":"b"}]""")
        }
    }

    @Test
    fun toleratesBom() {
        assertEquals("terrain_5", LegacyTerrainMapper.mapTerrain("﻿[{\"mapid\":5,\"name\":\"x\"}]").single().id)
    }
}
