package com.ccz.app.campaign

import com.ccz.app.battle.BattleReducer
import com.ccz.contentpack.ContentValidator
import com.ccz.core.event.ScenarioOp
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the bundled stage-1 campaign runtime: generic generated battle pack plus authored intro pack,
 * assembled into game-core inputs without app-side battle rules.
 */
class CampaignRuntimeTest {
    @Test
    fun bothCampaignPacksValidateClean() {
        assertEquals(emptyList<Any>(), ContentValidator.validate(CampaignRuntime.battlePack()))
        assertEquals(emptyList<Any>(), ContentValidator.validate(CampaignRuntime.scenarioPack()))
    }

    @Test
    fun theTwoPacksShareOneCampaignContentVersion() {
        assertEquals(CampaignRuntime.battlePack().manifest.contentVersion, CampaignRuntime.scenarioPack().manifest.contentVersion)
        assertEquals(CampaignRuntime.battlePack().manifest.contentVersion, CampaignRuntime.contentVersion())
    }

    @Test
    fun theFullRosterDeploysWithAllThreeFactionsAndDeferredMetadata() {
        val state = CampaignRuntime.initialState()
        assertEquals("opening deployment is 3 player + 22 enemy + 8 ally", 33, state.units.size)
        assertEquals(setOf(Faction.PLAYER, Faction.ALLY, Faction.ENEMY), state.units.values.map { it.faction }.toSet())
        assertEquals(Faction.PLAYER, state.units.getValue("hero_1").faction)
        assertEquals(Faction.ENEMY, state.units.getValue("hero_226").faction)
        assertEquals(Faction.ALLY, state.units.getValue("hero_650").faction)

        val loadouts = CampaignRuntime.context().loadouts
        assertTrue("generic stage packs grant every roster unit the generated basic attack", loadouts.values.all { it == listOf("skill_1") })
        assertTrue("script-referenced same-tile unit is not an opening unit", "hero_603" !in state.units)
        assertTrue("script-referenced same-tile unit stays available as a reserve", "hero_603" in CampaignRuntime.scriptContext().reserves)
        val deferred = CampaignRuntime.battlePack().events.deferredDeployments.single()
        assertEquals("hero_603", deferred.unit)
        assertEquals(Pos(19, 4), deferred.at)
        assertEquals(Faction.ENEMY, deferred.faction)
        assertEquals("legacy_actor_state_refs", deferred.source)
    }

    @Test
    fun theBattleIsFoughtOnTheFullRealMap() {
        val map = CampaignRuntime.context().map
        assertEquals(23, map.width)
        assertEquals(16, map.height)
        val terrains = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)).terrainId } }.toSet()
        assertTrue("the board carries legacy terrain ids", terrains.any { it in setOf("terrain_3", "terrain_4", "terrain_5") })
        assertTrue("a real map has more than one terrain type", terrains.size > 1)
    }

    @Test
    fun terrainCoverIsLiveOnTheBattleMap() {
        val map = CampaignRuntime.context().map
        val tiles = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)) } }
        assertTrue("some tile grants defender cover", tiles.any { it.defBonus > 0 || it.avoidBonus > 0 })
    }

    @Test
    fun theEnemyTurnTerminatesAndHandsBackToThePlayer() {
        val reducer = BattleReducer(CampaignRuntime.context(), CampaignRuntime.script(), CampaignRuntime.scriptContext())
        val start = reducer.initial(CampaignRuntime.initialState())
        val after = reducer.endTurn(start)
        assertEquals("the enemy turn terminates and returns control to the player", Faction.PLAYER, after.state.active)
        val enemies = start.state.units.values.filter { it.faction == Faction.ENEMY }
        assertTrue("at least one enemy advances on its turn", enemies.any { after.state.units.getValue(it.id).pos != it.pos })
    }

    @Test
    fun growthAndGradeBudgetRealHeroPanelsOnTheField() {
        val state = CampaignRuntime.initialState()
        val guanyu = state.units.getValue("hero_2")
        assertEquals(137, guanyu.stats.atk)
        assertEquals(228, guanyu.hpMax)
        val grunt = state.units.getValue("hero_226")
        assertTrue("the grade-2 veteran outscales a real low-level enemy", guanyu.stats.atk > grunt.stats.atk)
    }

    @Test
    fun theIntroIsTheDaxingshanCutsceneWithABranchingChoice() {
        val script = CampaignRuntime.introScript()
        assertEquals("daxingshan_intro", script.id)
        assertTrue("the authored intro offers a march-plan choice", script.ops.any { it is ScenarioOp.Choice })
        assertTrue("it is a multi-op authored cutscene", script.ops.size > 5)
    }
}
