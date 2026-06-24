package com.ccz.core.battle

import com.ccz.core.model.Combatant
import com.ccz.core.model.Pos
import com.ccz.core.model.Skill

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
 * with the first skill that can reach one; else reposition — prefer a reachable tile it could ATTACK a
 * foe from, nearest by Manhattan (so a ranged unit takes its range band, even backing away from a
 * too-close foe, and a melee unit closes to striking distance), falling back to simply closing on the
 * nearest foe; if it can do neither, Wait.
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

    /** The tile to reposition to: a firing position if any is reachable, else strictly closer to the
     *  nearest foe; null if neither helps (the caller then Waits). All tie-breaks are deterministic. */
    private fun stepTowardNearestFoe(state: BattleState, context: BattleContext, actor: Combatant): Pos? {
        val foes = foesOf(state, actor)
        val destinations = Gameplay.legalDestinations(state, actor.id, context)
        if (foes.isEmpty() || destinations.isEmpty()) return null
        val skills = Gameplay.legalSkills(state, actor.id, context).mapNotNull { context.skills[it] }
        // Prefer a reachable tile the unit could attack a foe from, nearest by Manhattan (a cheap proxy
        // for least movement — exact on uniform terrain) — so ranged units take their range band (even
        // backing up) and melee units close to striking distance. The current tile is never one (the
        // caller already found no in-range target there), so this only ever yields a real move.
        val firing = destinations.filter { canAttackFrom(it, skills, foes) }
        // Among firing tiles, prefer the most favorable combat ground (highest terrain affinity), then
        // nearest by Manhattan, then a stable x/y tie-break. With no affinities every tile ties at 100,
        // so this reduces to the prior nearest-tile choice (terrain only refines, never destabilizes).
        if (firing.isNotEmpty()) {
            return firing.minWith(
                compareByDescending<Pos> { terrainAffinityAt(context, actor, it) }
                    .thenBy { manhattan(it, actor.pos) }
                    .thenBy { it.x }
                    .thenBy { it.y },
            )
        }
        // No firing position reachable: close on the nearest foe (only if a tile is strictly closer).
        val foe = nearestUnit(state, actor.pos, foes.map { it.id }.toSet()) ?: return null
        val best = destinations.minWith(compareBy({ manhattan(it, foe.pos) }, { it.x }, { it.y }))
        return if (manhattan(best, foe.pos) < manhattan(actor.pos, foe.pos)) best else null
    }

    /** True if from [tile] some skill's range covers some foe — i.e. the unit could attack after moving there. */
    private fun canAttackFrom(tile: Pos, skills: List<Skill>, foes: List<Combatant>): Boolean =
        skills.any { skill -> foes.any { foe -> skill.range.covers(manhattan(tile, foe.pos)) } }

    /** The actor class's combat affinity (percent) for the terrain on [tile]; 100 when neutral/unknown. */
    private fun terrainAffinityAt(context: BattleContext, actor: Combatant, tile: Pos): Int =
        context.classes[actor.classId]?.terrain?.affinity?.get(context.map.tileAt(tile).terrainId) ?: 100

    private fun foesOf(state: BattleState, actor: Combatant): List<Combatant> =
        state.units.values.filter { it.alive && !sameSide(it.faction, actor.faction) }

    /** The unit among [ids] nearest [from] (Manhattan), tie-broken by id; null if [ids] is empty. */
    private fun nearestUnit(state: BattleState, from: Pos, ids: Set<String>): Combatant? =
        ids.mapNotNull { state.units[it] }
            .minWithOrNull(compareBy({ manhattan(it.pos, from) }, { it.id }))
}
