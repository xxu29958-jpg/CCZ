package com.ccz.app.campaign

import com.ccz.contentpack.ContentValidator
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.Gameplay
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotedStageRuntimesTest {
    @Test
    fun quyangSiegePackValidatesAndAssembles() {
        val runtime = PromotedStageRuntimes.QuyangSiege

        assertEquals(emptyList<Any>(), ContentValidator.validate(runtime.battlePack()))
        assertEquals("legacy_stage_2", runtime.stageId)
        assertEquals("legacy_stage_2", runtime.battleScriptId)
        assertEquals("legacy_stage_2_map", runtime.mapId)
        assertNull(runtime.introScriptOrNull())
    }

    @Test
    fun quyangSiegeDeploysRealLegacyRosterPlusDefaultPlayerParty() {
        val runtime = PromotedStageRuntimes.QuyangSiege
        val state = runtime.initialState()

        assertEquals(43, state.units.size)
        assertEquals(setOf(Faction.PLAYER, Faction.ALLY, Faction.ENEMY), state.units.values.map { it.faction }.toSet())
        assertTrue("default player trio is present", listOf("hero_1", "hero_2", "hero_3").all { it in state.units })
        assertEquals(29, state.units.values.count { it.faction == Faction.ENEMY })
        assertEquals(11, state.units.values.count { it.faction == Faction.ALLY })
        assertTrue("everyone has a basic attack loadout", runtime.context().loadouts.values.all { it == listOf("skill_1") })
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, runtime.script()))
    }

    @Test
    fun quyangSiegeUsesRealMapAndDefaultObjectives() {
        val runtime = PromotedStageRuntimes.QuyangSiege
        val map = runtime.context().map

        assertEquals(21, map.width)
        assertEquals(20, map.height)
        assertTrue("real map has varied terrain", (0 until map.width).flatMap { x ->
            (0 until map.height).map { y -> map.tileAt(com.ccz.core.model.Pos(x, y)).terrainId }
        }.toSet().size > 1)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), runtime.script().win)
        assertEquals(listOf(WinLoseCondition.ProtectAlive("hero_1")), runtime.script().lose)
    }
}
