package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassGrowth
import com.ccz.core.model.CombatStats

/**
 * Tuning for [GrowthBudget]: a soft cap that flattens the legacy stat inflation (the old game's
 * 9M/40M run-time ceilings are an inflation artifact, deliberately not reproduced) and the grade →
 * multiplier table. Defaults are NEUTRAL: a single-entry `gradeMulPctByGrade = [100]` means every
 * unit budgets at 100% regardless of grade, so the grade dimension is structurally present but
 * dormant until a later phase (per ADR 0006) feeds real `dic_grade` tiers in.
 */
data class GrowthConfig(
    val statCap: Int = 999,
    val hpCap: Int = 9999,
    val gradeMulPctByGrade: List<Int> = listOf(100),
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
    /** Grade multiplier percent for [grade]; out-of-range grades fall back to the last (highest) tier. */
    fun gradeMulPct(grade: Int, cfg: GrowthConfig): Int =
        cfg.gradeMulPctByGrade.getOrElse(grade) { cfg.gradeMulPctByGrade.last() }

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
