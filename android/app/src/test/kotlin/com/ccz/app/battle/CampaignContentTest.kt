package com.ccz.app.battle

import com.ccz.contentpack.ContentValidator
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the app's built-in demo campaign is valid content and assembles (via CampaignAssembler, exercised
 * through DemoBattle) into the battle the reducer tests expect — the content-driven path end to end. The
 * detailed combat behavior is covered by BattleReducerTest, which now runs over this same assembled battle.
 */
class CampaignContentTest {
    @Test
    fun theDemoPackValidatesClean() {
        assertTrue(
            "the built-in campaign must pass content validation before assembly",
            ContentValidator.validate(CampaignContent.pack()).isEmpty(),
        )
    }

    @Test
    fun theDemoPackDecodesFromItsBundledJsonResource() {
        // Pins the real pipeline: the JSON resource is found, decodes (strict, fail-closed), and carries
        // the values the assembler depends on — including the wall's passable=false and the deployment ops.
        val pack = CampaignContent.pack()
        assertEquals("ccz_demo", pack.manifest.contentId)
        assertEquals(setOf("guan", "zhang", "foe", "foe2", "zhao"), pack.tables.units.map { it.id }.toSet())
        assertEquals("cao_cao_calm", pack.events.portraitSubjects.single().portrait)
        assertEquals(CampaignContent.INTRO_SCRIPT_ID, pack.events.rScripts.single().id)
        assertEquals(
            "the intro R-script keeps the demo branch exercise in content",
            15,
            pack.events.rScripts.single().ops.size,
        )
        assertFalse("the wall terrain decodes as impassable", pack.tables.terrain.first { it.id == "wall" }.passable)
        assertEquals("the battle script deploys four units via pre ops", 4, pack.events.sScripts.first().pre.size)
    }

    @Test
    fun theBattleDeploysTheDemoRosterAtExpectedTiles() {
        val state = DemoBattle.initialState()
        assertEquals(setOf("guan", "zhang", "foe", "foe2"), state.units.keys)
        assertEquals(Pos(1, 2), state.units.getValue("guan").pos)
        assertEquals(240, state.units.getValue("guan").hp) // deployed at full hp from the reserve
        assertEquals(Faction.PLAYER, state.units.getValue("guan").faction)
        assertEquals(Pos(4, 2), state.units.getValue("zhang").pos)
        assertEquals(Pos(5, 2), state.units.getValue("foe").pos)
        assertEquals(Faction.ENEMY, state.units.getValue("foe").faction)
        assertEquals(Pos(1, 4), state.units.getValue("foe2").pos)
        assertEquals(1, state.turn)
        assertEquals(Faction.PLAYER, state.active)
    }

    @Test
    fun theVeteranReserveIsBudgetedByLevelAndGrade() {
        // Zhao is a veteran cavalry reserve (level 10, grade 2) NOT deployed by any pre-op — so he is
        // absent from the deployed battle (proven above) but present as an off-map reserve template,
        // budgeted through the real JSON -> loader -> CampaignAssembler path. Cavalry growth is +5 atk /
        // +2 def / +1 res / +14 hp per level; grade 2 = the 140% tier. Over (10-1) levels:
        //   atk 110 + 5*9*140/100 = 173, def 85 + 2*9*140/100 = 110, res 55 + 1*9*140/100 = 67,
        //   hp 230 + 14*9*140/100 = 406. mat has no growth weight, so it stays 30.
        val state = DemoBattle.initialState()
        assertFalse("the veteran reserve is not auto-deployed onto the field", "zhao" in state.units)

        val zhao = DemoBattle.scriptContext().reserves.getValue("zhao")
        assertEquals(173, zhao.stats.atk)
        assertEquals(110, zhao.stats.def)
        assertEquals(30, zhao.stats.mat)
        assertEquals(67, zhao.stats.res)
        assertEquals(406, zhao.hpMax)
        assertEquals("a reserve enters at full hp", 406, zhao.hp)

        // The same cavalry growth is inert for a level-1 unit (growth scales by level-1 = 0), so Guan's
        // panel stays his base — which is why adding growth left the playable battle byte-identical.
        val guan = DemoBattle.scriptContext().reserves.getValue("guan")
        assertEquals("growth only bites above level 1: Guan keeps his base atk", 120, guan.stats.atk)
        assertEquals("Guan keeps his base hp", 240, guan.hpMax)
    }

    @Test
    fun theAssembledMapCarriesWallPassabilityAndForestCost() {
        val map = DemoBattle.context().map
        assertEquals(7, map.width)
        assertEquals(6, map.height)
        assertFalse("the wall tile is impassable in the assembled map", map.tileAt(Pos(3, 1)).passable)
        assertEquals("forest costs 2 to enter", 2, map.tileAt(Pos(2, 4)).moveCost)
        assertTrue("plain is passable", map.tileAt(Pos(0, 0)).passable)
    }

    @Test
    fun theAssembledContextExposesPerUnitLoadoutsAndSkillRanges() {
        val context = DemoBattle.context()
        assertEquals(listOf("strike", "spear"), context.loadouts["guan"])
        assertEquals(listOf("bow"), context.loadouts["foe"])
        assertEquals(RangeSpec(1, 2), context.skills.getValue("spear").range)
        assertEquals(RangeSpec(2, 3), context.skills.getValue("bow").range)
    }

    @Test
    fun theBattleScriptJudgesRoutAndProtagonist() {
        val script = DemoBattle.script()
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), script.win)
        assertEquals(listOf(WinLoseCondition.ProtectAlive("guan")), script.lose)
    }
}
