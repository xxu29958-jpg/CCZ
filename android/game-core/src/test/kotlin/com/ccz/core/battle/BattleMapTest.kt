package com.ccz.core.battle

import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BattleMapTest {
    @Test
    fun uniformFillsEveryTile() {
        val map = BattleMap.uniform(3, 2, MapTile("plain", 1))
        assertEquals(3, map.width)
        assertEquals(2, map.height)
        assertTrue(map.rows.all { row -> row.all { it == MapTile("plain", 1) } })
    }

    @Test
    fun tileAtUsesRowMajorXyIndexing() {
        val rows = List(3) { y -> List(2) { x -> MapTile("$x-$y", 1) } }
        val map = BattleMap(2, 3, rows)
        assertEquals("1-2", map.tileAt(Pos(1, 2)).terrainId)
        assertTrue(map.inBounds(Pos(1, 2)))
        assertTrue(!map.inBounds(Pos(2, 0)))
    }

    @Test
    fun constructorRejectsRowCountMismatch() {
        assertFailsWith<IllegalArgumentException> {
            BattleMap(2, 3, listOf(listOf(MapTile("plain", 1), MapTile("plain", 1))))
        }
    }

    @Test
    fun mapTileRejectsNonPositiveMoveCost() {
        assertFailsWith<IllegalArgumentException> { MapTile("plain", 0) }
    }
}
