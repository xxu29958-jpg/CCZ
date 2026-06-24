package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

/**
 * Computes which tiles a unit can legally finish a move on, honoring terrain
 * move cost, impassable tiles, and occupancy. Movement is 4-directional (no
 * diagonals), matching the Cao Cao Zhuan grid. Entering a tile costs that tile's
 * [MapTile.moveCost]; the origin is free. Friendly units may be passed through
 * but not stopped on; enemy units block transit entirely.
 */
/**
 * The moving unit's spatial budget: how far it may travel ([budget]), whose side it is on (for
 * pass-through rules), and its per-class [terrainCost] overrides. Grouped so [MoveReachability]
 * stays within the parameter-count budget. Empty [terrainCost] = terrain-agnostic (tile defaults).
 */
internal data class Mover(
    val budget: Int,
    val side: Faction,
    val terrainCost: Map<String, Int> = emptyMap(),
)

internal object MoveReachability {
    private val STEPS = listOf(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))

    fun reachableStops(origin: Pos, map: BattleMap, occupancy: Map<Pos, Faction>, mover: Mover): Set<Pos> =
        minCost(origin, map, occupancy, mover).keys
            .filterTo(mutableSetOf()) { it !in occupancy }

    private fun minCost(origin: Pos, map: BattleMap, occupancy: Map<Pos, Faction>, mover: Mover): Map<Pos, Int> {
        val best = hashMapOf(origin to 0)
        val frontier = ArrayDeque<Pos>().apply { add(origin) }
        while (frontier.isNotEmpty()) {
            val current = frontier.removeFirst()
            val baseCost = best.getValue(current)
            for (step in STEPS) {
                val next = Pos(current.x + step.x, current.y + step.y)
                val stepCost = enterCost(next, map, occupancy, mover)
                if (stepCost == Int.MAX_VALUE) continue
                val cost = baseCost + stepCost
                if (cost <= mover.budget && cost < (best[next] ?: Int.MAX_VALUE)) {
                    best[next] = cost
                    frontier.add(next)
                }
            }
        }
        return best
    }

    /** Cost to step onto [pos], or [Int.MAX_VALUE] when the tile cannot be transited. */
    private fun enterCost(pos: Pos, map: BattleMap, occupancy: Map<Pos, Faction>, mover: Mover): Int {
        if (!map.inBounds(pos)) return Int.MAX_VALUE
        val tile = map.tileAt(pos)
        // A globally-impassable tile (wall/void) is a hard floor for every class — overrides never
        // punch through it; terrainCost only modulates otherwise-passable terrain.
        if (!tile.passable) return Int.MAX_VALUE
        val occupant = occupancy[pos]
        if (occupant != null && !sameSide(occupant, mover.side)) return Int.MAX_VALUE
        // Per-class override (Advance-Wars movement types): a declared cost wins over the tile's own —
        // `<= 0` means this class cannot enter the terrain. Absent → the tile's global cost.
        val override = mover.terrainCost[tile.terrainId]
        return when {
            override == null -> tile.moveCost
            override <= 0 -> Int.MAX_VALUE
            else -> override
        }
    }
}
