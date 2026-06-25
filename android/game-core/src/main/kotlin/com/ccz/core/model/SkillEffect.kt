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

    /**
     * Instantly add a SIGNED [amount] to the target's [stat] for the rest of the battle (ADR 0008 Phase 2) —
     * a stat change resolved straight into the panel, with NO duration (timed effects are Phase 3). A
     * positive amount on a [EffectTarget.SELF]/[EffectTarget.ALLY] target is a buff; a negative amount on an
     * [EffectTarget.ENEMY] target is a debuff. Deterministic, RNG-free; [amount] is a content-authored
     * parameter (contentVersion, like [Skill.powerCoeff]), validated non-zero by `ContentValidator`. The
     * resolver floors the resulting stat at 0 defensively.
     */
    data class StatDelta(
        val target: EffectTarget,
        val stat: AffectedStat,
        val amount: Int,
        // 0 = a permanent/instant change (default, ADR 0008 Phase 2). > 0 = a TIMED change (Phase 3): the
        // delta is applied now AND reversed after [duration] turn-boundaries elapse (each Command.EndTurn
        // decrements every active effect; one reaching 0 reverses its stat mod). Persisted via Combatant.effects.
        val duration: Int = 0,
    ) : SkillEffect
}

/**
 * A timed stat modification currently active on a unit (ADR 0008 Phase 3). When a [SkillEffect.StatDelta]
 * with `duration > 0` resolves, the unit's [stat] is changed by [amount] (already folded into its panel) and
 * one of these is recorded with [remaining] = duration; each turn-boundary decrements it, and at 0 the mod is
 * reversed (the recorded [amount] is subtracted back). Persisted on [com.ccz.core.model.Combatant.effects];
 * reversal is deterministic (no RNG), so a save = fresh initialState + folded commands reproduces it exactly.
 */
data class ActiveEffect(val stat: AffectedStat, val amount: Int, val remaining: Int)

/** How a [SkillEffect.Heal]'s amount is read: a flat HP value, or a percent of the target's max HP. */
enum class HealMode { FLAT, PERCENT_MAX }

/** A combat stat a [SkillEffect.StatDelta] can modify (mirrors the four [CombatStats] fields). */
enum class AffectedStat { ATK, DEF, MAT, RES }

/**
 * Who a [SkillEffect] lands on, relative to the caster: the caster itself ([SELF]), a same-side [ALLY]
 * (which includes the caster), or an [ENEMY] (the inverse side — for debuffs). [SELF]/[ALLY] are the
 * friendly bands (heals/buffs); [ENEMY] is used by debuff [SkillEffect.StatDelta] (a [SkillEffect.Heal]
 * must stay friendly — `ContentValidator` rejects a heal targeting [ENEMY]).
 */
enum class EffectTarget { SELF, ALLY, ENEMY }
