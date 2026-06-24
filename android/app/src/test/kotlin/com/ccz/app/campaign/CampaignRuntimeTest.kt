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
 * The single real-campaign runtime (大兴山): two bundled packs (generated battle + authored intro)
 * assembled into the inputs game-core needs. Pins that both packs decode + validate, the real roster
 * deploys with growth/grade-budgeted panels on real terrain with live cover, the intro is the matching
 * cutscene, and the two packs share one campaign content version (ADR 0007).
 */
class CampaignRuntimeTest {
    @Test
    fun bothCampaignPacksValidateClean() {
        assertEquals(emptyList<Any>(), ContentValidator.validate(CampaignRuntime.battlePack()))
        assertEquals(emptyList<Any>(), ContentValidator.validate(CampaignRuntime.scenarioPack()))
    }

    @Test
    fun theTwoPacksShareOneCampaignContentVersion() {
        // ADR 0007: battle pack + scenario pack ship as one campaign release under a shared content_version.
        assertEquals(CampaignRuntime.battlePack().manifest.contentVersion, CampaignRuntime.scenarioPack().manifest.contentVersion)
        assertEquals(CampaignRuntime.battlePack().manifest.contentVersion, CampaignRuntime.contentVersion())
    }

    @Test
    fun theRealRosterDeploysWithRealSides() {
        val state = CampaignRuntime.initialState()
        assertEquals(setOf("hero_1", "hero_2", "hero_3", "hero_226", "hero_227"), state.units.keys)
        assertEquals(Faction.PLAYER, state.units.getValue("hero_2").faction) // 关羽
        assertEquals(Faction.ENEMY, state.units.getValue("hero_226").faction) // 程远志 (spawn faction override)
        assertEquals(Faction.ENEMY, state.units.getValue("hero_227").faction) // 邓茂
    }

    @Test
    fun theBattleIsFoughtOnRealCroppedTerrain() {
        val map = CampaignRuntime.context().map
        assertEquals(8, map.width)
        assertEquals(7, map.height)
        val terrains = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)).terrainId } }.toSet()
        assertTrue("the board carries real legacy terrain (荒地/山地/树林)", terrains.any { it in setOf("terrain_3", "terrain_4", "terrain_5") })
        assertTrue("a real map has more than one terrain type", terrains.size > 1)
    }

    @Test
    fun terrainCoverIsLiveOnTheBattleMap() {
        val map = CampaignRuntime.context().map
        val tiles = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)) } }
        assertTrue("some tile grants defender cover (def/avoid)", tiles.any { it.defBonus > 0 || it.avoidBonus > 0 })
    }

    @Test
    fun everyEnemyAdvancesOnItsAutoDrivenTurn() {
        val reducer = BattleReducer(CampaignRuntime.context(), CampaignRuntime.script(), CampaignRuntime.scriptContext())
        val start = reducer.initial(CampaignRuntime.initialState())
        val after = reducer.endTurn(start)
        start.state.units.values.filter { it.faction == Faction.ENEMY }.forEach { enemy ->
            assertTrue(
                "enemy ${enemy.id} must be able to move on its turn (not stranded by terrain)",
                after.state.units.getValue(enemy.id).pos != enemy.pos,
            )
        }
    }

    @Test
    fun growthAndGradeBudgetRealHeroPanelsOnTheField() {
        val state = CampaignRuntime.initialState()
        // 关羽 — 裨将 +4 atk / +6 hp per level; level 8, grade 2 (140%): atk 137, hp 228.
        val guanyu = state.units.getValue("hero_2")
        assertEquals(137, guanyu.stats.atk)
        assertEquals(228, guanyu.hpMax)
        // 邓茂 — 黄巾军 +3 atk / +5 hp per level; level 4, grade 0 (100%): atk 84, hp 155.
        val dengmao = state.units.getValue("hero_227")
        assertEquals(84, dengmao.stats.atk)
        assertEquals(155, dengmao.hpMax)
        assertTrue("the grade-2 veteran outscales the grade-0 rookie", guanyu.stats.atk > dengmao.stats.atk)
    }

    @Test
    fun theIntroIsTheDaxingshanCutsceneWithABranchingChoice() {
        val script = CampaignRuntime.introScript()
        assertEquals("daxingshan_intro", script.id)
        assertTrue("the authored 大兴山 intro offers a march-plan choice", script.ops.any { it is ScenarioOp.Choice })
        assertTrue("it is a multi-op authored cutscene", script.ops.size > 5)
    }
}
