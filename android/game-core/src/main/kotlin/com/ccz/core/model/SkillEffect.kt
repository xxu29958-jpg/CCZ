package com.ccz.core.model

/**
 * An engine-owned effect a skill applies beyond raw damage (ADR 0008). Phase 1 models a single
 * deterministic variant — a flat single-target [Heal] — resolved through `Command.Cast` /
 * `Resolver.cast` with ZERO RNG, so a battle that casts replays byte-identically and the damage
 * golden is untouched. Duration / status / percent / area / probabilistic effects and damage+effect
 * riders are deferred to later ADR-0008 phases behind explicit RULES_VERSION / save-schema bumps;
 * this sealed type is the seam they extend. A skill carries a list of effects ([Skill.effects]).
 */
sealed interface SkillEffect {
    /**
     * Restore HP to the cast's target (capped at its max HP), by [amount] interpreted per [mode]:
     * [HealMode.FLAT] = a flat HP amount; [HealMode.PERCENT_MAX] = a percent of the target's max HP
     * (the legacy 战法 "恢复生命X%" shape — see ADR 0008). Deterministic and RNG-free; percent uses
     * integer truncating math (`hpMax * amount / 100`), no floats. [amount] is a content-authored
     * parameter on the same footing as [Skill.powerCoeff] (ADR 0008 magnitude gate: gated by
     * contentVersion, NOT RULES_VERSION). It carries no `require`: a bad amount is rejected as a clean
     * `ContentValidator` issue (FLAT >= 1; PERCENT_MAX in 1..100), and the resolver only heals when the
     * computed amount > 0.
     */
    data class Heal(val target: EffectTarget, val amount: Int, val mode: HealMode = HealMode.FLAT) : SkillEffect
}

/** How a [SkillEffect.Heal]'s amount is read: a flat HP value, or a percent of the target's max HP. */
enum class HealMode { FLAT, PERCENT_MAX }

/**
 * Who a [SkillEffect] lands on, relative to the caster. Phase 1: the caster itself ([SELF]) or a
 * same-side [ALLY] (which includes the caster). Enemy-targeting effects (debuffs/ailments) are a
 * later ADR-0008 phase, so the enum deliberately omits an ENEMY band for now.
 */
enum class EffectTarget { SELF, ALLY }
