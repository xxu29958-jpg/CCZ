package com.ccz.core.battle

import com.ccz.core.event.SScript
import com.ccz.core.model.Pos

/**
 * Gameplay-layer entry point. Validates a command against the authoritative state
 * before letting [Resolver] mutate anything: illegal commands are rejected
 * deterministically and never reach the resolver, so they consume no RNG and
 * leave state untouched. This is the only path the app / battle loop should use
 * to submit commands; replay re-applies already-accepted commands through
 * [Resolver] directly. See ARCHITECTURE: Gameplay = command validation.
 */
object Gameplay {
    sealed interface Outcome {
        data class Accepted(val resolution: Resolution) : Outcome
        data class Rejected(val reason: RejectReason) : Outcome
    }

    fun submit(state: BattleState, command: Command, context: BattleContext): Outcome {
        val reason = CommandValidator.check(state, command, context)
        return if (reason != null) {
            Outcome.Rejected(reason)
        } else {
            Outcome.Accepted(Resolver.apply(state, command, context.resolve))
        }
    }

    /**
     * Read-only query: the tiles a unit may legally finish a [Command.Move] on, so the
     * presentation layer can highlight reachable tiles without owning the spatial rule —
     * the answer is computed here, in the authority, exactly as [CommandValidator] would
     * accept it. Pure: consumes no RNG and never mutates state. Returns an empty set when
     * the unit is unknown, dead, not on the active side, or has no known class — i.e. when
     * no [Command.Move] for it could be accepted right now. A living, active-side unit
     * always gets at least its own tile back (move-to-self is an accepted wait-in-place),
     * so a non-empty result also answers "is this unit selectable this turn?". [submit]
     * remains the sole authority that mutates state; this only previews what it would allow.
     */
    fun legalDestinations(state: BattleState, unitId: String, context: BattleContext): Set<Pos> {
        if (actorEligibility(state, unitId, state.active) != null) return emptySet()
        // Action economy: a unit that already moved or acted this turn has no legal destinations.
        if (state.hasMoved(unitId) || state.hasActed(unitId)) return emptySet()
        val unit = state.units.getValue(unitId)
        val unitClass = context.classes[unit.classId] ?: return emptySet()
        val occupancy = occupancyOf(state, exclude = unit.id)
        return MoveReachability.reachableStops(
            unit.pos, context.map, occupancy, Mover(unitClass.move, unit.faction, unitClass.terrain.moveCost),
        )
    }

    /**
     * Read-only query: the ids of units a unit may legally [Command.Attack] with [skillId], so the
     * presentation layer can highlight valid targets without owning the targeting rule — the answer
     * is computed here, in the authority, exactly as [CommandValidator] would accept it. Pure:
     * consumes no RNG and never mutates state. Returns an empty set when the attacker is unknown,
     * dead, not on the active side, or [skillId] is unknown — i.e. when no [Command.Attack] for it
     * could be accepted right now. Only living enemy-side units within the skill's range are
     * reported; the attacker itself and same-side units are never targets. [submit] remains the
     * sole authority that mutates state; this only previews which targets it would allow.
     */
    fun legalTargets(state: BattleState, attackerId: String, skillId: String, context: BattleContext): Set<String> {
        if (actorEligibility(state, attackerId, state.active) != null) return emptySet()
        // Action economy: an exhausted unit (already acted this turn) can attack no one.
        if (state.hasActed(attackerId)) return emptySet()
        val attacker = state.units.getValue(attackerId)
        val skill = context.skills[skillId] ?: return emptySet()
        if (!context.loadoutAllows(attackerId, skillId)) return emptySet()
        return state.units.values
            .filter { target ->
                target.alive &&
                    target.id != attacker.id &&
                    !sameSide(attacker.faction, target.faction) &&
                    skill.range.covers(manhattan(attacker.pos, target.pos))
            }
            .map { it.id }
            .toSet()
    }

    /**
     * Read-only query: the attack skills [attackerId] may choose from this turn, in a stable order,
     * so the presentation layer can offer a skill picker without owning the loadout rule. A unit
     * with a loadout configured gets that loadout, filtered to the skills the context actually
     * defines (a loadout entry naming an unknown skill is dropped, fail-closed); a unit with no
     * loadout entry — the unconstrained default — gets every id the skill table defines. Returns an
     * empty list when the attacker is unknown, dead, or not on the active side, i.e. when it cannot
     * attack at all right now. Listing a skill does not promise a target is in range for it; pair
     * with [legalTargets] per skill to know which actually have something to hit.
     */
    fun legalSkills(state: BattleState, attackerId: String, context: BattleContext): List<String> {
        if (actorEligibility(state, attackerId, state.active) != null) return emptyList()
        // Action economy: an exhausted unit has no attack skills available this turn.
        if (state.hasActed(attackerId)) return emptyList()
        val loadout = context.loadouts[attackerId] ?: context.skills.keys.toList()
        return loadout.filter { it in context.skills }
    }

    /**
     * Read-only query: the [BattleOutcome] the S-script's win/lose lists decide for the current state,
     * so the presentation layer can show a victory/defeat banner and stop accepting commands without
     * owning the win/lose rule — the verdict is computed here, in the authority, by [WinLose] (lose wins
     * ties). Pure: reads state only, consumes no RNG, never mutates. [submit] stays the sole authority
     * that mutates state; this lets the UI poll after each accepted command instead of threading
     * settlement into [submit] (which would ripple an SScript through every call site and the replay path).
     *
     * It returns the same verdict VALUE [WinLose.settle] would reach, but unlike `settle` it does NOT
     * persist the outcome onto state or emit [Event.BattleEnded] — so [WinLose]'s state-level "sticky once
     * decided" short-circuit is inert on this polling path: the verdict is re-derived from live unit
     * aliveness / turn each call. That is stable (monotonic) for the conditions used today, but a
     * non-monotonic condition (e.g. a script that revives a unit after a win) could flip the polled value
     * back; callers wanting a one-way latch must hold the decided verdict themselves — the `:app` reducer
     * does, by freezing input once decided, so the battle is terminal there regardless of re-derivation.
     */
    fun outcome(state: BattleState, script: SScript): BattleOutcome = WinLose.evaluate(state, script)
}
