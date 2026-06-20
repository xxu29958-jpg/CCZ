package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.math.abs

/** Manhattan (4-direction) tile distance, the metric used for range checks. */
internal fun manhattan(a: Pos, b: Pos): Int = abs(a.x - b.x) + abs(a.y - b.y)

/**
 * PLAYER and ALLY form one side; ENEMY is the other. Governs both targeting
 * (you cannot attack your own side) and turn ownership (your side acts together).
 */
internal fun sameSide(a: Faction, b: Faction): Boolean = (a == Faction.ENEMY) == (b == Faction.ENEMY)

/** Tiles held by living units other than [exclude], keyed to their faction. */
internal fun occupancyOf(state: BattleState, exclude: String? = null): Map<Pos, Faction> =
    state.units.values
        .filter { it.alive && it.id != exclude }
        .associate { it.pos to it.faction }
