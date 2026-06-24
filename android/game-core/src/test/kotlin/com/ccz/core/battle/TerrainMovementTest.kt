package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-class terrain movement (Advance-Wars movement types): [MoveReachability] honors a class's
 * [com.ccz.core.model.UnitClass.terrainCost] over the tile's own cost. Replay/golden-safe — it only
 * shapes live reachability; the resolver re-applies recorded moves without recomputing cost.
 */
class TerrainMovementTest {
    // A single row: plain | plain | forest | plain — the lone path to x=3 runs through the forest at x=2.
    private fun row(): BattleMap = BattleMap(
        width = 4,
        height = 1,
        rows = listOf(
            listOf(MapTile("plain", 1), MapTile("plain", 1), MapTile("forest", 1), MapTile("plain", 1)),
        ),
    )

    private val origin = Pos(0, 0)
    private val noOccupancy = emptyMap<Pos, Faction>()

    @Test
    fun emptyTerrainCostFallsBackToTileMoveCost() {
        val reach = MoveReachability.reachableStops(origin, row(), noOccupancy, Mover(budget = 3, side = Faction.PLAYER))
        assertTrue(Pos(2, 0) in reach && Pos(3, 0) in reach, "terrain-agnostic movement reaches the whole row")
    }

    @Test
    fun perClassImpassableTerrainBlocksItAndCutsOffTilesBeyond() {
        val reach = MoveReachability.reachableStops(
            origin, row(), noOccupancy, Mover(budget = 3, side = Faction.PLAYER, terrainCost = mapOf("forest" to 0)),
        )
        assertTrue(Pos(1, 0) in reach)
        assertFalse(Pos(2, 0) in reach, "forest is impassable for this class")
        assertFalse(Pos(3, 0) in reach, "the tile beyond the forest is unreachable")
    }

    @Test
    fun perClassExpensiveTerrainShrinksRange() {
        val reach = MoveReachability.reachableStops(
            origin, row(), noOccupancy, Mover(budget = 3, side = Faction.PLAYER, terrainCost = mapOf("forest" to 3)),
        )
        assertTrue(Pos(1, 0) in reach)
        assertFalse(Pos(2, 0) in reach, "entering the forest costs 1+3 > budget 3")
    }
}
