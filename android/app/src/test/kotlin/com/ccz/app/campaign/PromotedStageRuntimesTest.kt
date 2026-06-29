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
    fun promotedStagePacksValidateAndAssemble() {
        val expected = listOf(
            PromotedStageRuntimes.QuyangSiege to Triple("legacy_stage_2", "legacy_stage_2", "legacy_stage_2_map"),
            PromotedStageRuntimes.ShimenAttack to Triple("legacy_stage_3", "legacy_stage_3", "legacy_stage_3_map"),
            PromotedStageRuntimes.SishuiPassOne to Triple("legacy_stage_4", "legacy_stage_4", "legacy_stage_4_map"),
        )

        expected.forEach { (runtime, ids) ->
            assertEquals(emptyList<Any>(), ContentValidator.validate(runtime.battlePack()))
            assertEquals(ids.first, runtime.stageId)
            assertEquals(ids.second, runtime.battleScriptId)
            assertEquals(ids.third, runtime.mapId)
            assertNull(runtime.introScriptOrNull())
        }
    }

    @Test
    fun promotedStagesDeployRealLegacyRostersPlusDefaultPlayerParty() {
        assertRoster(PromotedStageRuntimes.QuyangSiege, expectedTotal = 43, expectedEnemies = 29, expectedAllies = 11)
        assertRoster(PromotedStageRuntimes.ShimenAttack, expectedTotal = 72, expectedEnemies = 51, expectedAllies = 18)
        assertRoster(PromotedStageRuntimes.SishuiPassOne, expectedTotal = 52, expectedEnemies = 39, expectedAllies = 10)
    }

    @Test
    fun promotedStagesUseRealMapsAndDefaultObjectives() {
        assertMapAndObjectives(PromotedStageRuntimes.QuyangSiege, expectedWidth = 21, expectedHeight = 20)
        assertMapAndObjectives(PromotedStageRuntimes.ShimenAttack, expectedWidth = 28, expectedHeight = 24)
        assertMapAndObjectives(PromotedStageRuntimes.SishuiPassOne, expectedWidth = 19, expectedHeight = 20)
    }

    private fun assertRoster(runtime: BundledBattleRuntime, expectedTotal: Int, expectedEnemies: Int, expectedAllies: Int) {
        val state = runtime.initialState()

        assertEquals(expectedTotal, state.units.size)
        assertEquals(setOf(Faction.PLAYER, Faction.ALLY, Faction.ENEMY), state.units.values.map { it.faction }.toSet())
        assertTrue("default player trio is present", listOf("hero_1", "hero_2", "hero_3").all { it in state.units })
        assertEquals(expectedEnemies, state.units.values.count { it.faction == Faction.ENEMY })
        assertEquals(expectedAllies, state.units.values.count { it.faction == Faction.ALLY })
        assertTrue("everyone has a basic attack loadout", runtime.context().loadouts.values.all { it == listOf("skill_1") })
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, runtime.script()))
    }

    private fun assertMapAndObjectives(runtime: BundledBattleRuntime, expectedWidth: Int, expectedHeight: Int) {
        val map = runtime.context().map

        assertEquals(expectedWidth, map.width)
        assertEquals(expectedHeight, map.height)
        assertTrue("real map has varied terrain", (0 until map.width).flatMap { x ->
            (0 until map.height).map { y -> map.tileAt(com.ccz.core.model.Pos(x, y)).terrainId }
        }.toSet().size > 1)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), runtime.script().win)
        assertEquals(listOf(WinLoseCondition.ProtectAlive("hero_1")), runtime.script().lose)
    }
}
