package com.ccz.core.battle

import com.ccz.core.model.Combatant
import com.ccz.core.model.Pos

/**
 * Deterministic, aggressive enemy-turn planner. [nextCommand] yields the NEXT single [Command] for the
 * side whose turn it is, so a turn is driven one command at a time — each command resolves (consuming
 * RNG, possibly downing a unit) before the next is planned against the actual resulting state, which is
 * why the plan never tries to predict an attack's outcome. The plan is PURE and consumes no RNG: a given
 * state always yields the same command (the RNG advance happens in the [Resolver] when the command is
 * applied, exactly as for a player command), so a driven enemy turn is reproducible.
 *
 * It owns no battle authority — it only chooses among what the [Gameplay] read-only queries report legal
 * and is submitted through the same [Gameplay.submit] gate a player uses. It relies on the action economy
 * to terminate: every active-side unit acts at most once (Move then Attack/Wait), so the driving loop is
 * bounded; when all are exhausted it returns [Command.EndTurn].
 *
 * Policy (aggressive, v1): for the next un-exhausted active-side unit (in id order — a stable tie-break;
 * HP / unit-class priority like the source game is a later refinement), attack the nearest in-range foe
 * with the first skill that can reach one; else step toward the nearest foe (the legal destination that
 * minimizes Manhattan distance to it); if it cannot get closer, Wait.
 */
object EnemyAi {
    fun nextCommand(state: BattleState, context: BattleContext): Command {
        val actor = state.units.values
            .filter { it.alive && sameSide(it.faction, state.active) && !state.hasActed(it.id) }
            .minByOrNull { it.id }
            ?: return Command.EndTurn(state.active)

        attackCommand(state, context, actor)?.let { return it }

        if (!state.hasMoved(actor.id)) {
            stepTowardNearestFoe(state, context, actor)?.let { return Command.Move(actor.id, it) }
        }
        return Command.Wait(actor.id)
    }

    /** An attack on the nearest foe in range of the actor's first reaching skill, or null if none. */
    private fun attackCommand(state: BattleState, context: BattleContext, actor: Combatant): Command? {
        for (skill in Gameplay.legalSkills(state, actor.id, context)) {
            val target = nearestUnit(state, actor.pos, Gameplay.legalTargets(state, actor.id, skill, context))
            if (target != null) return Command.Attack(actor.id, target.id, skill)
        }
        return null
    }

    /** The reachable tile strictly closer to the nearest foe than the actor's current tile, or null. */
    private fun stepTowardNearestFoe(state: BattleState, context: BattleContext, actor: Combatant): Pos? {
        val foe = nearestUnit(state, actor.pos, foesOf(state, actor).map { it.id }.toSet()) ?: return null
        val destinations = Gameplay.legalDestinations(state, actor.id, context)
        if (destinations.isEmpty()) return null
        // Closest reachable tile to the foe; stable tie-break by (x, y) so the plan is deterministic.
        val best = destinations.minWith(compareBy({ manhattan(it, foe.pos) }, { it.x }, { it.y }))
        return if (manhattan(best, foe.pos) < manhattan(actor.pos, foe.pos)) best else null
    }

    private fun foesOf(state: BattleState, actor: Combatant): List<Combatant> =
        state.units.values.filter { it.alive && !sameSide(it.faction, actor.faction) }

    /** The unit among [ids] nearest [from] (Manhattan), tie-broken by id; null if [ids] is empty. */
    private fun nearestUnit(state: BattleState, from: Pos, ids: Set<String>): Combatant? =
        ids.mapNotNull { state.units[it] }
            .minWithOrNull(compareBy({ manhattan(it.pos, from) }, { it.id }))
}
