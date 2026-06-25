package com.ccz.core.battle

/** Why a command is illegal. `null` from [CommandValidator.check] means legal. */
enum class RejectReason {
    NOT_ACTIVE_FACTION,
    UNIT_NOT_FOUND,
    UNIT_DEAD,
    UNKNOWN_CLASS,
    DESTINATION_OUT_OF_BOUNDS,
    DESTINATION_IMPASSABLE,
    DESTINATION_OCCUPIED,
    OUT_OF_MOVE_RANGE,
    UNKNOWN_SKILL,
    SKILL_NOT_IN_LOADOUT,
    TARGET_NOT_FOUND,
    TARGET_DEAD,
    SELF_TARGET,
    TARGET_FRIENDLY,
    OUT_OF_ATTACK_RANGE,
    WRONG_END_TURN_FACTION,
    UNIT_ALREADY_MOVED,
    UNIT_ALREADY_ACTED,
    // Cast (effect skill) rejections (ADR 0008): the skill carries no effects (not a cast skill),
    // the target does not match the effect's SELF/ALLY band, or it is out of the cast's range.
    SKILL_HAS_NO_EFFECT,
    CAST_TARGET_INVALID,
    OUT_OF_CAST_RANGE,
    // The caster is silenced (ADR 0008 ailment) — a command-legality gate that forbids casting while it lasts
    // (the unit may still move/attack/wait, so silence is checked only on the Cast path, not actorEligibility).
    CASTER_SILENCED,
}

/**
 * Pure, deterministic legality gate for the gameplay layer. It reads the
 * authoritative state plus the static [BattleContext] and returns the first
 * reason a command is illegal, or `null` when legal. It consumes no RNG and never
 * mutates state, so a rejected command cannot perturb replay determinism. See
 * CCZ_ENGINE_RULES: Gameplay 负责 command 合法性（移动范围 / 射程 / 存活 / 回合归属）.
 *
 * Turn ownership is checked by SIDE, not exact faction: PLAYER and ALLY act on the
 * same turn (see [sameSide]), so an ALLY unit may move/attack while [BattleState.active]
 * is PLAYER. [BattleState.active] only ever holds a side representative (PLAYER or
 * ENEMY; see Resolver.nextFaction), so [Command.EndTurn] is matched exactly against it.
 * A [Command.Move] whose destination equals the unit's current tile is an accepted
 * wait-in-place no-op (the origin is always a reachable, unoccupied stop).
 */
object CommandValidator {
    fun check(state: BattleState, command: Command, context: BattleContext): RejectReason? = when (command) {
        is Command.Move -> checkMove(state, command, context)
        is Command.Attack -> checkAttack(state, command, context)
        is Command.Cast -> checkCast(state, command, context)
        is Command.Wait -> checkWait(state, command)
        is Command.EndTurn ->
            if (command.faction == state.active) null else RejectReason.WRONG_END_TURN_FACTION
    }

    private fun checkMove(state: BattleState, command: Command.Move, context: BattleContext): RejectReason? {
        actorEligibility(state, command.unit, state.active)?.let { return it }
        // Action economy: a unit may move once, and not after it has acted (attacked/waited).
        if (state.hasActed(command.unit)) return RejectReason.UNIT_ALREADY_ACTED
        if (state.hasMoved(command.unit)) return RejectReason.UNIT_ALREADY_MOVED
        val unit = state.units.getValue(command.unit)
        val unitClass = context.classes[unit.classId] ?: return RejectReason.UNKNOWN_CLASS
        if (!context.map.inBounds(command.to)) return RejectReason.DESTINATION_OUT_OF_BOUNDS
        if (!context.map.tileAt(command.to).passable) return RejectReason.DESTINATION_IMPASSABLE
        val occupancy = occupancyOf(state, exclude = unit.id)
        if (command.to in occupancy) return RejectReason.DESTINATION_OCCUPIED
        val stops = MoveReachability.reachableStops(
            unit.pos, context.map, occupancy, Mover(unitClass.move, unit.faction, unitClass.terrain.moveCost),
        )
        return if (command.to in stops) null else RejectReason.OUT_OF_MOVE_RANGE
    }

    private fun checkAttack(state: BattleState, command: Command.Attack, context: BattleContext): RejectReason? {
        actorEligibility(state, command.attacker, state.active)?.let { return it }
        // Action economy: one action per unit per turn (moving first is allowed; acting twice is not).
        if (state.hasActed(command.attacker)) return RejectReason.UNIT_ALREADY_ACTED
        val attacker = state.units.getValue(command.attacker)
        val skill = context.skills[command.skill] ?: return RejectReason.UNKNOWN_SKILL
        if (!context.loadoutAllows(command.attacker, command.skill)) return RejectReason.SKILL_NOT_IN_LOADOUT
        if (command.attacker == command.target) return RejectReason.SELF_TARGET
        val target = state.units[command.target] ?: return RejectReason.TARGET_NOT_FOUND
        if (!target.alive) return RejectReason.TARGET_DEAD
        if (sameSide(attacker.faction, target.faction)) return RejectReason.TARGET_FRIENDLY
        val distance = manhattan(attacker.pos, target.pos)
        return if (skill.range.covers(distance)) null else RejectReason.OUT_OF_ATTACK_RANGE
    }

    /**
     * A cast applies an effect skill to a SELF/ALLY target (the inverse of attack's enemy targeting).
     * Legal for an eligible, un-acted caster using a known, loadout-allowed skill that HAS effects, on a
     * living target within range whose relation to the caster satisfies every effect's [EffectTarget]
     * band: SELF requires target == caster; ALLY requires a same-side target (which includes the caster).
     * RNG-free, mutates nothing — mirrors [checkAttack] but for the friendly-targeting path.
     */
    private fun checkCast(state: BattleState, command: Command.Cast, context: BattleContext): RejectReason? {
        actorEligibility(state, command.caster, state.active)?.let { return it }
        if (state.hasActed(command.caster)) return RejectReason.UNIT_ALREADY_ACTED
        val caster = state.units.getValue(command.caster)
        // Silence forbids casting (the legality-gate ailment). Single-sourced through Combatant.silenced so the
        // legalCastTargets preview (which returns empty when the caster is silenced) cannot disagree with this gate.
        if (caster.silenced) return RejectReason.CASTER_SILENCED
        val skill = context.skills[command.skill] ?: return RejectReason.UNKNOWN_SKILL
        if (!context.loadoutAllows(command.caster, command.skill)) return RejectReason.SKILL_NOT_IN_LOADOUT
        if (skill.effects.isEmpty()) return RejectReason.SKILL_HAS_NO_EFFECT
        val target = state.units[command.target] ?: return RejectReason.TARGET_NOT_FOUND
        if (!target.alive) return RejectReason.TARGET_DEAD
        // Every effect's SELF/ALLY band must accept this target (single-sourced in castTargetAllows so the
        // legalCastTargets preview can never disagree about who is a valid cast target).
        if (skill.effects.any { !castTargetAllows(it, caster, target) }) return RejectReason.CAST_TARGET_INVALID
        return if (skill.range.covers(manhattan(caster.pos, target.pos))) null else RejectReason.OUT_OF_CAST_RANGE
    }

    /** Wait stands the unit down for the turn; legal for an eligible unit that has not yet acted. */
    private fun checkWait(state: BattleState, command: Command.Wait): RejectReason? {
        actorEligibility(state, command.unit, state.active)?.let { return it }
        return if (state.hasActed(command.unit)) RejectReason.UNIT_ALREADY_ACTED else null
    }
}
