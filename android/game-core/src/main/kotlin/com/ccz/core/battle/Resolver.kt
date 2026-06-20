package com.ccz.core.battle

import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass
import com.ccz.core.rng.Rng

object Resolver {
    fun apply(
        state: BattleState,
        command: Command,
        classes: Map<String, UnitClass>,
        skills: Map<String, Skill> = DEMO_SKILLS,
        rules: BattleRules = BattleRules.DEFAULT,
    ): Resolution = when (command) {
        is Command.Move -> move(state, command)
        is Command.Attack -> attack(state, command, classes, skills, rules)
        is Command.EndTurn -> Resolution(
            state.copy(active = nextFaction(command.faction), turn = state.turn + 1),
            listOf(Event.TurnEnded(command.faction)),
        )
    }

    private fun move(state: BattleState, command: Command.Move): Resolution {
        val unit = state.unit(command.unit)
        return Resolution(
            state.withUnit(unit.copy(pos = command.to)),
            listOf(Event.Moved(unit.id, unit.pos, command.to)),
        )
    }

    private fun attack(
        state: BattleState,
        command: Command.Attack,
        classes: Map<String, UnitClass>,
        skills: Map<String, Skill>,
        rules: BattleRules,
    ): Resolution {
        val rng = Rng.restore(state.rngState)
        val attacker = state.unit(command.attacker)
        val skill = skills.getValue(command.skill)
        var defender = state.unit(command.target)
        val profile = Formula.rollHitProfile(attacker.rates, defender.rates, rng)
        val events = mutableListOf<Event>()

        if (!profile.hit) {
            events += Event.Missed(attacker.id, defender.id)
            return Resolution(state.copy(rngState = rng.snapshot()), events)
        }

        val (atkValue, defValue) = when (skill.kind) {
            DamageKind.PHYSICAL -> attacker.stats.atk to defender.stats.def
            DamageKind.STRATEGY -> attacker.stats.mat to defender.stats.res
        }
        val counter = classes[attacker.classId]?.counters?.get(defender.classId)
        val broke = atkValue - defValue > 0

        fun strike(coeff: Int, isCrit: Boolean, isCombo: Boolean) {
            val damage = Formula.damage(DamageInput(
                atk = atkValue,
                def = defValue,
                skillCoeffPct = coeff,
                flags = DamageFlags(crit = isCrit, counter = counter, blocked = profile.blocked),
            ), rules)
            defender = defender.withHp((defender.hp - damage).coerceAtLeast(0))
            events += Event.Damaged(defender.id, damage, isCrit, isCombo, broke)
            if (!defender.alive) events += Event.Died(defender.id)
        }

        strike(skill.powerCoeff, profile.crit, isCombo = false)
        if (profile.combo && defender.alive) {
            strike(rules.damage.comboCoeffPct, isCrit = false, isCombo = true)
        }

        return Resolution(state.withUnit(defender).copy(rngState = rng.snapshot()), events)
    }

    private fun nextFaction(faction: Faction): Faction = when (faction) {
        Faction.PLAYER, Faction.ALLY -> Faction.ENEMY
        Faction.ENEMY -> Faction.PLAYER
    }

    val DEMO_SKILLS: Map<String, Skill> = mapOf(
        "atk" to Skill("atk", "Attack", DamageKind.PHYSICAL, 100),
    )
}
