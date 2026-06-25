package com.ccz.app.battle

import com.ccz.core.battle.BattleMap
import com.ccz.core.model.Combatant
import com.ccz.core.model.Pos

/**
 * Read-only presentation model of one map tile: the terrain's display name plus the movement and
 * combat-cover fields game-core already carries on [com.ccz.core.battle.MapTile] — the move cost
 * and the def/avoid/heal bonuses a defender standing here gains (wired into the damage/heal
 * resolution since the terrain-cover slice). The :app layer derives this purely from the
 * authoritative map; it computes no combat truth, it only names what the engine already reads.
 */
data class TerrainInfo(
    val name: String,
    val moveCost: Int,
    val passable: Boolean,
    val defBonus: Int,
    val avoidBonus: Int,
    val heal: Int,
)

/**
 * The tile at [pos] as a display model, resolving its terrain id to a name via [terrainName]
 * (falling back to the raw id when the pack registers no name). Null when [pos] is off the map —
 * a defensive guard; the board only ever taps in-bounds cells.
 */
fun terrainInfoAt(map: BattleMap, terrainName: (String) -> String, pos: Pos): TerrainInfo? {
    if (!map.inBounds(pos)) return null
    val tile = map.tileAt(pos)
    return TerrainInfo(
        name = terrainName(tile.terrainId),
        moveCost = tile.moveCost,
        passable = tile.passable,
        defBonus = tile.defBonus,
        avoidBonus = tile.avoidBonus,
        heal = tile.heal,
    )
}

/**
 * The tile's passability and combat cover as human-readable lines, listing only the modifiers that
 * actually apply (an impassable tile, then each non-zero bonus). Empty for open ground — the panel
 * then shows just the name and move cost. Pure text mirroring the def/avoid/heal the formula reads.
 */
fun terrainBonusLines(info: TerrainInfo): List<String> = buildList {
    if (!info.passable) add("不可通行")
    if (info.defBonus > 0) add("防御 +${info.defBonus}")
    if (info.avoidBonus > 0) add("回避 +${info.avoidBonus}")
    if (info.heal > 0) add("回血 +${info.heal}")
}

/**
 * One-line readout of a unit's CURRENT combat panel — HP plus the four stats, then any active ailment with
 * its remaining turns (e.g. "· 沉默 2") — so the player can see the live effect of a heal / buff / debuff /
 * ailment on the inspect panel (ADR 0008 effects otherwise only flash a one-shot badge). A pure read of
 * authoritative [Combatant] state; it computes nothing.
 */
fun combatantSummary(unit: Combatant): String {
    val panel = "HP ${unit.hp}/${unit.hpMax} · ATK ${unit.stats.atk} · DEF ${unit.stats.def} · MAT ${unit.stats.mat} · RES ${unit.stats.res}"
    val ailments = unit.ailments.joinToString("") { " · ${ailmentLabel(it.kind)} ${it.remaining}" }
    return panel + ailments
}
