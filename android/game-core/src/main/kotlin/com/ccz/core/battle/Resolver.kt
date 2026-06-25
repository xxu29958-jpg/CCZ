package com.ccz.core.battle

import com.ccz.core.model.ActiveAilment
import com.ccz.core.model.ActiveEffect
import com.ccz.core.model.AffectedStat
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Combatant
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.HealMode
import com.ccz.core.model.Skill
import com.ccz.core.model.SkillEffect
import com.ccz.core.model.UnitClass
import com.ccz.core.rng.Rng

/**
 * Inputs the [Resolver] resolves a command against: the class/skill tables, rule constants, and the
 * optional map (needed for position-dependent effects like terrain affinity; null = terrain-agnostic).
 * Grouped into one value object so the resolver/replay entry points stay within the parameter gate.
 */
data class ResolveContext(
    val classes: Map<String, UnitClass>,
    val skills: Map<String, Skill> = Resolver.DEMO_SKILLS,
    val rules: BattleRules = BattleRules.DEFAULT,
    val map: BattleMap? = null,
)

object Resolver {
    fun apply(state: BattleState, command: Command, ctx: ResolveContext): Resolution = when (command) {
        is Command.Move -> move(state, command)
        is Command.Attack -> attack(state, command, ctx)
        is Command.Cast -> cast(state, command, ctx)
        is Command.Wait -> Resolution(state.markActed(command.unit), listOf(Event.Waited(command.unit)))
        is Command.EndTurn -> endTurn(state, command, ctx)
    }

    /**
     * Resolves a [Command.Cast]: folds the skill's effects onto the target, deterministically and RNG-FREE —
     * [BattleState.rngState] is returned unchanged (like Move/Wait), so a cast perturbs no draw order and the
     * damage golden is unaffected (ADR 0008). The caster is marked acted (the cast spends its turn action).
     * Each effect is applied by a guard-clause helper ([applyEffect]); the exhaustive `when` there makes a new
     * effect variant a compile error until handled.
     */
    private fun cast(state: BattleState, command: Command.Cast, ctx: ResolveContext): Resolution {
        val skill = ctx.skills.getValue(command.skill)
        var target = state.unit(command.target)
        val events = mutableListOf<Event>()
        skill.effects.forEach { effect ->
            val (next, event) = applyEffect(target, effect)
            target = next
            if (event != null) events += event
        }
        return Resolution(state.withUnit(target).markActed(command.caster), events)
    }

    /** Applies one [SkillEffect] to [target], returning the new combatant and the event it surfaced (or null). */
    private fun applyEffect(target: Combatant, effect: SkillEffect): Pair<Combatant, Event?> = when (effect) {
        is SkillEffect.Heal -> applyHeal(target, effect)
        is SkillEffect.StatDelta -> applyStatDeltaEffect(target, effect)
        is SkillEffect.ApplyAilment -> applyAilment(target, effect)
        is SkillEffect.Cleanse -> applyCleanse(target)
    }

    /**
     * Lifts all active ailments from a friendly target (ADR 0008 cleanse), emitting [Event.StatusCleared].
     * No-op (null event) on a dead target or one carrying no ailments. Deterministic, RNG-free, no new
     * persistent state — it only empties the already-serialized [Combatant.ailments]; timed stat-debuffs
     * ([Combatant.effects]) are intentionally left to self-expire (cleanse removes the disabling AILMENTS).
     */
    private fun applyCleanse(target: Combatant): Pair<Combatant, Event?> {
        if (!target.alive || target.ailments.isEmpty()) return target to null
        return target.copy(ailments = emptyList()) to Event.StatusCleared(target.id)
    }

    /**
     * Inflicts a timed ailment (ADR 0008): records an [ActiveAilment] with `remaining = duration` and emits
     * [Event.StatusApplied]. No-op (null event) on a non-positive duration or a dead target. Re-casting the
     * same kind REFRESHES it (the existing one is replaced) rather than stacking, so a unit holds at most one
     * instance per kind — deterministic and keeps the legality gate a simple membership test ([Combatant.silenced]).
     */
    private fun applyAilment(target: Combatant, effect: SkillEffect.ApplyAilment): Pair<Combatant, Event?> {
        if (effect.duration <= 0 || !target.alive) return target to null
        val refreshed = target.ailments.filterNot { it.kind == effect.ailment } + ActiveAilment(effect.ailment, effect.duration)
        return target.copy(ailments = refreshed) to Event.StatusApplied(target.id, effect.ailment.name)
    }

