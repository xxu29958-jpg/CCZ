package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyMapMapperTest {
    @Test
    fun mapsTerrainGridIntoTiles() {
        val json = """{"desc":"terrainMap_1","map_width":3,"map_height":2,"map_value":[[3,4,5],[0,14,0]]}"""

        val map = LegacyMapMapper.mapMap(json, id = "map_1")

        assertEquals("map_1", map.id)
        assertEquals(PackSize(3, 2), map.size)
        assertEquals(
            listOf(
                listOf("terrain_3", "terrain_4", "terrain_5"),
                listOf("terrain_0", "terrain_14", "terrain_0"),
            ),
            map.tiles,
        )
    }

    @Test
    fun emitsSnakeCaseMapsJson() {
        val map = LegacyMapMapper.mapMap("""{"map_width":1,"map_height":1,"map_value":[[3]]}""", id = "m")
        val out = LegacyMapMapper.toMapsJson(listOf(map))
        assertTrue(out.contains("\"tileset\"") && out.contains("\"tiles\""), out)
        assertTrue(out.contains("terrain_3"), out)
    }

    @Test
    fun rejectsRowWidthMismatchFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            LegacyMapMapper.mapMap("""{"map_width":3,"map_height":1,"map_value":[[3,4]]}""", id = "m")
        }
    }

    @Test
    fun rejectsHeightMismatchFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            LegacyMapMapper.mapMap("""{"map_width":1,"map_height":2,"map_value":[[3]]}""", id = "m")
        }
    }

    @Test
    fun toleratesBom() {
        val map = LegacyMapMapper.mapMap("﻿{\"map_width\":1,\"map_height\":1,\"map_value\":[[7]]}", id = "m")
        assertEquals(listOf(listOf("terrain_7")), map.tiles)
    }
}
