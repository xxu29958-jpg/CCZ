package com.ccz.app.battle

import com.ccz.contentpack.ContentValidator
import com.ccz.core.model.Faction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real-data battle the app plays: a content pack generated from the user's decrypted legacy tables
 * (real classes + real `dic_job` growth + real `dic_hero` stats + a grade forged from combat strength).
 * This pins that the WHOLE growth/grade chain bites on REAL heroes in the shipped battle: each unit's
 * on-field panel is budgeted by its real class growth × its deploy level × its quality grade, so an elite
 * (关羽, grade 2 level 8) visibly outscales a rookie (邓茂, grade 0 level 4).
 */
class RealBattleTest {
    @Test
    fun theRealPackValidatesClean() {
        assertEquals(
            "the generated real-data pack must pass content validation before assembly",
            emptyList<Any>(),
            ContentValidator.validate(RealBattle.pack()),
        )
    }

    @Test
    fun theRealRosterDeploysWithRealStatsAndSides() {
        val state = RealBattle.initialState()
        assertEquals(
            setOf("hero_1", "hero_2", "hero_3", "hero_226", "hero_227"),
            state.units.keys,
        )
        assertEquals(Faction.PLAYER, state.units.getValue("hero_2").faction) // 关羽
        assertEquals(Faction.ENEMY, state.units.getValue("hero_226").faction) // 程远志 (spawn faction override)
        assertEquals(Faction.ENEMY, state.units.getValue("hero_227").faction) // 邓茂
    }

    @Test
    fun growthAndGradeBudgetRealHeroPanelsOnTheField() {
        val state = RealBattle.initialState()

        // 关羽 — 裨将 grows +4 atk / +6 hp per level; deployed level 8, grade 2 (140% tier). Over (8-1)
        // levels: atk 98 + 4*7*140/100 = 137, hp 170 + 6*7*140/100 = 228. He enters at full hp.
        val guanyu = state.units.getValue("hero_2")
        assertEquals(137, guanyu.stats.atk)
        assertEquals(228, guanyu.hpMax)
        assertEquals(228, guanyu.hp)

        // 邓茂 — 黄巾军 grows +3 atk / +5 hp per level; deployed level 4, grade 0 (neutral 100%). Over (4-1)
        // levels: atk 75 + 3*3*100/100 = 84, hp 140 + 5*3*100/100 = 155. The rookie trails the elite.
        val dengmao = state.units.getValue("hero_227")
        assertEquals(84, dengmao.stats.atk)
        assertEquals(155, dengmao.hpMax)
        assertTrue("the grade-2 veteran outscales the grade-0 rookie", guanyu.stats.atk > dengmao.stats.atk)
    }
}
