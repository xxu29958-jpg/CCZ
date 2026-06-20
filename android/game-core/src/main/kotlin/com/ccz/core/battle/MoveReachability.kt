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
internal object MoveReachability {
    private val STEPS = listOf(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))

    fun reachableStops(
        origin: Pos,
        budget: Int,
        map: BattleMap,
        occupancy: Map<Pos, Faction>,
        moverSide: Faction,
    ): Set<Pos> =
        minCost(origin, budget, map, occupancy, moverSide).keys
            .filterTo(mutableSetOf()) { it !in occupancy }

    private fun minCost(
        origin: Pos,
        budget: Int,
        map: BattleMap,
        occupancy: Map<Pos, Faction>,
        moverSide: Faction,
    ): Map<Pos, Int> {
        val best = hashMapOf(origin to 0)
        val frontier = ArrayDeque<Pos>().apply { add(origin) }
        while (frontier.isNotEmpty()) {
            val current = frontier.removeFirst()
            val baseCost = best.getValue(current)
            for (step in STEPS) {
                val next = Pos(current.x + step.x, current.y + step.y)
                val stepCost = enterCost(next, map, occupancy, moverSide)
                if (stepCost == Int.MAX_VALUE) continue
                val cost = baseCost + stepCost
                if (cost <= budget && cost < (best[next] ?: Int.MAX_VALUE)) {
                    best[next] = cost
                    frontier.add(next)
                }
            }
        }
        return best
    }

    /** Cost to step onto [pos], or [Int.MAX_VALUE] when the tile cannot be transited. */
    private fun enterCost(pos: Pos, map: BattleMap, occupancy: Map<Pos, Faction>, moverSide: Faction): Int {
        if (!map.inBounds(pos)) return Int.MAX_VALUE
        val tile = map.tileAt(pos)
        if (!tile.passable) return Int.MAX_VALUE
        val occupant = occupancy[pos]
        if (occupant != null && !sameSide(occupant, moverSide)) return Int.MAX_VALUE
        return tile.moveCost
    }
}
