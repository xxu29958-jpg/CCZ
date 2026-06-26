package com.ccz.app.battle

import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleState
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
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
 * One-line readout of a unit's CURRENT combat panel — HP plus the four stats, then any active TIMED stat
 * effect (the signed delta + remaining turns, e.g. "· ATK +15 (2)") and any active ailment with its remaining
 * turns (e.g. "· 沉默 2") — so the player can see the live effect of a heal / buff / debuff / ailment on the
 * inspect panel (ADR 0008 effects otherwise only flash a one-shot badge). Without the timed-effect clause the
 * stat number alone gives no signal a buff/debuff is temporary or for how long. A pure read of authoritative
 * [Combatant] state; it computes nothing.
 */
fun combatantSummary(unit: Combatant): String {
    val panel = "HP ${unit.hp}/${unit.hpMax} · ATK ${unit.stats.atk} · DEF ${unit.stats.def} · MAT ${unit.stats.mat} · RES ${unit.stats.res}"
    val effects = unit.effects.joinToString("") {
        " · ${it.stat.name} ${if (it.amount >= 0) "+${it.amount}" else "${it.amount}"} (${it.remaining})"
    }
    val ailments = unit.ailments.joinToString("") { " · ${ailmentLabel(it.kind)} ${it.remaining}" }
    return panel + effects + ailments
}

/**
 * A glanceable, condensed status string for a unit's board marker (ADR 0008): each active ailment as its
 * label's first glyph (沉默 → 沉, 麻痹 → 麻) and each timed stat effect as an arrow (↑ buff / ↓ debuff), so a
 * player scanning the board sees WHO is afflicted/buffed without tapping each tile (the inspect panel
 * [combatantSummary] gives the full detail). Empty for a clean unit. Pure read; kept tiny so it fits the cell.
 */
fun statusChips(unit: Combatant): String {
    val ailments = unit.ailments.joinToString("") { ailmentLabel(it.kind).take(1) }
    val effects = unit.effects.joinToString("") { if (it.amount >= 0) "↑" else "↓" }
    return ailments + effects
}

/** True if [unit] carries any ailment or stat-debuff — used to tint its [statusChips] as a hostile state. */
fun hasHostileStatus(unit: Combatant): Boolean = unit.ailments.isNotEmpty() || unit.effects.any { it.amount < 0 }

/** PLAYER and ALLY act on the player's turn (mirrors game-core's sameSide for the player band). */
private fun isPlayerSide(faction: Faction): Boolean = faction == Faction.PLAYER || faction == Faction.ALLY

/**
 * True when [unit] is one of the PLAYER's own units that has already taken its action this turn — under the
 * Fire-Emblem action economy it can neither move nor act again ([com.ccz.core.battle.CommandValidator] rejects
 * any further command), so the board dims it and the player sees at a glance who is still available. Enemy units
 * are never marked spent (their economy is the AI's, not the player's to track). Pure read of authoritative
 * [state]; [com.ccz.core.battle.BattleState.hasActed] is cleared on EndTurn, so this resets each turn.
 */
fun isSpent(unit: Combatant, state: BattleState): Boolean = isPlayerSide(unit.faction) && state.hasActed(unit.id)

/** Living unit counts per side for the HUD: player (PLAYER + ALLY) vs enemy. */
data class ForceTally(val player: Int, val enemy: Int)

/**
 * The living-unit count on each side, so the HUD shows annihilation-objective progress at a glance — a defeated
 * unit stops rendering, so the player cannot count tiles to see who is left. Counts who is on the board NOW
 * (reinforcements raise the enemy count as they spawn), which is the correct live reading. Pure read of [state].
 */
fun forceTally(state: BattleState): ForceTally {
    val living = state.units.values.filter { it.alive }
    return ForceTally(
        player = living.count { isPlayerSide(it.faction) },
        enemy = living.count { it.faction == Faction.ENEMY },
    )
}
