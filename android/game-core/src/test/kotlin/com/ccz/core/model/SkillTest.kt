package com.ccz.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [Skill.isCast] / [Skill.isAttack] as the single source for the ADR 0008 use-kind split (effects
 * emptiness): a pure-damage skill is an ATTACK ([Command.Attack]), one carrying effects is a CAST
 * ([Command.Cast]). Every gameplay validator / read-only query / enemy AI / presentation reducer classifies a
 * skill through these properties rather than re-deriving `effects.isEmpty()` inline, so this is the one place
 * the contract is asserted — and the one place to change when Phase 4 composite (damage+rider) skills land.
 */
class SkillTest {
    private fun skill(effects: List<SkillEffect>): Skill = Skill("s", "S", DamageKind.PHYSICAL, 100, effects = effects)

    @Test
    fun aSkillWithNoEffectsIsAnAttackNotACast() {
        val attack = skill(emptyList())
        assertTrue(attack.isAttack, "no effects ⟹ attack")
        assertFalse(attack.isCast, "no effects ⟹ not cast")
        assertTrue(attack.isAttack != attack.isCast, "the two are exact complements")
    }

    @Test
    fun aSkillWithEffectsIsACastNotAnAttack() {
        val cast = skill(listOf(SkillEffect.Heal(EffectTarget.SELF, 10)))
        assertTrue(cast.isCast, "has effects ⟹ cast")
        assertFalse(cast.isAttack, "has effects ⟹ not attack")
        assertTrue(cast.isAttack != cast.isCast, "the two are exact complements")
    }
}
