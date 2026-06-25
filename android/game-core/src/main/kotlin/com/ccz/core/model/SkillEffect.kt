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
     * Restore a flat [amount] of HP to the cast's target (capped at its max HP). Deterministic and
     * RNG-free — the same clamp terrain healing uses. [amount] is a content-authored parameter on the
     * same footing as [Skill.powerCoeff] (ADR 0008 magnitude gate: gated by contentVersion, NOT
     * RULES_VERSION). It carries no `require`: a non-positive amount is rejected as a clean
     * `ContentValidator` issue (>= 1) and, defensively, the resolver only heals when amount > 0.
     */
    data class Heal(val target: EffectTarget, val amount: Int) : SkillEffect
}

/**
 * Who a [SkillEffect] lands on, relative to the caster. Phase 1: the caster itself ([SELF]) or a
 * same-side [ALLY] (which includes the caster). Enemy-targeting effects (debuffs/ailments) are a
 * later ADR-0008 phase, so the enum deliberately omits an ENEMY band for now.
 */
enum class EffectTarget { SELF, ALLY }
