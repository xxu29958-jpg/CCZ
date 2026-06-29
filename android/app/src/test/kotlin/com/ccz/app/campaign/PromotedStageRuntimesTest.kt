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
        assertEquals(expected.protectUnits.map(WinLoseCondition::ProtectAlive), runtime.script().lose)
    }

    private data class PromotedStageExpectation(
        val runtime: BundledBattleRuntime,
        val initialUnits: Int,
        val enemies: Int,
        val allies: Int,
        val width: Int,
        val height: Int,
        val deferred: Int = 0,
        val protectUnits: List<String> = listOf("hero_1"),
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
            stage("legacy_stage_16", initialUnits = 38, enemies = 32, allies = 3, width = 28, height = 20),
            stage("legacy_stage_17", initialUnits = 42, enemies = 33, allies = 6, width = 22, height = 20),
            stage("legacy_stage_18", initialUnits = 70, enemies = 59, allies = 8, width = 26, height = 30),
            stage("legacy_stage_19", initialUnits = 23, enemies = 20, allies = 0, width = 22, height = 18),
            stage(
                "legacy_stage_21",
                initialUnits = 27,
                enemies = 24,
                allies = 0,
                width = 20,
                height = 30,
                protectUnits = listOf("hero_3"),
            ),
            stage(
                "legacy_stage_23",
                initialUnits = 51,
                enemies = 36,
                allies = 12,
                width = 22,
                height = 24,
                protectUnits = listOf("hero_2"),
            ),
            stage("legacy_stage_24", initialUnits = 52, enemies = 39, allies = 10, width = 20, height = 35),
            stage("legacy_stage_29", initialUnits = 61, enemies = 58, allies = 0, width = 40, height = 24),
            stage("legacy_stage_30", initialUnits = 81, enemies = 78, allies = 0, width = 33, height = 34),
            stage("legacy_stage_31", initialUnits = 30, enemies = 25, allies = 2, width = 24, height = 20),
            stage("legacy_stage_34", initialUnits = 50, enemies = 39, allies = 8, width = 24, height = 36),
            stage("legacy_stage_35", initialUnits = 60, enemies = 57, allies = 0, width = 24, height = 34),
            stage("legacy_stage_36", initialUnits = 22, enemies = 19, allies = 0, width = 24, height = 28),
            stage("legacy_stage_37", initialUnits = 55, enemies = 52, allies = 0, width = 32, height = 20),
            stage("legacy_stage_38", initialUnits = 45, enemies = 42, allies = 0, width = 20, height = 26),
            stage(
                "legacy_stage_39",
                initialUnits = 42,
                enemies = 39,
                allies = 0,
                width = 24,
                height = 20,
                protectUnits = listOf("hero_3"),
            ),
            stage("legacy_stage_40", initialUnits = 37, enemies = 32, allies = 2, width = 28, height = 22),
            stage(
                "legacy_stage_41",
                initialUnits = 63,
                enemies = 47,
                allies = 13,
                width = 28,
                height = 26,
                protectUnits = listOf("hero_1", "hero_77"),
            ),
            stage("legacy_stage_42", initialUnits = 58, enemies = 50, allies = 5, width = 20, height = 32),
            stage("legacy_stage_44", initialUnits = 38, enemies = 33, allies = 2, width = 20, height = 25),
            stage("legacy_stage_45", initialUnits = 27, enemies = 24, allies = 0, width = 19, height = 28),
            stage("legacy_stage_46", initialUnits = 63, enemies = 58, allies = 2, width = 34, height = 24),
            stage("legacy_stage_47", initialUnits = 50, enemies = 47, allies = 0, width = 22, height = 25),
            stage("legacy_stage_48", initialUnits = 41, enemies = 38, allies = 0, width = 20, height = 20),
            stage("legacy_stage_50", initialUnits = 72, enemies = 67, allies = 2, width = 20, height = 24),
            stage(
                "legacy_stage_51",
                initialUnits = 59,
                enemies = 53,
                allies = 3,
                width = 32,
                height = 18,
                protectUnits = listOf("hero_1", "hero_3"),
            ),
            stage(
                "legacy_stage_52",
                initialUnits = 55,
                enemies = 52,
                allies = 0,
                width = 20,
                height = 24,
                protectUnits = listOf("hero_3"),
            ),
            stage(
                "legacy_stage_53",
                initialUnits = 46,
                enemies = 42,
                allies = 1,
                width = 18,
                height = 24,
                protectUnits = listOf("hero_3"),
            ),
            stage("legacy_stage_54", initialUnits = 75, enemies = 69, allies = 3, width = 30, height = 31),
            stage("legacy_stage_55", initialUnits = 70, enemies = 58, allies = 9, width = 20, height = 30),
            stage("legacy_stage_56", initialUnits = 81, enemies = 78, allies = 0, width = 32, height = 28),
            stage(
                "legacy_stage_57",
                initialUnits = 79,
                enemies = 76,
                allies = 0,
                width = 28,
                height = 24,
                protectUnits = listOf("hero_1", "hero_3"),
            ),
            stage(
                "legacy_stage_58",
                initialUnits = 44,
                enemies = 41,
                allies = 0,
                width = 18,
                height = 24,
                protectUnits = listOf("hero_2"),
            ),
            stage(
                "legacy_stage_59",
                initialUnits = 40,
                enemies = 37,
                allies = 0,
                width = 24,
                height = 22,
                protectUnits = listOf("hero_2"),
            ),
            stage("legacy_stage_61", initialUnits = 68, enemies = 64, allies = 1, width = 21, height = 28),
            stage("legacy_stage_62", initialUnits = 68, enemies = 65, allies = 0, width = 28, height = 24),
        )

        private fun stage(
            stageId: String,
            initialUnits: Int,
            enemies: Int,
            allies: Int,
            width: Int,
            height: Int,
            deferred: Int = 0,
            protectUnits: List<String> = listOf("hero_1"),
        ): PromotedStageExpectation =
            PromotedStageExpectation(
                runtime = requireNotNull(PromotedStageRuntimes.all().singleOrNull { it.stageId == stageId }),
                initialUnits = initialUnits,
                enemies = enemies,
                allies = allies,
                width = width,
                height = height,
                deferred = deferred,
                protectUnits = protectUnits,
            )
    }
}
