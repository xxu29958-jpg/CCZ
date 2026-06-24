package com.ccz.core.battle

import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Skill
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
        is Command.Wait -> Resolution(state.markActed(command.unit), listOf(Event.Waited(command.unit)))
        is Command.EndTurn -> Resolution(
            // Reset the per-turn action economy so the next side's units start fresh.
            state.copy(active = nextFaction(command.faction), turn = state.turn + 1).clearTurnActions(),
            listOf(Event.TurnEnded(command.faction)),
        )
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
        val profile = Formula.rollHitProfile(attacker.rates, defender.rates, rng)
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
        val counter = ctx.classes[attacker.classId]?.counters?.get(defender.classId)
        val broke = atkValue - defValue > 0

        fun strike(coeff: Int, isCrit: Boolean, isCombo: Boolean) {
            val damage = Formula.damage(DamageInput(
                atk = atkValue,
                def = defValue,
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
