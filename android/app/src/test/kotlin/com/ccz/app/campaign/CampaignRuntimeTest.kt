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
    fun theFullRosterDeploysWithAllThreeFactionsAndEffectSkills() {
        val state = CampaignRuntime.initialState()
        assertEquals("the faithful full stage deploys the whole dispatch roster (3 player + 22 enemy + 8 ally)", 33, state.units.size)
        assertEquals(
            "all three sides take the field",
            setOf(Faction.PLAYER, Faction.ALLY, Faction.ENEMY),
            state.units.values.map { it.faction }.toSet(),
        )
        assertEquals(Faction.PLAYER, state.units.getValue("hero_1").faction) // 刘备 protagonist
        assertEquals(Faction.ENEMY, state.units.getValue("hero_226").faction) // 程远志 (spawn faction override)
        assertEquals(Faction.ALLY, state.units.getValue("hero_650").faction) // a dispatched allied NPC
        // the player trio keeps its hand-authored effect skills (the reconciliation survived into the live runtime)
        val loadouts = CampaignRuntime.context().loadouts
        assertTrue("刘备 keeps heal+cleanse", loadouts.getValue("hero_1").containsAll(listOf("skill_2", "skill_8")))
        assertTrue("关羽 keeps debuff+silence", loadouts.getValue("hero_2").containsAll(listOf("skill_4", "skill_5")))
        assertTrue("张飞 keeps buff+stun", loadouts.getValue("hero_3").containsAll(listOf("skill_3", "skill_6")))
    }

    @Test
    fun theBattleIsFoughtOnTheFullRealMap() {
        val map = CampaignRuntime.context().map
        assertEquals("the full uncropped terrainMap_1", 23, map.width)
        assertEquals(16, map.height)
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
    fun theEnemyTurnTerminatesAndHandsBackToThePlayer() {
        val reducer = BattleReducer(CampaignRuntime.context(), CampaignRuntime.script(), CampaignRuntime.scriptContext())
        val start = reducer.initial(CampaignRuntime.initialState())
        val after = reducer.endTurn(start)
        // 22 enemies auto-drive then the loop hands control back: a crowded big map must still TERMINATE (active
        // becomes PLAYER again) and the AI must not be globally stranded (some enemy advances toward a foe — a
        // back-rank unit may be blocked by its own line, so "every enemy moves" no longer holds, but "some does").
        assertEquals("the enemy turn terminates and returns control to the player", Faction.PLAYER, after.state.active)
        val enemies = start.state.units.values.filter { it.faction == Faction.ENEMY }
        assertTrue("at least one enemy advances on its turn", enemies.any { after.state.units.getValue(it.id).pos != it.pos })
    }

    @Test
    fun growthAndGradeBudgetRealHeroPanelsOnTheField() {
        val state = CampaignRuntime.initialState()
        // 关羽 — 裨将 +4 atk / +6 hp per level; level 8, grade 2 (140%): atk 137, hp 228 (deploy level preserved).
        val guanyu = state.units.getValue("hero_2")
        assertEquals(137, guanyu.stats.atk)
        assertEquals(228, guanyu.hpMax)
        // a real dispatched enemy at its low LEGACY level — the leveled grade-2 veteran outscales it, proving
        // growth × grade still bites for the real full roster, not just the curated subset.
        val grunt = state.units.getValue("hero_226") // 程远志 (重骑兵), real deploy level 2
        assertTrue("the grade-2 veteran outscales a real low-level enemy", guanyu.stats.atk > grunt.stats.atk)
    }

    @Test
    fun theIntroIsTheDaxingshanCutsceneWithABranchingChoice() {
        val script = CampaignRuntime.introScript()
        assertEquals("daxingshan_intro", script.id)
        assertTrue("the authored 大兴山 intro offers a march-plan choice", script.ops.any { it is ScenarioOp.Choice })
        assertTrue("it is a multi-op authored cutscene", script.ops.size > 5)
    }
}