    /**
     * FLAT = the amount directly; PERCENT_MAX = a percent of max HP via integer-truncating math
     * (hpMax * amount / 100; hpMax is small so no overflow, no floats). No-op (null event) for a non-positive
     * amount or a dead/full target — same clamp shape as [applyTerrainHeal].
     */
    private fun applyHeal(target: Combatant, effect: SkillEffect.Heal): Pair<Combatant, Event?> {
        val healAmount = when (effect.mode) {
            HealMode.FLAT -> effect.amount
            HealMode.PERCENT_MAX -> target.hpMax * effect.amount / 100
        }
        if (healAmount <= 0 || !target.alive || target.hp >= target.hpMax) return target to null
        val gained = (target.hp + healAmount).coerceAtMost(target.hpMax) - target.hp
        return target.withHp(target.hp + gained) to Event.Healed(target.id, gained)
    }

    /**
     * Signed stat change folded into the panel. Records/emits the REALIZED delta (after the floor-at-0 clamp),
     * not the requested amount, so a timed effect's expiry reverses EXACTLY what was applied — a debuff that
     * floored a low stat restores the original value instead of inflating it. duration 0 = permanent (no record).
     */
    private fun applyStatDeltaEffect(target: Combatant, effect: SkillEffect.StatDelta): Pair<Combatant, Event?> {
        if (effect.amount == 0 || !target.alive) return target to null
        val before = statValue(target.stats, effect.stat)
        val newStats = applyStatDelta(target.stats, effect.stat, effect.amount)
        val applied = statValue(newStats, effect.stat) - before
        if (applied == 0) return target to null
        val withStats = target.copy(stats = newStats)
        val next = if (effect.duration > 0) {
            withStats.copy(effects = withStats.effects + ActiveEffect(effect.stat, applied, effect.duration))
        } else {
            withStats
        }
        return next to Event.StatChanged(target.id, effect.stat, applied)
    }

    private fun applyStatDelta(stats: CombatStats, stat: AffectedStat, amount: Int): CombatStats = when (stat) {
        AffectedStat.ATK -> stats.copy(atk = (stats.atk + amount).coerceAtLeast(0))
        AffectedStat.DEF -> stats.copy(def = (stats.def + amount).coerceAtLeast(0))
        AffectedStat.MAT -> stats.copy(mat = (stats.mat + amount).coerceAtLeast(0))
        AffectedStat.RES -> stats.copy(res = (stats.res + amount).coerceAtLeast(0))
    }

    private fun statValue(stats: CombatStats, stat: AffectedStat): Int = when (stat) {
        AffectedStat.ATK -> stats.atk
        AffectedStat.DEF -> stats.def
        AffectedStat.MAT -> stats.mat
        AffectedStat.RES -> stats.res
    }

    private fun endTurn(state: BattleState, command: Command.EndTurn, ctx: ResolveContext): Resolution {
        val next = nextFaction(command.faction)
        // Reset the per-turn action economy so the next side's units start fresh, then apply terrain
        // healing to that side's units as their phase begins (FE/AW fort/village recovery).
        val advanced = state.copy(active = next, turn = state.turn + 1).clearTurnActions()
        val (healed, healEvents) = applyTerrainHeal(advanced, next, ctx)
        return Resolution(tickConditions(healed), listOf(Event.TurnEnded(command.faction)) + healEvents)
    }

    /**
     * Decrements every active timed condition by one turn-boundary (ADR 0008 Phase 3+): a stat [ActiveEffect]
     * reaching 0 is REVERSED (its recorded delta subtracted back) and dropped; an [ActiveAilment] reaching 0
     * is simply dropped (a legality gate needs no reversal). Deterministic, id-sorted, NO RNG — so it is
     * replay-safe and leaves goldens (which carry neither effects nor ailments, making this a no-op)
     * byte-identical. Runs on every [Command.EndTurn], so a `duration` of N covers N turn-boundaries.
     */
    private fun tickConditions(state: BattleState): BattleState {
        var result = state
        state.units.values.sortedBy { it.id }.forEach { unit ->
            if (unit.effects.isEmpty() && unit.ailments.isEmpty()) return@forEach
            var stats = unit.stats
            val keptEffects = mutableListOf<ActiveEffect>()
            unit.effects.forEach { effect ->
                val remaining = effect.remaining - 1
                if (remaining <= 0) {
                    stats = applyStatDelta(stats, effect.stat, -effect.amount) // expire: reverse the change
                } else {
                    keptEffects += effect.copy(remaining = remaining)
                }
            }
            val keptAilments = unit.ailments.mapNotNull { ailment ->
                (ailment.remaining - 1).takeIf { it > 0 }?.let { ailment.copy(remaining = it) } // expire: just drop
            }
            result = result.withUnit(unit.copy(stats = stats, effects = keptEffects, ailments = keptAilments))
        }
        return result
    }

