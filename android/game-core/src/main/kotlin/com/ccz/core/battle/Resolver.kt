package com.ccz.core.battle

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
     * Resolves a [Command.Cast]: applies the skill's effects to the target, deterministically and
     * RNG-FREE — [BattleState.rngState] is returned unchanged (like Move/Wait), so a cast perturbs no
     * draw order and the damage golden is unaffected (ADR 0008). The caster is marked acted (the cast
     * spends its turn action). Phase 1 handles a single [SkillEffect.Heal]; the exhaustive `when` makes
     * a future effect variant a compile error until handled here.
     */
    private fun cast(state: BattleState, command: Command.Cast, ctx: ResolveContext): Resolution {
        val skill = ctx.skills.getValue(command.skill)
        var target = state.unit(command.target)
        val events = mutableListOf<Event>()
        skill.effects.forEach { effect ->
            when (effect) {
                is SkillEffect.Heal -> {
                    // FLAT = the amount directly; PERCENT_MAX = a percent of max HP via integer-truncating
                    // math (hpMax * amount / 100; hpMax is small so no overflow, no floats). Guard amount > 0
                    // (ContentValidator enforces the bounds; this is defense-in-depth so a bad amount can never
                    // reduce HP) and only heal a living, not-full target — same clamp shape as applyTerrainHeal.
                    val healAmount = when (effect.mode) {
                        HealMode.FLAT -> effect.amount
                        HealMode.PERCENT_MAX -> target.hpMax * effect.amount / 100
                    }
                    if (healAmount > 0 && target.alive && target.hp < target.hpMax) {
                        val gained = (target.hp + healAmount).coerceAtMost(target.hpMax) - target.hp
                        target = target.withHp(target.hp + gained)
                        events += Event.Healed(target.id, gained)
                    }
                }
            }
        }
        return Resolution(state.withUnit(target).markActed(command.caster), events)
    }

    private fun endTurn(state: BattleState, command: Command.EndTurn, ctx: ResolveContext): Resolution {
        val next = nextFaction(command.faction)
        // Reset the per-turn action economy so the next side's units start fresh, then apply terrain
        // healing to that side's units as their phase begins (FE/AW fort/village recovery).
        val advanced = state.copy(active = next, turn = state.turn + 1).clearTurnActions()
        val (healed, healEvents) = applyTerrainHeal(advanced, next, ctx)
        return Resolution(healed, listOf(Event.TurnEnded(command.faction)) + healEvents)
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
