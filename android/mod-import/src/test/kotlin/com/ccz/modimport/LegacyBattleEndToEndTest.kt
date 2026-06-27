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
    fun realLegacyMapFillsTerrainCoverageAndAssembles() {
        // a real terrainMap grid references terrain ids beyond the catalog (only mapid 1 here):
        // ids 0 and 2 must auto-fill so coverage validation passes without hand-curating the catalog.
        val terrainMap = """{"desc":"tm","map_width":3,"map_height":3,"map_value":[[1,1,2],[1,0,2],[1,1,2]]}"""
        val battle = MapBattleSpec(
            battleId = "rmap",
            mapId = "real_map",
            protect = "hero_1",
            placements = listOf(Placement("hero_1", 0, 0), Placement("hero_2", 2, 2, enemy = true)),
        )

        val content = LegacyBattleBuilder.loadOnMap(meta, sources(), terrainMap, battle)

        assertEquals(emptyList(), ContentValidator.validate(content), "auto-filled terrain coverage must validate")
        assertTrue(content.tables.terrain.map { it.id }.containsAll(listOf("terrain_0", "terrain_1", "terrain_2")))
        val map = content.tables.maps.single()
        assertEquals(3, map.size.width)
        assertEquals(3, map.size.height)

        val setup = CampaignAssembler.assemble(content, "rmap", "real_map")
        assertEquals(setOf("hero_1", "hero_2"), setup.initialState.units.keys, "both sides deploy on the real map")
        assertEquals(Faction.ENEMY, setup.initialState.units.getValue("hero_2").faction)
        assertEquals(BattleOutcome.ONGOING, Gameplay.outcome(setup.initialState, setup.script))
    }

    @Test
    fun scriptDerivedObjectivesOverrideTheSynthesizedDefaults() {
        // when a spec carries script-derived win/lose (LegacyObjectiveImporter output), the battle uses them
        // instead of the synthesized annihilate-enemies / protect default — here "kill the enemy 曹将 to win".
        val terrainMap = """{"desc":"tm","map_width":3,"map_height":3,"map_value":[[1,1,1],[1,1,1],[1,1,1]]}"""
        // protect="hero_4" is the FALLBACK lose (used only if no override) — kept deliberately different from the
        // overridden lose so the assertions distinguish "spec honored" from "default fired" on BOTH sides.
        val derived = MapBattleSpec(
            battleId = "obj",
            mapId = "om",
            protect = "hero_4",
            placements = listOf(Placement("hero_1", 0, 0), Placement("hero_4", 2, 2, enemy = true)),
            win = listOf(PackCondition(PackCondition.DEFEAT_UNIT, unit = "hero_4")),
            lose = listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1")),
        )
        val script = checkNotNull(LegacyBattleBuilder.buildBattleOnMap(meta, sources(), terrainMap, derived).events).sScripts.single()
        assertEquals(listOf(PackCondition(PackCondition.DEFEAT_UNIT, unit = "hero_4")), script.win, "win is the spec's, not the annihilate default")
        assertEquals(listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1")), script.lose, "lose is the spec's (hero_1), not the default protect(hero_4)")
        // the derived-objective pack still loads + validates end-to-end
        assertEquals(emptyList(), ContentValidator.validate(LegacyBattleBuilder.loadOnMap(meta, sources(), terrainMap, derived)))
    }

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
    fun deployLevelMakesGrowthAndGradeBudgetPortedHeroPanels() {
        // The capstone: a ported hero deployed above level 1 is budgeted by its class growth × quality
        // grade (ADR 0006). The ore gives no deploy level (all dic_hero level 1), so the BattleSpec
        // supplies it — the seam that makes the growth (#84) and grade (#87) levers visible for real
        // heroes. job 1 grows +5 atk / +10 hp per level; the hero's strength 100+100+90+60 = 350 forges
        // grade 2 (140% tier). Over (10-1) levels: atk 100 + 5*9*140/100 = 163, hp 200 + 10*9*140/100 = 326.
        val growthSources = LegacyTableSources(
            dicJob = """[{"jobid":1,"name":"群雄","move":3,"atk":5,"hp_up":10}]""",
            dicSkill = """[{"skid":1,"name":"普攻","type":0,"hurt_num":50,"mp_consume":0}]""",
            dicHero = """[{"hid":1,"name":"关羽","jobid":1,"atk":100,"def":100,"ints":90,"burst":60,"level":1,"hp":200,"skill":1}]""",
            mapTerrain = """[{"mapid":1,"name":"平原"}]""",
        )
        fun assembleAtLevel(level: Int) = CampaignAssembler.assemble(
            LegacyBattleBuilder.load(
                meta,
                growthSources,
                BattleSpec("lv", 3, 1, "terrain_1", "hero_1", listOf(Placement("hero_1", 0, 0, level = level))),
            ),
            "lv",
            LegacyBattleBuilder.mapId("lv"),
        ).initialState.units.getValue("hero_1")

        val veteran = assembleAtLevel(10)
        assertEquals(163, veteran.stats.atk, "level 10 × +5 atk growth × grade-2 140% over base 100")
        assertEquals(326, veteran.hpMax, "level 10 × +10 hp growth × grade-2 140% over base 200")

        // Same hero/class data; only the deploy level differs. Level 1 → growth×(1-1)=0 → base panel,
        // proving the deploy level is the unlock, not a change to the ported data.
        val rookie = assembleAtLevel(1)
        assertEquals(100, rookie.stats.atk, "level 1 stays at base atk")
        assertEquals(200, rookie.hpMax, "level 1 stays at base hp")
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
