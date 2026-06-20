package com.ccz.core.battle

import com.ccz.core.model.CounterRelation
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleDataTest {
    @Test
    fun damageRulesAreInjectedInsteadOfGlobalMutableState() {
        val input = DamageInput(
            atk = 30,
            def = 10,
            skillCoeffPct = 100,
            flags = DamageFlags(crit = true, counter = CounterRelation.FAVOR, blocked = true),
        )
        val customRules = BattleRules(
            damage = DamageRuleSet(
                critPct = 200,
                counter = CounterRuleSet(favorPct = 100),
                blockReducePct = 100,
            ),
        )

        assertEquals(23, Formula.damage(input))
        assertEquals(40, Formula.damage(input, customRules))
    }

    @Test
    fun chipDamageUsesInjectedRoundingRule() {
        val input = DamageInput(atk = 15, def = 20, skillCoeffPct = 100)
        val customRules = BattleRules(
            rounding = Rounding.CEIL,
            damage = DamageRuleSet(chipPermille = 101),
        )

        assertEquals(1, Formula.damage(input))
        assertEquals(2, Formula.damage(input, customRules))
    }
}
