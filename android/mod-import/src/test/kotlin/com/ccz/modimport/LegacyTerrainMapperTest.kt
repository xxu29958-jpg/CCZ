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
    fun assignsDesignedDefenderBonusesByTerrainType() {
        // Engine-design cover by terrain type (not mined): open ground none, forest evasion, mountain
        // defense+evasion, village defense+heal.
        val json = """[{"mapid":1,"name":"平原"},{"mapid":3,"name":"树林"},{"mapid":5,"name":"山地"},{"mapid":22,"name":"村庄"}]"""
        val terrain = LegacyTerrainMapper.mapTerrain(json).associateBy { it.id }
        assertEquals(PackBonuses(), terrain.getValue("terrain_1").bonuses)
        assertEquals(PackBonuses(avoidBonus = 15), terrain.getValue("terrain_3").bonuses)
        assertEquals(PackBonuses(defBonus = 15, avoidBonus = 10), terrain.getValue("terrain_5").bonuses)
        assertEquals(PackBonuses(defBonus = 10, heal = 10), terrain.getValue("terrain_22").bonuses)
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
