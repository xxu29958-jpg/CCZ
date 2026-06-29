package com.ccz.app.campaign

import com.ccz.contentpack.ContentValidator
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.Gameplay
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotedStageRuntimesTest {
    @Test
    fun promotedStagePacksValidateAndAssemble() {
        PromotedStageExpectations.all.forEach { expected ->
            val runtime = expected.runtime

            assertEquals(emptyList<Any>(), ContentValidator.validate(runtime.battlePack()))
            assertEquals(expected.stageId, runtime.stageId)
            assertEquals(expected.stageId, runtime.battleScriptId)
            assertEquals("${expected.stageId}_map", runtime.mapId)
            assertEquals(expected.deferred, runtime.battlePack().events.deferredDeployments.size)
            assertNull(runtime.introScriptOrNull())
        }
    }

    @Test
    fun promotedStagesDeployRealLegacyRostersPlusDefaultPlayerParty() {
        PromotedStageExpectations.all.forEach(::assertRoster)
    }

    @Test
    fun promotedStagesUseRealMapsAndDefaultObjectives() {
        PromotedStageExpectations.all.forEach(::assertMapAndObjectives)
    }

    private fun assertRoster(expected: PromotedStageExpectation) {
        val runtime = expected.runtime
        val state = runtime.initialState()

        assertEquals(expected.initialUnits, state.units.size)
        assertEquals(expected.factions, state.units.values.map { it.faction }.toSet())
        assertTrue("default player trio is present", listOf("hero_1", "hero_2", "hero_3").all { it in state.units })
        assertEquals(expected.enemies, state.units.values.count { it.faction == Faction.ENEMY })
        assertEquals(expected.allies, state.units.values.count { it.faction == Faction.ALLY })
        assertDeferredUnitsStayOffMap(runtime)
        assertTrue("everyone has a basic attack loadout", runtime.context().loadouts.values.all { it == listOf("skill_1") })
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, runtime.script()))
    }

    private fun assertDeferredUnitsStayOffMap(runtime: BundledBattleRuntime) {
        runtime.battlePack().events.deferredDeployments.forEach { deferred ->
            assertTrue("${deferred.unit} stays available as reserve", deferred.unit in runtime.scriptContext().reserves)
            assertFalse("${deferred.unit} is not an opening unit", deferred.unit in runtime.initialState().units)
        }
    }

    private fun assertMapAndObjectives(expected: PromotedStageExpectation) {
        val runtime = expected.runtime
        val map = runtime.context().map

        assertEquals(expected.width, map.width)
        assertEquals(expected.height, map.height)
        assertTrue("real map has expected terrain variety", terrainKinds(runtime) >= expected.minTerrainKinds)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), runtime.script().win)
        assertEquals(expected.protectUnits.map(WinLoseCondition::ProtectAlive), runtime.script().lose)
    }

    private fun terrainKinds(runtime: BundledBattleRuntime): Int {
        val map = runtime.context().map
        return (0 until map.width).flatMap { x ->
            (0 until map.height).map { y -> map.tileAt(Pos(x, y)).terrainId }
        }.toSet().size
    }
}
