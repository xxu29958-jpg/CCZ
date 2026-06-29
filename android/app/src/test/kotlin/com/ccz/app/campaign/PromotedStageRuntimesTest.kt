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
        expectations.forEach { expected ->
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
        expectations.forEach(::assertRoster)
    }

    @Test
    fun promotedStagesUseRealMapsAndDefaultObjectives() {
        expectations.forEach(::assertMapAndObjectives)
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
        assertTrue("real map has varied terrain", (0 until map.width).flatMap { x ->
            (0 until map.height).map { y -> map.tileAt(Pos(x, y)).terrainId }
        }.toSet().size > 1)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), runtime.script().win)
        assertEquals(listOf(WinLoseCondition.ProtectAlive("hero_1")), runtime.script().lose)
    }

    private data class PromotedStageExpectation(
        val runtime: BundledBattleRuntime,
        val initialUnits: Int,
        val enemies: Int,
        val allies: Int,
        val width: Int,
        val height: Int,
        val deferred: Int = 0,
    ) {
        val stageId: String = runtime.stageId
        val factions: Set<Faction> = buildSet {
            add(Faction.PLAYER)
            if (allies > 0) add(Faction.ALLY)
            if (enemies > 0) add(Faction.ENEMY)
        }
    }

    private companion object {
        private val expectations = listOf(
            stage("legacy_stage_2", initialUnits = 43, enemies = 29, allies = 11, width = 21, height = 20),
            stage("legacy_stage_3", initialUnits = 72, enemies = 51, allies = 18, width = 28, height = 24),
            stage("legacy_stage_4", initialUnits = 52, enemies = 39, allies = 10, width = 19, height = 20),
            stage("legacy_stage_5", initialUnits = 66, enemies = 49, allies = 14, width = 20, height = 28),
            stage("legacy_stage_6", initialUnits = 58, enemies = 40, allies = 15, width = 35, height = 16),
            stage("legacy_stage_8", initialUnits = 70, enemies = 53, allies = 14, width = 22, height = 21, deferred = 1),
            stage("legacy_stage_9", initialUnits = 46, enemies = 43, allies = 0, width = 24, height = 20),
            stage("legacy_stage_10", initialUnits = 92, enemies = 78, allies = 11, width = 24, height = 20),
            stage("legacy_stage_11", initialUnits = 47, enemies = 44, allies = 0, width = 24, height = 28),
            stage("legacy_stage_12", initialUnits = 78, enemies = 71, allies = 4, width = 24, height = 20),
            stage("legacy_stage_13", initialUnits = 65, enemies = 59, allies = 3, width = 36, height = 16),
            stage("legacy_stage_14", initialUnits = 96, enemies = 79, allies = 14, width = 24, height = 28),
        )

        private fun stage(
            stageId: String,
            initialUnits: Int,
            enemies: Int,
            allies: Int,
            width: Int,
            height: Int,
            deferred: Int = 0,
        ): PromotedStageExpectation =
            PromotedStageExpectation(
                runtime = requireNotNull(PromotedStageRuntimes.all().singleOrNull { it.stageId == stageId }),
                initialUnits = initialUnits,
                enemies = enemies,
                allies = allies,
                width = width,
                height = height,
                deferred = deferred,
            )
    }
}
