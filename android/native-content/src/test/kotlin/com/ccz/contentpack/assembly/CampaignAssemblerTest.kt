package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassCombat
import com.ccz.contentpack.ClassDef
import com.ccz.contentpack.ClassGrowth
import com.ccz.contentpack.ClassMovement
import com.ccz.contentpack.ContentManifest
import com.ccz.contentpack.ContentTables
import com.ccz.contentpack.DeferredDeploymentDef
import com.ccz.contentpack.EventTables
import com.ccz.contentpack.MapDef
import com.ccz.contentpack.MapSize
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.RangeDef
import com.ccz.contentpack.SkillDef
import com.ccz.contentpack.SkillUse
import com.ccz.contentpack.SourceInfo
import com.ccz.contentpack.TerrainBonuses
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
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.SkillEffect
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
        deferredDeployments: List<DeferredDeploymentDef> = emptyList(),
        win: List<WinLoseCondition> = listOf(WinLoseCondition.AnnihilateEnemies),
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
            terrain = terrainDefs(),
            skills = skillDefs(),
            items = emptyList(),
            // Size derived from the tiles so every variant stays self-consistent (height x width).
            maps = listOf(MapDef(id = "m", size = MapSize(tiles.first().size, tiles.size), tileset = "t", tiles = tiles)),
        ),
        events = EventTables(sScripts = listOf(battleScript(pre = pre, mid = mid, win = win)), deferredDeployments = deferredDeployments),
    )

    private fun terrainDefs(): List<TerrainDef> = listOf(
        TerrainDef(id = "plain", name = "Plain", moveCost = 1),
        TerrainDef(id = "wall", name = "Wall", moveCost = 1, passable = false),
        TerrainDef(id = "wood", name = "Wood", moveCost = 2, bonuses = TerrainBonuses(defBonus = 20, avoidBonus = 15, heal = 10)),
    )

    private fun classDefs(): List<ClassDef> = listOf(
        ClassDef(
            id = "cav",
            name = "Cavalry",
            movement = ClassMovement(moveType = "horse", move = 5),
            // Growth weights so the budgeting path is exercised; inert for level-1 units (scales by level-1).
            combat = ClassCombat(counters = mapOf("inf" to "FAVOR"), growth = ClassGrowth(atk = 5, def = 2, res = 1, hp = 14)),
        ),
        ClassDef(id = "inf", name = "Infantry", movement = ClassMovement(moveType = "foot", move = 4)),
    )

    private fun skillDefs(): List<SkillDef> = listOf(
        skillDef("hit", powerCoeff = 100, min = 1, max = 1),
        skillDef("shot", powerCoeff = 80, min = 2, max = 3),
        skillDef("heal", powerCoeff = 0, min = 0, max = 1).copy(effects = listOf(SkillEffect.Heal(EffectTarget.ALLY, 25))),
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
        // It is also a level-10 grade-2 cavalry veteran (built inline to keep the shared helper at <=5
        // params), so its reserve panel exercises growth × grade budgeting through CampaignAssembler
        // (the deployed level-1 units leave that path at identity).
        UnitDef(
            identity = UnitIdentity(id = "extra", name = "Name-extra", classId = "cav", faction = Faction.ENEMY),
            profile = UnitProfile(level = 10, hpMax = 50, stats = CombatStats(atk = 50, def = 30, mat = 10, res = 20), grade = 2),
            loadout = UnitLoadout(skills = emptyList()),
        ),
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
        assertEquals(20, map.tileAt(Pos(3, 2)).defBonus, "wood's terrain defense carries through to the engine tile")
        assertEquals(15, map.tileAt(Pos(3, 2)).avoidBonus, "wood's terrain avoid carries through to the engine tile")
        assertEquals(10, map.tileAt(Pos(3, 2)).heal, "wood's terrain heal carries through to the engine tile")
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
    fun assembleThreadsSkillEffectsIntoTheCoreSkill() {
        // ADR 0008: a content skill's effects must reach the core Skill so Resolver.cast can apply them
        // (CampaignAssembler.skills() previously dropped everything but id/name/kind/powerCoeff/range).
        val heal = CampaignAssembler.assemble(content(), "b", "m").context.skills.getValue("heal")
        assertEquals(listOf(SkillEffect.Heal(EffectTarget.ALLY, 25)), heal.effects)
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
    fun assembleBudgetsReserveByClassGrowthAndUnitGrade() {
        // CampaignAssembler derives growthByClass from each class's combat.growth and budgets every reserve
        // panel by its unit's level × grade. "extra" is a level-10 grade-2 cavalry veteran; cavalry grows
        // +5 atk / +2 def / +1 res / +14 hp per level, grade 2 = the 140% tier, over (10-1) levels:
        //   atk 50 + 5*9*140/100 = 113, def 30 + 2*9*140/100 = 55, res 20 + 1*9*140/100 = 32,
        //   hp 50 + 14*9*140/100 = 226. mat has no growth weight, so it stays 10.
        val reserves = CampaignAssembler.assemble(content(), "b", "m").scriptContext.reserves
        val veteran = reserves.getValue("extra")
        assertEquals(113, veteran.stats.atk)
        assertEquals(55, veteran.stats.def)
        assertEquals(10, veteran.stats.mat)
        assertEquals(32, veteran.stats.res)
        assertEquals(226, veteran.hpMax)

        // The same cavalry growth is inert for the deployed level-1 hero (scales by level-1 = 0), so its
        // panel stays at base — which is why adding class growth left the level-1 battle byte-identical.
        val hero = reserves.getValue("hero")
        assertEquals(50, hero.stats.atk)
        assertEquals(100, hero.hpMax)
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
    fun assembleThrowsWhenDeferredDeploymentTargetFallsOutsideMap() {
        val deferred = listOf(DeferredDeploymentDef("b", "extra", Pos(4, 0), Faction.ENEMY))

        val error = assertFailsWith<CampaignAssemblyException> {
            CampaignAssembler.assemble(content(deferredDeployments = deferred), "b", "m")
        }

        assertTrue(error.message.orEmpty().contains("events.deferredDeployments[b][0].at=(4, 0)"))
    }

    @Test
    fun assembleThrowsWhenPreMoveTargetsUndeployedUnit() {
        val pre = listOf(BattleOp.MoveUnit("hero", Pos(1, 0)))

        val error = assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(pre = pre), "b", "m") }

        assertTrue(error.message.orEmpty().contains("events.sScripts[b].pre[0].move=hero"))
    }

    @Test
    fun assembleThrowsWhenPreRemoveTargetsUndeployedUnit() {
        val pre = listOf(BattleOp.RemoveUnit("hero"))

        val error = assertFailsWith<CampaignAssemblyException> { CampaignAssembler.assemble(content(pre = pre), "b", "m") }

        assertTrue(error.message.orEmpty().contains("events.sScripts[b].pre[0].remove=hero"))
    }

    @Test
    fun assembleAllowsPreRemoveAfterSpawn() {
        val pre = listOf(
            BattleOp.SpawnUnit("hero", Pos(0, 0)),
            BattleOp.RemoveUnit("hero"),
            BattleOp.SpawnUnit("foe", Pos(2, 2)),
        )

        val state = CampaignAssembler.assemble(content(pre = pre), "b", "m").initialState

        assertFalse("hero" in state.units)
        assertTrue("foe" in state.units)
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
