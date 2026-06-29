package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyMapMapperTest {
    @Test
    fun mapsTerrainGridIntoTiles() {
        val json = """{"desc":"terrainMap_1","map_width":3,"map_height":2,"map_value":[[3,null,5],[0,14,0]]}"""

        val map = LegacyMapMapper.mapMap(json, id = "map_1")

        assertEquals("map_1", map.id)
        assertEquals(PackSize(3, 2), map.size)
        assertEquals(
            listOf(
                listOf("terrain_3", "terrain_void", "terrain_5"),
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
    fun normalizesShortRowsAndCropsLongRows() {
        val map = LegacyMapMapper.mapMap("""{"map_width":3,"map_height":2,"map_value":[[3,4],[5,6,7,8]]}""", id = "m")

        assertEquals(
            listOf(listOf("terrain_3", "terrain_4", "terrain_void"), listOf("terrain_5", "terrain_6", "terrain_7")),
            map.tiles,
        )
    }

    @Test
    fun normalizesMissingRowsAndCropsExtraRows() {
        val map = LegacyMapMapper.mapMap("""{"map_width":2,"map_height":2,"map_value":[[3,4],[5,6],[7,8]]}""", id = "m")

        assertEquals(listOf(listOf("terrain_3", "terrain_4"), listOf("terrain_5", "terrain_6")), map.tiles)
        val padded = LegacyMapMapper.mapMap("""{"map_width":2,"map_height":2,"map_value":[[3,4]]}""", id = "p")
        assertEquals(listOf("terrain_void", "terrain_void"), padded.tiles[1])
    }

    @Test
    fun rejectsNegativeTerrainFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            LegacyMapMapper.mapMap("""{"map_width":1,"map_height":1,"map_value":[[-1]]}""", id = "m")
        }
    }

    @Test
    fun toleratesBom() {
        val map = LegacyMapMapper.mapMap("﻿{\"map_width\":1,\"map_height\":1,\"map_value\":[[7]]}", id = "m")
        assertEquals(listOf(listOf("terrain_7")), map.tiles)
    }
}
