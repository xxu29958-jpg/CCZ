package com.ccz.app.battle

import com.ccz.contentpack.ContentValidator
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
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
    fun theBattleIsFoughtOnRealCroppedTerrain() {
        // An 8×7 crop of the real terrainMap_1 (大兴山) — genuine legacy terrain (荒地/山地/树林), not a flat
        // synthetic field; proves the real map layout reaches the assembled board.
        val map = RealBattle.context().map
        assertEquals(8, map.width)
        assertEquals(7, map.height)
        val terrains = (0 until map.width)
            .flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)).terrainId } }
            .toSet()
        assertTrue("the board carries real legacy terrain (荒地/山地/树林)", terrains.any { it in setOf("terrain_3", "terrain_4", "terrain_5") })
        assertTrue("a real map has more than one terrain type", terrains.size > 1)
    }

    @Test
    fun everyEnemyAdvancesOnItsAutoDrivenTurn() {
        // Regression guard against stranded units on real terrain: the roster spawns 3 rows apart (out of
        // the basic attack's reach-1), so the aggressive AI must MOVE every enemy toward the players on the
        // enemy turn. A unit boxed in by impassable terrain (e.g. a move-1 黄巾军 ringed by 山地, cost 2)
        // could only Wait — its position would not change, failing this test.
        val reducer = BattleReducer(RealBattle.context(), RealBattle.script(), RealBattle.scriptContext())
        val start = reducer.initial(RealBattle.initialState())
        val after = reducer.endTurn(start) // player ends → AI drives the whole enemy turn → back to player
        start.state.units.values.filter { it.faction == Faction.ENEMY }.forEach { enemy ->
            assertTrue(
                "enemy ${enemy.id} must be able to move on its turn (not stranded by terrain)",
                after.state.units.getValue(enemy.id).pos != enemy.pos,
            )
        }
    }

    @Test
    fun terrainCoverIsLiveOnTheBattleMap() {
        // 大兴山 terrain (荒地/山地/树林) carries designed defender bonuses (def/avoid) through the importer →
        // loader → assembler into the engine MapTile, so positioning matters (e.g. 山地 grants +def/+avoid).
        // Also proves the def_bonus/avoid_bonus wire keys decode (a bad key would fail the strict loader).
        val map = RealBattle.context().map
        val tiles = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map.tileAt(Pos(x, y)) } }
        assertTrue("some tile grants defender cover (def/avoid)", tiles.any { it.defBonus > 0 || it.avoidBonus > 0 })
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
