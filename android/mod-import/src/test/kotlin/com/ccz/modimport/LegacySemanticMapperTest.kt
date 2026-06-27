package com.ccz.modimport

import com.ccz.modimport.LegacyScriptDecoder.LegacyObjectives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives [LegacySemanticMapper]: legacy objective clauses → CCZ [PackCondition]s, with everything that lacks a
 * confident mapping recorded as unsupported (fail-closed), never force-mapped.
 */
class LegacySemanticMapperTest {
    // 大兴山 final-phase objectives, with a name->id resolver standing in for dic_hero.
    private val names = mapOf("刘备" to "hero_1", "邹靖" to "hero_182", "张角" to "hero_967")
    private fun nameToId(n: String): String? = names[n]

    @Test
    fun mapsAnnihilateWinAndProtectLoseFromRealClauses() {
        val obj = LegacyObjectives(win = listOf("全灭敌军。"), lose = listOf("刘备死亡。", "邹靖死亡。"), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertEquals(listOf(PackCondition(PackCondition.ANNIHILATE_ENEMIES)), m.win)
        assertEquals(
            listOf(
                PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1"),
                PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_182"),
            ),
            m.lose,
            "both protected units are resolved to their real hero ids",
        )
        assertTrue(m.unsupported.isEmpty())
    }

    @Test
    fun recognizesAnnihilationSynonymsAsTheOrdinaryClearCondition() {
        // annihilation is the DEFAULT win; its common synonyms all map to annihilate_enemies (not a guess).
        for (clause in listOf("全灭敌军。", "全歼敌军", "歼灭敌军", "全军覆没", "击破全部敌军")) {
            val m = LegacySemanticMapper.mapObjectives(
                LegacyObjectives(win = listOf(clause), lose = emptyList(), offset = 0),
                ::nameToId,
            )
            assertEquals(listOf(PackCondition(PackCondition.ANNIHILATE_ENEMIES)), m.win, "annihilation synonym: $clause")
            assertTrue(m.unsupported.isEmpty(), "annihilation synonym is mapped, not unsupported: $clause")
        }
    }

    @Test
    fun winSideDeathMapsToDefeatUnitNotProtect() {
        // "X死亡" in the WIN section = "kill X (a boss) to win" -> defeat_unit; in LOSE it would be protect_alive
        val obj = LegacyObjectives(win = listOf("张角死亡。"), lose = emptyList(), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertEquals(listOf(PackCondition(PackCondition.DEFEAT_UNIT, unit = "hero_967")), m.win)
        assertTrue(m.lose.isEmpty() && m.unsupported.isEmpty())
    }

    @Test
    fun turnDeadlineIsUnsupportedNotForcedOntoSurviveTurns() {
        val obj = LegacyObjectives(win = emptyList(), lose = listOf("回合数超过15。"), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertTrue(m.lose.isEmpty(), "a turn deadline is NOT mapped (CCZ SurviveTurns is the opposite)")
        assertEquals(1, m.unsupported.size)
        assertEquals("回合数超过15。", m.unsupported[0].clause)
        assertEquals("lose", m.unsupported[0].side)
    }

    @Test
    fun areaObjectiveIsUnsupported() {
        val obj = LegacyObjectives(win = listOf("到达村庄。"), lose = listOf("村庄被占领。"), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertTrue(m.win.isEmpty() && m.lose.isEmpty())
        assertEquals(2, m.unsupported.size, "both the reach-village win and the village-captured lose are unmapped")
    }

    @Test
    fun unresolvedUnitNameFailsClosedNotGuessed() {
        // a death clause whose name has no global dic_hero id (a battle-local actor) is unsupported, not faked
        val obj = LegacyObjectives(win = emptyList(), lose = listOf("无名小卒死亡。"), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertTrue(m.lose.isEmpty())
        assertEquals(1, m.unsupported.size)
        assertTrue("无名小卒" in m.unsupported[0].reason)
    }

    @Test
    fun unknownClauseIsUnsupported() {
        val obj = LegacyObjectives(win = listOf("收集七颗龙珠"), lose = emptyList(), offset = 0)
        val m = LegacySemanticMapper.mapObjectives(obj, ::nameToId)
        assertTrue(m.win.isEmpty())
        assertEquals(1, m.unsupported.size)
        assertEquals("win", m.unsupported[0].side)
    }
}
