package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassCombat
import com.ccz.contentpack.ClassDef
import com.ccz.contentpack.ClassMovement
import com.ccz.contentpack.ContentManifest
import com.ccz.contentpack.ContentTables
import com.ccz.contentpack.EventTables
import com.ccz.contentpack.MapDef
import com.ccz.contentpack.MapSize
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.RangeDef
import com.ccz.contentpack.SkillDef
import com.ccz.contentpack.SkillUse
import com.ccz.contentpack.SourceInfo
import com.ccz.contentpack.TerrainDef
import com.ccz.contentpack.UnitDef
import com.ccz.contentpack.UnitIdentity
import com.ccz.contentpack.UnitLoadout
import com.ccz.contentpack.UnitProfile
import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.SScript
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CampaignAssemblerTest {
    // A deliberately NON-SQUARE map (4 wide x 3 tall) so an x/y transpose in the assembler would be
    // caught: a wall (impassable) at (1,1) and a wood (cost 2) at the asymmetric (3,2); plain elsewhere.
    private val standardTiles = listOf(
        listOf("plain", "plain", "plain", "plain"),
        listOf("plain", "wall", "plain", "plain"),
        listOf("plain", "plain", "plain", "wood"),
    )

    private fun content(
        tiles: List<List<String>> = standardTiles,
        pre: List<BattleOp> = listOf(BattleOp.SpawnUnit("hero", Pos(0, 0)), BattleOp.SpawnUnit("foe", Pos(2, 2))),
        mid: List<BattleTrigger> = emptyList(),
        win: List<WinLoseCondition> = listOf(WinLoseCondition.AnnihilateEnemies),
        terrain: List<TerrainDef> = terrainDefs(),
    ): NativeContent = NativeContent(
        manifest = ContentManifest(
            nativeFormatVersion = "1",
            contentId = "test",
            contentVersion = "0.1.0",
            source = SourceInfo(mod = "test"),
            entry = "b",
        ),
        tables = ContentTables(
            classes = classDefs(),
            units = unitDefs(),
            terrain = terrain,
            skills = skillDefs(),
            items = emptyList(),
            // Size derived from the tiles so every variant stays self-consistent (height x width).
            maps = listOf(MapDef(id = "m", size = MapSize(tiles.first().size, tiles.size), tileset = "t", tiles = tiles)),
        ),
        events = EventTables(sScripts = listOf(battleScript(pre = pre, mid = mid, win = win))),
    )

    private fun terrainDefs(): List<TerrainDef> = listOf(
        TerrainDef(id = "plain", name = "Plain", moveCost = 1),
        TerrainDef(id = "wall", name = "Wall", moveCost = 1, passable = false),
        TerrainDef(id = "wood", name = "Wood", moveCost = 2),
    )

    private fun classDefs(): List<ClassDef> = listOf(
        ClassDef(
            id = "cav",
            name = "Cavalry",
            movement = ClassMovement(moveType = "horse", move = 5),
            combat = ClassCombat(counters = mapOf("inf" to "FAVOR")),
        ),
        ClassDef(id = "inf", name = "Infantry", movement = ClassMovement(moveType = "foot", move = 4)),
    )

    private fun skillDefs(): List<SkillDef> = listOf(
        skillDef("hit", powerCoeff = 100, min = 1, max = 1),
        skillDef("shot", powerCoeff = 80, min = 2, max = 3),
    )

    private fun skillDef(id: String, powerCoeff: Int, min: Int, max: Int): SkillDef = SkillDef(
        id = id,
        name = id,
        kind = DamageKind.PHYSICAL,
        powerCoeff = powerCoeff,
        use = SkillUse(range = RangeDef(min, max), area = "single", targeting = "enemy"),
    )

    private fun unitDefs(): List<UnitDef> = listOf(
        unitDef("hero", "cav", Faction.PLAYER, hpMax = 100, skills = listOf("hit")),
        unitDef("foe", "inf", Faction.ENEMY, hpMax = 80, skills = listOf("shot")),
        // "extra" declares no loadout — it must be omitted from the loadouts map (unconstrained default),
        // not locked out, and it is not deployed (no pre-op references it), so it stays an off-map reserve.
        unitDef("extra", "inf", Faction.ENEMY, hpMax = 50, skills = emptyList()),
    )

    private fun unitDef(id: String, classId: String, faction: Faction, hpMax: Int, skills: List<String>): UnitDef =
        UnitDef(
            identity = UnitIdentity(id = id, name = "Name-$id", classId = classId, faction = faction),
            profile = UnitProfile(level = 1, hpMax = hpMax, stats = CombatStats(atk = 50, def = 30, mat = 10, res = 20)),
            loadout = UnitLoadout(skills = skills),
        )

    private fun battleScript(
        pre: List<BattleOp>,
        mid: List<BattleTrigger> = emptyList(),
        win: List<WinLoseCondition> = listOf(WinLoseCondition.AnnihilateEnemies),
        lose: List<WinLoseCondition> = emptyList(),
        post: List<BattleOp> = emptyList(),
    ): SScript = SScript(
        id = "b",
        win = win,
        lose = lose,
        pre = pre,
        mid = mid,
        post = post,
    )

    @Test
    fun assembleBuildsMapWithTerrainCostAndPassability() {
        val map = CampaignAssembler.assemble(content(), "b", "m").context.map
        assertEquals(4, map.width)
        assertEquals(3, map.height)
        assertTrue(map.tileAt(Pos(0, 0)).passable, "plain is passable")
        assertEquals(1, map.tileAt(Pos(0, 0)).moveCost)
        assertFalse(map.tileAt(Pos(1, 1)).passable, "the wall tile carries content passability through to the engine")
        assertEquals("wall", map.tileAt(Pos(1, 1)).terrainId)
        // (3,2): asymmetric x!=y, valid only on a 4x3 map — a transpose would read the wrong tile or go OOB.
        assertEquals(2, map.tileAt(Pos(3, 2)).moveCost, "wood move cost survives assembly at an asymmetric tile")
        assertEquals("wood", map.tileAt(Pos(3, 2)).terrainId)
    }

    @Test
    fun assembleMapsClassesWithMoveAndCounters() {
        val classes = CampaignAssembler.assemble(content(), "b", "m").context.classes
        assertEquals(5, classes.getValue("cav").move)
        assertEquals("horse", classes.getValue("cav").moveType)
        assertEquals(mapOf("inf" to CounterRelation.FAVOR), classes.getValue("cav").counters)
        assertEquals(emptyMap<String, CounterRelation>(), classes.getValue("inf").counters)
    }

    @Test
    fun assembleMapsSkillsWithRange() {
        val skills = CampaignAssembler.assemble(content(), "b", "m").context.skills
        assertEquals(RangeSpec(1, 1), skills.getValue("hit").range)
        assertEquals(100, skills.getValue("hit").powerCoeff)
        assertEquals(DamageKind.PHYSICAL, skills.getValue("hit").kind)
        assertEquals(RangeSpec(2, 3), skills.getValue("shot").range)
    }

    @Test
    fun assembleBuildsLoadoutsOmittingUnitsWithoutOne() {
        val loadouts = CampaignAssembler.assemble(content(), "b", "m").context.loadouts
        assertEquals(mapOf("hero" to listOf("hit"), "foe" to listOf("shot")), loadouts)
    }

    @Test
    fun assembleDeploysRosterAtScriptedTilesAtFullHp() {
        val state = CampaignAssembler.assemble(content(), "b", "m").initialState
        assertEquals(setOf("hero", "foe"), state.units.keys) // only the two deployed by pre-ops, not "extra"
        val hero = state.units.getValue("hero")
        assertEquals(Pos(0, 0), hero.pos)
        assertEquals(Faction.PLAYER, hero.faction)
        assertEquals(100, hero.hp) // full hp from the assembled reserve
        assertEquals(Pos(2, 2), state.units.getValue("foe").pos)
        assertEquals(Faction.ENEMY, state.units.getValue("foe").faction)
        assertEquals(1, state.turn)
        assertEquals(Faction.PLAYER, state.active)
    }

    @Test
    fun assembleReturnsEntryScriptAndHonorsSeed() {
        val setup = CampaignAssembler.assemble(content(), "b", "m", seed = 7L)
        assertEquals("b", setup.script.id)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), setup.script.win)
        assertEquals(7L, setup.initialState.rngState)
    }

    @Test
    fun assembleSurfacesAScriptContextWithReservesAndMapForMidTriggers() {
        // The battle loop threads this ScriptContext into TriggerRunner.tick; reserves must cover every
        // unit (so a mid SpawnUnit can draw any of them) and the map must be the same one commands use.
        val setup = CampaignAssembler.assemble(content(), "b", "m")
        assertEquals(setOf("hero", "foe", "extra"), setup.scriptContext.reserves.keys)
        assertSame(setup.context.map, setup.scriptContext.map, "script and battle contexts share one BattleMap")
    }

    @Test
    fun assembleThrowsWhenMidTriggerReachTileFallsOutsideMap() {
        val mid = listOf(
            BattleTrigger(
                id = "oob",
                whenCondition = TriggerCondition.UnitReach("hero", Pos(4, 0)),
                actions = emptyList(),
            ),
        )

        val error = assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(mid = mid), "b", "m") }

        assertTrue(error.message.orEmpty().contains("events.sScripts[b].mid[0].when.pos=(4, 0)"))
    }

    @Test
    fun assembleThrowsWhenMidActionLandingFallsOutsideMap() {
        val mid = listOf(
            BattleTrigger(
                id = "spawn-oob",
                whenCondition = TriggerCondition.TurnStart(turn = 1),
                actions = listOf(BattleOp.SpawnUnit("extra", Pos(0, 3))),
            ),
        )

        val error = assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(mid = mid), "b", "m") }

        assertTrue(error.message.orEmpty().contains("events.sScripts[b].mid[0].actions[0].at=(0, 3)"))
    }

    @Test
    fun assembleThrowsWhenWinReachTileFallsOutsideMap() {
        val win = listOf(WinLoseCondition.ReachTile("hero", Pos(4, 2)))

        val error = assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(win = win), "b", "m") }

        assertTrue(error.message.orEmpty().contains("events.sScripts[b].win[0].pos=(4, 2)"))
    }

    @Test
    fun assembleThrowsOnUnknownScriptId() {
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(), "missing", "m") }
    }

    @Test
    fun assembleThrowsOnUnknownMapId() {
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(), "b", "missing") }
    }

    @Test
    fun assembleThrowsWhenATileNamesUnknownTerrain() {
        val tiles = listOf(listOf("plain", "plain", "void"), listOf("plain", "plain", "plain"), listOf("plain", "plain", "plain"))
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(tiles = tiles), "b", "m") }
    }

    @Test
    fun assembleThrowsWhenDeploymentLandsOnImpassableTile() {
        // (1,1) is the wall: threading the map into the spawn path makes IMPASSABLE fire fail-closed,
        // which would otherwise be a dormant check (no map in the context) — proving the map is wired in.
        val pre = listOf(BattleOp.SpawnUnit("hero", Pos(1, 1)))
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(pre = pre), "b", "m") }
    }

    @Test
    fun assembleThrowsWhenTwoUnitsDeployOntoTheSameTile() {
        val pre = listOf(BattleOp.SpawnUnit("hero", Pos(0, 0)), BattleOp.SpawnUnit("foe", Pos(0, 0)))
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(pre = pre), "b", "m") }
    }

    @Test
    fun assembleThrowsWhenADeploymentMoveOpIsRejected() {
        // A spawned unit then moved onto the impassable wall surfaces Event.MoveRejected (not SpawnRejected):
        // pins that deploy() catches non-spawn placement rejections too, not just the spawn path.
        val pre = listOf(BattleOp.SpawnUnit("hero", Pos(0, 0)), BattleOp.MoveUnit("hero", Pos(1, 1)))
        assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(pre = pre), "b", "m") }
    }
}
