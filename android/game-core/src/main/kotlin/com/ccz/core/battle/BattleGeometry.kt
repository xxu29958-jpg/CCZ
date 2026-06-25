package com.ccz.core.battle

import com.ccz.core.model.Combatant
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.SkillEffect
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

/**
 * The actor-eligibility preconditions shared by every command and every read-only preview query:
 * the unit must exist, be alive, and be on the side whose turn it is ([active]). Returns the first
 * failing [RejectReason], or null when the unit may act. Single-sourced here so [CommandValidator]
 * (which surfaces the reason) and the [Gameplay] preview queries (which treat any non-null as
 * "cannot act → empty result") agree BY CONSTRUCTION: a new actor precondition added here can never
 * be enforced by one path and missed by the other, which is what keeps query⟺submit parity. Only
 * the actor gate lives here; command-specific checks (class/skill lookup, destination/target,
 * range) stay with their command.
 */
internal fun actorEligibility(state: BattleState, unitId: String, active: Faction): RejectReason? {
    val unit = state.units[unitId] ?: return RejectReason.UNIT_NOT_FOUND
    if (!unit.alive) return RejectReason.UNIT_DEAD
    if (!sameSide(unit.faction, active)) return RejectReason.NOT_ACTIVE_FACTION
    return null
}

/**
 * Whether [target] satisfies a cast effect's [EffectTarget] band relative to [caster] (ADR 0008): SELF
 * requires the target to BE the caster; ALLY requires a same-side target (which includes the caster).
 * Single-sourced here so [CommandValidator.check]'s cast gate (which rejects with CAST_TARGET_INVALID)
 * and the [Gameplay.legalCastTargets] preview (which filters to the allowed set) agree BY CONSTRUCTION —
 * the same query⟺submit parity guarantee [actorEligibility] gives the actor gate. Exhaustive `when` over
 * the sealed effect type: a new effect variant must declare its band here or fail to compile.
 */
internal fun castTargetAllows(effect: SkillEffect, caster: Combatant, target: Combatant): Boolean = when (effect) {
    is SkillEffect.Heal -> when (effect.target) {
        EffectTarget.SELF -> caster.id == target.id
        EffectTarget.ALLY -> sameSide(caster.faction, target.faction)
    }
}
