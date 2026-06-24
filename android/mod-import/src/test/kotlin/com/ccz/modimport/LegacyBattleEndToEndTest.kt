package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.Command
import com.ccz.core.battle.Gameplay
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end proof that decrypted legacy tables drive a real, playable battle: ported classes/units/
 * skills/terrain + a synthesized map/script → engine loader → validator → CampaignAssembler →
 * game-core combat. No copyrighted data: the legacy tables here are synthetic but exercise the same
 * mappers and pipeline as the real APK export.
 */
class LegacyBattleEndToEndTest {
    private val meta = PackMeta(contentId = "trssgshz", contentVersion = "0.1.0", mod = "trssgshz", entry = "skirmish")

    private fun sources(): LegacyTableSources = LegacyTableSources(
        dicJob = """[{"jobid":1,"name":"群雄","move":3}]""",
        dicSkill = """[{"skid":1,"name":"普攻","type":0,"hurt_num":50,"mp_consume":0}]""",
        dicHero = """[
            {"hid":1,"name":"刘备","jobid":1,"atk":120,"def":40,"ints":30,"burst":20,"level":1,"hp":200,"skill":1},
            {"hid":2,"name":"张飞","jobid":1,"atk":140,"def":50,"ints":10,"burst":20,"level":1,"hp":220,"skill":1},
            {"hid":3,"name":"曹兵","jobid":1,"atk":60,"def":30,"ints":10,"burst":10,"level":1,"hp":120,"skill":1},
            {"hid":4,"name":"曹将","jobid":1,"atk":90,"def":40,"ints":20,"burst":20,"level":1,"hp":160,"skill":1}
        ]""",
        mapTerrain = """[{"mapid":1,"name":"平原"}]""",
    )

    private val spec = BattleSpec(
        battleId = "skirmish",
        width = 6,
        height = 3,
        terrainId = "terrain_1",
        protect = "hero_1",
        placements = listOf(
            Placement("hero_1", x = 2, y = 1),
            Placement("hero_2", x = 0, y = 1),
            Placement("hero_3", x = 3, y = 1, enemy = true),
            Placement("hero_4", x = 5, y = 1, enemy = true),
        ),
    )

    @Test
    fun portedDataAssemblesIntoAValidLoadableBattle() {
        val content = LegacyBattleBuilder.load(meta, sources(), spec)

        assertEquals(emptyList(), ContentValidator.validate(content), "ported pack must pass reference validation")

        val setup = CampaignAssembler.assemble(content, spec.battleId, LegacyBattleBuilder.mapId(spec.battleId))
        val units = setup.initialState.units
        assertEquals(setOf("hero_1", "hero_2", "hero_3", "hero_4"), units.keys)
        assertEquals(Faction.PLAYER, units.getValue("hero_1").faction)
        assertEquals(Faction.ENEMY, units.getValue("hero_3").faction, "spawn faction override deploys the enemy side")
        assertEquals(Pos(3, 1), units.getValue("hero_3").pos)
        assertEquals(200, units.getValue("hero_1").hpMax, "ported hp survives into the live battle")
    }

    @Test
    fun portedUnitsCanFightThroughGameCore() {
        val content = LegacyBattleBuilder.load(meta, sources(), spec)
        val setup = CampaignAssembler.assemble(content, spec.battleId, LegacyBattleBuilder.mapId(spec.battleId))
        val state = setup.initialState
        val context = setup.context

        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(state, setup.script), "battle starts live")
        assertTrue("skill_1" in Gameplay.legalSkills(state, "hero_1", context), "ported melee skill is usable")
        assertTrue("hero_3" in Gameplay.legalTargets(state, "hero_1", "skill_1", context), "adjacent enemy in range")

        val outcome = Gameplay.submit(state, Command.Attack("hero_1", "hero_3", "skill_1"), context)
        val accepted = outcome as Gameplay.Outcome.Accepted
        assertTrue(
            accepted.resolution.state.unit("hero_3").hp < state.unit("hero_3").hp,
            "the attack damages the ported enemy — ported data drives real combat",
        )
    }
}
