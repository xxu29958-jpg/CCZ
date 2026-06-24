package com.ccz.core.battle

import com.ccz.core.model.Pos

/**
 * Static spatial model of a battle: bounds plus per-tile movement cost and
 * passability. Built from native content (MapDef + terrain) at a higher layer;
 * game-core treats it as an immutable input, like [BattleRules] or the class
 * table. Occupancy is NOT stored here — it is derived from [BattleState.units]
 * so unit positions stay single-sourced in state.
 */
data class BattleMap(
    val width: Int,
    val height: Int,
    val rows: List<List<MapTile>>,
) {
    init {
        require(width > 0 && height > 0) { "map bounds must be positive: ${width}x$height" }
        require(rows.size == height) { "row count ${rows.size} must equal height $height" }
        require(rows.all { it.size == width }) { "every row must have $width tiles" }
    }

    fun inBounds(pos: Pos): Boolean = pos.x in 0 until width && pos.y in 0 until height

    fun tileAt(pos: Pos): MapTile = rows[pos.y][pos.x]

    companion object {
        /** A field of a single uniform terrain. Handy for flat maps and tests. */
        fun uniform(width: Int, height: Int, tile: MapTile): BattleMap =
            BattleMap(width, height, List(height) { List(width) { tile } })
    }
}

/**
 * One grid cell. [moveCost] is the cost to enter the tile (the origin tile of a
 * move is free); impassable tiles ([passable] = false) can never be entered.
 */
data class MapTile(
    val terrainId: String,
    val moveCost: Int,
    val passable: Boolean = true,
    // Flat defense a unit gains while standing here (FE/AW "terrain defense"): added to its effective
    // DEF in the damage formula, so defenders take less on forts/forests. 0 = open ground (no effect),
    // the default, so existing maps and saves that predate the field resolve combat unchanged.
    val defBonus: Int = 0,
    // Flat evasion a unit gains while standing here (FE/AW "terrain avoid"): subtracted from the
    // attacker's effective hit chance, so defenders are harder to hit on forests/etc. 0 = no effect,
    // the default; only the hit threshold shifts, never the RNG draw order, so goldens stay deterministic.
    val avoidBonus: Int = 0,
) {
    init {
        require(moveCost >= 1) { "moveCost must be >= 1, was $moveCost" }
        require(defBonus >= 0) { "defBonus must be >= 0, was $defBonus" }
        require(avoidBonus >= 0) { "avoidBonus must be >= 0, was $avoidBonus" }
    }
}
