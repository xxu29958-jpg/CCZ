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
    val loadouts: Map<String, List<String>> = emptyMap(),
    val rules: BattleRules = BattleRules.DEFAULT,
) {
    /**
     * Whether [unitId] is allowed to attack with [skillId] under its skill loadout. A unit that
     * has a loadout configured (an entry in [loadouts]) may use only the skills that loadout
     * lists — a skill outside it is rejected fail-closed, so the loadout is the authority on what
     * a unit can do, not the presentation layer. A unit with no loadout entry is unconstrained
     * (any skill the [skills] table defines is allowed); this is the backward-compatible default
     * until the content driver layer fills a loadout for every unit (mirrors how the optional
     * [BattleMap] gates spawn/move placement only once it is threaded in). It does not check that
     * [skillId] exists in [skills] — [CommandValidator] does that separately.
     */
    fun loadoutAllows(unitId: String, skillId: String): Boolean {
        val loadout = loadouts[unitId] ?: return true
        return skillId in loadout
    }
}
