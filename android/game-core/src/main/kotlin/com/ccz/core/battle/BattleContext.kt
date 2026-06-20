package com.ccz.core.battle

import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass

/**
 * Immutable per-battle context: the static map plus the rule/content tables a
 * command is validated and resolved against. Bundled into one value object so the
 * gameplay entry point stays within the parameter-count budget and so rule
 * configuration is passed explicitly (CCZ_ENGINE_RULES: 规则配置必须以不可变 value
 * object 显式传入).
 */
data class BattleContext(
    val map: BattleMap,
    val classes: Map<String, UnitClass>,
    val skills: Map<String, Skill> = Resolver.DEMO_SKILLS,
    val rules: BattleRules = BattleRules.DEFAULT,
)
