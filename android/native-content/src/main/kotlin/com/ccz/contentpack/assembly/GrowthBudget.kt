package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassGrowth
import com.ccz.core.model.CombatStats

/**
 * Tuning for [GrowthBudget]: a soft cap that flattens stat inflation (the old game's 9M/40M run-time
 * ceilings are an inflation artifact we deliberately do NOT reproduce) and THIS engine's own quality-tier
 * → growth-multiplier ladder.
 *
 * Grade ("评级") is this engine's quality lever — designed here, not ported from the old game's rating
 * table: a non-negative tier index that scales how fast a unit's per-level growth accrues. The default
 * ladder is six tiers at a flat +20%/tier (100..200): tier 0 is the NEUTRAL 100% baseline and the top
 * tier doubles growth SPEED only — deliberately linear and shallow so elite units pull ahead without the
 * old game's multiplicative runaway. Units default to grade 0, so content that declares no grade budgets
 * exactly as before; the lever is real but inert until content sets a higher tier. (The old "评级" data
 * was only the ore that suggested a quality lever is worth having — its tier names and random
 * up/down-grade churn are intentionally not reproduced.)
 */
data class GrowthConfig(
    val statCap: Int = 999,
    val hpCap: Int = 9999,
    val gradeMulPctByGrade: List<Int> = listOf(100, 120, 140, 160, 180, 200),
) {
    init {
        require(statCap >= 0) { "statCap must be >= 0, was $statCap" }
        require(hpCap >= 0) { "hpCap must be >= 0, was $hpCap" }
        require(gradeMulPctByGrade.isNotEmpty()) { "gradeMulPctByGrade needs at least one (neutral) entry" }
    }
}

/**
 * Budgets a unit's combat panel at its target level — a PURE, deterministic, RNG-free closed form
 * (ADR 0006 "导入期成长预算"). Growth happens at assembly/import time and is baked into the snapshot
 * the save envelope stores; game-core never sees a growth table and replay never recomputes, so the
 * battle RNG draw order is untouched and goldens stay byte-identical. With the default empty
 * [ClassGrowth] (all weights 0) every result equals the base panel — the engine's current behaviour.
 *
 * Per stat: `clamp(base + growth * (level - 1) * gradeMulPct / 100, 0, cap)`, truncating (matching
 * `Formula.mulDiv` TRUNCATE), in [Long] so a large level × growth can't overflow before the clamp.
 */
object GrowthBudget {
    /** Growth multiplier percent for [grade], clamped into the tier table (negatives → neutral first
     *  tier, too-high → top tier) so malformed grades can never crash or over-reward. */
    fun gradeMulPct(grade: Int, cfg: GrowthConfig): Int =
        cfg.gradeMulPctByGrade[grade.coerceIn(0, cfg.gradeMulPctByGrade.lastIndex)]

    private fun budget(base: Int, growth: Int, level: Int, gradeMulPct: Int, cap: Int): Int {
        val levels = (level - 1).coerceAtLeast(0).toLong()
        val grown = base + growth.toLong() * levels * gradeMulPct / 100
        return grown.coerceIn(0L, cap.toLong()).toInt()
    }

    /** The four-stat panel grown to [level] under [growth] and [gradeMulPct], clamped to the stat cap. */
    fun budgetStats(base: CombatStats, growth: ClassGrowth, level: Int, gradeMulPct: Int, cfg: GrowthConfig): CombatStats =
        CombatStats(
            atk = budget(base.atk, growth.atk, level, gradeMulPct, cfg.statCap),
            def = budget(base.def, growth.def, level, gradeMulPct, cfg.statCap),
            mat = budget(base.mat, growth.mat, level, gradeMulPct, cfg.statCap),
            res = budget(base.res, growth.res, level, gradeMulPct, cfg.statCap),
        )

    /** Max HP grown to [level] under [growth] and [gradeMulPct], clamped to the HP cap. */
    fun budgetHp(baseHp: Int, growth: ClassGrowth, level: Int, gradeMulPct: Int, cfg: GrowthConfig): Int =
        budget(baseHp, growth.hp, level, gradeMulPct, cfg.hpCap)
}
