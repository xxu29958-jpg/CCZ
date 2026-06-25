package com.ccz.core.battle

import com.ccz.core.model.Combatant
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Pos
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect

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
 * Policy (aggressive + support, v1): for the next un-exhausted active-side unit (in id order — a stable
 * tie-break; own-unit action ORDER like the source game's low-HP-first / cavalry-first is a later refinement
 * that needs a class speed model): SUPPORT-FIRST — if it has a heal (effect) skill and a same-side ally is
 * meaningfully wounded (below half max HP) within cast range, heal the most wounded one (securing survival);
 * else DISABLE — if it has an enemy-targeting stat-debuff (cast) skill and a not-yet-debuffed foe in range,
 * soften the most threatening one (highest ATK); else focus-fire the most wounded in-range foe (lowest HP,
 * tie-broken nearest then id) with the first DAMAGE skill that can reach it (a cast/effect skill is never used
 * as an attack); else reposition — prefer a reachable tile it could ATTACK a foe from, nearest by Manhattan
 * (so a ranged unit takes its range band, even backing away from a too-close foe, and a melee unit closes to
 * striking distance), falling back to simply closing on the nearest foe; if it can do neither, Wait.
 */
object EnemyAi {
    fun nextCommand(state: BattleState, context: BattleContext): Command {
        val actor = state.units.values
            .filter { it.alive && sameSide(it.faction, state.active) && !state.hasActed(it.id) }
            .minByOrNull { it.id }
            ?: return Command.EndTurn(state.active)

        healCommand(state, context, actor)?.let { return it }
        debuffCommand(state, context, actor)?.let { return it }
        attackCommand(state, context, actor)?.let { return it }

        if (!state.hasMoved(actor.id)) {
            stepTowardNearestFoe(state, context, actor)?.let { return Command.Move(actor.id, it) }
        }
        return Command.Wait(actor.id)
    }

    /**
     * A heal cast on the most wounded same-side ally a cast (effect) skill can reach, or null if the actor
     * has no heal skill or no ally is meaningfully wounded (below half max HP) in range. Support-first: a
     * healer secures an ally's survival before attacking. Deterministic, RNG-free — it chooses only among
     * what [Gameplay.legalCastTargets] reports and the cast itself draws no RNG. "Meaningfully wounded"
     * (hp*2 < hpMax) keeps the AI from wasting a turn topping off a barely-scratched ally.
     */
    private fun healCommand(state: BattleState, context: BattleContext, actor: Combatant): Command? {
        for (skill in Gameplay.legalSkills(state, actor.id, context)) {
            // Only skills that actually HEAL trigger support-healing — a non-heal effect skill (e.g. a
            // StatDelta buff, ADR 0008 Phase 2) is not cast here (the AI does not auto-buff yet).
            if (context.skills[skill]?.effects?.any { it is SkillEffect.Heal } != true) continue
            val target = Gameplay.legalCastTargets(state, actor.id, skill, context)
                .mapNotNull { state.units[it] }
                .filter { it.hp * 2 < it.hpMax }
                .minWithOrNull(compareBy({ it.hp }, { it.id }))
            if (target != null) return Command.Cast(actor.id, target.id, skill)
        }
        return null
    }

    /**
     * A stat-debuff cast on the most threatening (highest-ATK) in-range foe a NEGATIVE, TIMED enemy-targeting
     * [SkillEffect.StatDelta] (cast) skill can reach, or null if the actor has no such skill or every reachable foe
     * already carries a mod on the debuffed stat. Slotted AFTER support-heal but BEFORE attack so a disabler softens
     * the enemy's strongest unit before trading blows; the "not already affected on that stat" filter keeps it from
     * re-casting the same debuff (so it falls through to attacking once its targets are debuffed — never pacifist).
     * Deterministic and RNG-free: it chooses only among what [Gameplay.legalCastTargets] reports and the cast draws
     * no RNG. Only a DEBUFF (enemy band) triggers this — a heal/buff (friendly) effect is not auto-cast here. The
     * `amount < 0 && duration > 0` filter is defense-in-depth: a positive amount would BUFF the foe, and a
     * duration-0 (instant, unrecorded) delta would defeat the dedup filter and re-cast forever (a stall loop).
     */
    private fun debuffCommand(state: BattleState, context: BattleContext, actor: Combatant): Command? {
        for (skill in Gameplay.legalSkills(state, actor.id, context)) {
            val debuff = context.skills[skill]?.effects
                ?.filterIsInstance<SkillEffect.StatDelta>()
                ?.firstOrNull { it.target == EffectTarget.ENEMY && it.amount < 0 && it.duration > 0 }
                ?: continue
            val target = Gameplay.legalCastTargets(state, actor.id, skill, context)
                .mapNotNull { state.units[it] }
                .filter { foe -> foe.effects.none { it.stat == debuff.stat } }
                .minWithOrNull(compareByDescending<Combatant> { it.stats.atk }.thenBy { it.id })
            if (target != null) return Command.Cast(actor.id, target.id, skill)
        }
        return null
    }

    /**
     * The actor's best attack this turn, or null if no foe is in range. Among every foe reachable by a
     * legal skill, focus-fire the most wounded (lowest current HP) — tie-broken by nearest then id, and
     * struck with the first legal skill that reaches it. Lowest-HP is a cheap proxy for finishing a weak
     * foe; it is NOT a predicted kill — the plan reads HP and positions but never computes the attack's
     * damage (the [Resolver] owns that). A target reachable by several skills ties on every key, so the
     * stable min keeps the first skill in [Gameplay.legalSkills] order.
     */
    private fun attackCommand(state: BattleState, context: BattleContext, actor: Combatant): Command? {
        // Only DAMAGE skills attack — a cast/effect skill (e.g. a heal) is never used as a chip attack.
        val reachable = Gameplay.legalSkills(state, actor.id, context)
            .filter { context.skills[it]?.effects.isNullOrEmpty() }
            .flatMap { skill -> Gameplay.legalTargets(state, actor.id, skill, context).map { id -> id to skill } }
        val best = reachable
            .mapNotNull { (id, skill) -> state.units[id]?.let { foe -> Triple(foe, id, skill) } }
            .minWithOrNull(compareBy({ it.first.hp }, { manhattan(it.first.pos, actor.pos) }, { it.second }))
            ?: return null
        return Command.Attack(actor.id, best.second, best.third)
    }

    /** The tile to reposition to: a firing position if any is reachable, else strictly closer to the
     *  nearest foe; null if neither helps (the caller then Waits). All tie-breaks are deterministic. */
    private fun stepTowardNearestFoe(state: BattleState, context: BattleContext, actor: Combatant): Pos? {
        val foes = foesOf(state, actor)
        val destinations = Gameplay.legalDestinations(state, actor.id, context)
        if (foes.isEmpty() || destinations.isEmpty()) return null
        val skills = Gameplay.legalSkills(state, actor.id, context).mapNotNull { context.skills[it] }.filter { it.effects.isEmpty() }
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