    /** Heal the [faction]'s living units standing on healing tiles (capped at max HP); inert with no map. */
    private fun applyTerrainHeal(state: BattleState, faction: Faction, ctx: ResolveContext): Pair<BattleState, List<Event>> {
        val map = ctx.map ?: return state to emptyList()
        var result = state
        val events = mutableListOf<Event>()
        state.units.values.filter { it.alive && it.faction == faction }.sortedBy { it.id }.forEach { unit ->
            val heal = map.tileAt(unit.pos).heal
            if (heal > 0 && unit.hp < unit.hpMax) {
                val gained = (unit.hp + heal).coerceAtMost(unit.hpMax) - unit.hp
                result = result.withUnit(unit.withHp(unit.hp + gained))
                events += Event.Healed(unit.id, gained)
            }
        }
        return result to events
    }

    private fun move(state: BattleState, command: Command.Move): Resolution {
        val unit = state.unit(command.unit)
        return Resolution(
            state.withUnit(unit.copy(pos = command.to)).markMoved(unit.id),
            listOf(Event.Moved(unit.id, unit.pos, command.to)),
        )
    }

    private fun attack(state: BattleState, command: Command.Attack, ctx: ResolveContext): Resolution {
        val rng = Rng.restore(state.rngState)
        val attacker = state.unit(command.attacker)
        val skill = ctx.skills.getValue(command.skill)
        var defender = state.unit(command.target)
        // Attacker's terrain affinity from the tile it stands on (100 = neutral when no map / no entry).
        val affinityPct = ctx.map?.let {
            ctx.classes[attacker.classId]?.terrain?.affinity?.get(it.tileAt(attacker.pos).terrainId)
        } ?: 100
        // Defender's terrain bonuses from the tile it stands on: flat DEF + flat evasion (0 = none).
        val defenderTile = ctx.map?.tileAt(defender.pos)
        val terrainDef = defenderTile?.defBonus ?: 0
        val terrainAvoid = defenderTile?.avoidBonus ?: 0
        val profile = Formula.rollHitProfile(attacker.rates, defender.rates, rng, terrainAvoid)
        val events = mutableListOf<Event>()

        if (!profile.hit) {
            // A missed attack still spends the attacker's action for the turn.
            events += Event.Missed(attacker.id, defender.id)
            return Resolution(state.copy(rngState = rng.snapshot()).markActed(attacker.id), events)
        }

        val (atkValue, defValue) = when (skill.kind) {
            DamageKind.PHYSICAL -> attacker.stats.atk to defender.stats.def
            DamageKind.STRATEGY -> attacker.stats.mat to defender.stats.res
        }
        val effectiveDef = defValue + terrainDef
        val counter = ctx.classes[attacker.classId]?.counters?.get(defender.classId)
        val broke = atkValue - effectiveDef > 0

        fun strike(coeff: Int, isCrit: Boolean, isCombo: Boolean) {
            val damage = Formula.damage(DamageInput(
                atk = atkValue,
                def = effectiveDef,
                skillCoeffPct = coeff,
                modifiers = DamageModifiers(terrainAffinityPct = affinityPct),
                flags = DamageFlags(crit = isCrit, counter = counter, blocked = profile.blocked),
            ), ctx.rules)
            defender = defender.withHp((defender.hp - damage).coerceAtLeast(0))
            events += Event.Damaged(defender.id, damage, isCrit, isCombo, broke)
            if (!defender.alive) events += Event.Died(defender.id)
        }

        strike(skill.powerCoeff, profile.crit, isCombo = false)
        if (profile.combo && defender.alive) {
            strike(ctx.rules.damage.comboCoeffPct, isCrit = false, isCombo = true)
        }

        return Resolution(state.withUnit(defender).copy(rngState = rng.snapshot()).markActed(attacker.id), events)
    }

    private fun nextFaction(faction: Faction): Faction = when (faction) {
        Faction.PLAYER, Faction.ALLY -> Faction.ENEMY
        Faction.ENEMY -> Faction.PLAYER
    }

    val DEMO_SKILLS: Map<String, Skill> = mapOf(
        "atk" to Skill("atk", "Attack", DamageKind.PHYSICAL, 100),
    )
}
