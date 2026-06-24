package com.ccz.contentpack.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The `classes[].movement.terrain_cost` wire field decodes into the domain class movement profile. */
class MovementTerrainCostLoadTest {
    @Test
    fun decodesPerClassTerrainCost() {
        val json = """
            {
              "manifest": {
                "native_format_version": "1", "content_id": "t", "content_version": "0",
                "source": {"mod": "t"}, "entry": "b"
              },
              "tables": {
                "classes": [
                  {"id": "cav", "name": "Cav",
                   "movement": {"move_type": "horse", "move": 5, "terrain_cost": {"forest": 3, "water": 0}}}
                ]
              }
            }
        """.trimIndent()

        val movement = ContentJsonLoader.load(json).tables.classes.single().movement

        assertEquals(3, movement.terrainCost["forest"])
        assertEquals(0, movement.terrainCost["water"], "0 marks impassable-for-this-class")
    }

    @Test
    fun terrainCostDefaultsEmptyWhenOmitted() {
        val json = """
            {
              "manifest": {
                "native_format_version": "1", "content_id": "t", "content_version": "0",
                "source": {"mod": "t"}, "entry": "b"
              },
              "tables": {"classes": [{"id": "ft", "name": "Foot", "movement": {"move_type": "foot", "move": 4}}]}
            }
        """.trimIndent()

        assertTrue(ContentJsonLoader.load(json).tables.classes.single().movement.terrainCost.isEmpty())
    }
}
