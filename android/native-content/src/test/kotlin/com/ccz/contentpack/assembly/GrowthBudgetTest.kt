package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassGrowth
import com.ccz.contentpack.UnitDef
import com.ccz.contentpack.UnitIdentity
import com.ccz.contentpack.UnitProfile
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Faction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Import-time growth budgeting (ADR 0006): a pure, deterministic, RNG-free closed form that bakes a
 * unit's panel for its level at assembly time. The identity-at-empty-growth tests are the replay-safety
 * tripwire — they pin that the default (no growth) path leaves panels byte-identical, which is what
 * keeps goldens unchanged and `RULES_VERSION` un-bumped.
 */
class GrowthBudgetTest {
    private val base = CombatStats(atk = 100, def = 80, mat = 60, res = 40)
    private val cfg = GrowthConfig()

    @Test
    fun emptyGrowthIsIdentityAtAnyLevel() {
        assertEquals(base, GrowthBudget.budgetStats(base, ClassGrowth(), level = 50, gradeMulPct = 100, cfg = cfg))
        assertEquals(200, GrowthBudget.budgetHp(200, ClassGrowth(), level = 50, gradeMulPct = 100, cfg = cfg))
    }

    @Test
    fun levelOneIsAlwaysBase() {
        val growth = ClassGrowth(atk = 9, def = 9, mat = 9, res = 9, hp = 9)
        assertEquals(base, GrowthBudget.budgetStats(base, growth, level = 1, gradeMulPct = 100, cfg = cfg))
        assertEquals(200, GrowthBudget.budgetHp(200, growth, level = 1, gradeMulPct = 100, cfg = cfg))
    }

    @Test
    fun linearGrowthIsDeterministic() {
        // +growth per level above 1: level 10 -> base + growth*9
        val grown = GrowthBudget.budgetStats(base, ClassGrowth(atk = 5, def = 3), level = 10, gradeMulPct = 100, cfg = cfg)
        assertEquals(CombatStats(atk = 100 + 45, def = 80 + 27, mat = 60, res = 40), grown)
        assertEquals(200 + 4 * 9, GrowthBudget.budgetHp(200, ClassGrowth(hp = 4), level = 10, gradeMulPct = 100, cfg = cfg))
    }

    @Test
    fun gradeMultiplierScalesTheGainNotTheBase() {
        // 200% grade doubles the per-level gain only: base + growth*9*200/100
        val grown = GrowthBudget.budgetStats(base, ClassGrowth(atk = 5), level = 10, gradeMulPct = 200, cfg = cfg)
        assertEquals(100 + 90, grown.atk)
    }

    @Test
    fun capClampsRunawayGrowth() {
        val tiny = GrowthConfig(statCap = 150, hpCap = 250)
        assertEquals(150, GrowthBudget.budgetStats(base, ClassGrowth(atk = 999), level = 99, gradeMulPct = 100, cfg = tiny).atk)
        assertEquals(250, GrowthBudget.budgetHp(200, ClassGrowth(hp = 999), level = 99, gradeMulPct = 100, cfg = tiny))
    }

    @Test
    fun gradeMulPctClampsOutOfRangeIntoTheTierTable() {
        val tiered = GrowthConfig(gradeMulPctByGrade = listOf(100, 120, 150))
        assertEquals(100, GrowthBudget.gradeMulPct(0, tiered))
        assertEquals(150, GrowthBudget.gradeMulPct(2, tiered))
        assertEquals(150, GrowthBudget.gradeMulPct(9, tiered), "too-high grade clamps to the top tier")
        assertEquals(100, GrowthBudget.gradeMulPct(-1, tiered), "negative grade clamps to the neutral first tier")
    }

    @Test
    fun defaultGradeLadderIsNeutralAtZeroAndDoublesAtTop() {
        // this engine's own quality ladder: six tiers, grade 0 neutral, +20%/tier up to 2x growth speed
        assertEquals(6, cfg.gradeMulPctByGrade.size, "six-tier quality ladder")
        assertEquals(100, GrowthBudget.gradeMulPct(0, cfg), "tier 0 is the neutral baseline")
        assertEquals(140, GrowthBudget.gradeMulPct(2, cfg))
        assertEquals(200, GrowthBudget.gradeMulPct(5, cfg), "top tier doubles growth speed")
    }

    // --- assembly integration: BattleAssembler budgets reserves through the same closed form ---

    private fun unit(id: String, classId: String, level: Int, grade: Int = 0): UnitDef = UnitDef(
        identity = UnitIdentity(id = id, name = id, classId = classId, faction = Faction.PLAYER),
        profile = UnitProfile(level = level, hpMax = 200, stats = base, grade = grade),
    )

    @Test
    fun reservesKeepBasePanelWhenNoGrowthSupplied() {
        val reserve = BattleAssembler.reserves(listOf(unit("u", "job_1", level = 30))).getValue("u")
        assertEquals(base, reserve.stats, "no growth -> identity panel (goldens stay byte-identical)")
        assertEquals(200, reserve.hpMax)
        assertEquals(200, reserve.hp, "reserves enter at full HP")
    }

    @Test
    fun reservesBudgetClassGrowthToUnitLevel() {
        val growth = mapOf("job_1" to ClassGrowth(atk = 5, hp = 10))
        val reserve = BattleAssembler.reserves(listOf(unit("u", "job_1", level = 10)), growth).getValue("u")
        assertEquals(100 + 45, reserve.stats.atk, "atk grew by 5*(10-1)")
        assertEquals(200 + 90, reserve.hpMax, "hp grew by 10*(10-1)")
        assertEquals(reserve.hpMax, reserve.hp)
    }

    @Test
    fun higherGradeReservesGrowFaster() {
        val growth = mapOf("job_1" to ClassGrowth(atk = 5))
        val neutral = BattleAssembler.reserves(listOf(unit("g0", "job_1", level = 10, grade = 0)), growth).getValue("g0")
        val elite = BattleAssembler.reserves(listOf(unit("g2", "job_1", level = 10, grade = 2)), growth).getValue("g2")
        assertEquals(100 + 45, neutral.stats.atk, "grade 0 (neutral 100%): +5*9")
        assertEquals(100 + 63, elite.stats.atk, "grade 2 (140% tier): +5*9*140/100")
    }
}
