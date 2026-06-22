package com.ccz.app.battle

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
import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.CombatStats
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

/**
 * The app's built-in demo campaign, expressed as a native-content [NativeContent] pack — the
 * content-driven replacement for the old hand-built core-type seed. [com.ccz.contentpack.assembly.CampaignAssembler]
 * turns this into the runnable battle the app drives (via [DemoBattle]). It is plain content — classes,
 * units, terrain (a wall + forest), the skill table, and a battle S-script that *deploys* the opening
 * roster with SpawnUnit ops and judges win (rout all enemies) / lose (the protagonist Guan falls) — NOT
 * a second source of combat truth: the app never reads it to decide an outcome, it only assembles it and
 * hands the result to [com.ccz.core.battle.Gameplay].
 */
object CampaignContent {
    const val BATTLE_SCRIPT_ID = "demo_battle"
    const val MAP_ID = "battle_map"
    const val WIDTH = 7
    const val HEIGHT = 6

    fun pack(): NativeContent = NativeContent(
        manifest = ContentManifest(
            nativeFormatVersion = "1",
            contentId = "ccz_demo",
            contentVersion = "0.1.0",
            source = SourceInfo(mod = "ccz_demo"),
            entry = BATTLE_SCRIPT_ID,
        ),
        tables = ContentTables(
            classes = classes(),
            units = units(),
            terrain = terrain(),
            skills = skills(),
            items = emptyList(),
            maps = listOf(map()),
        ),
        events = EventTables(sScripts = listOf(battleScript())),
    )

    private fun classes(): List<ClassDef> = listOf(
        ClassDef("cavalry", "Cavalry", ClassMovement("horse", 5)),
        ClassDef("infantry", "Infantry", ClassMovement("foot", 4)),
        ClassDef("archer", "Archer", ClassMovement("foot", 4)),
    )

    // strike = reach 1, spear = reach 1–2, bow = reach 2–3 — so switching skill changes targets.
    private fun skills(): List<SkillDef> = listOf(
        skill("strike", "Strike", power = 100, min = 1, max = 1),
        skill("spear", "Spear Thrust", power = 110, min = 1, max = 2),
        skill("bow", "Bow Shot", power = 80, min = 2, max = 3),
    )

    private fun skill(id: String, name: String, power: Int, min: Int, max: Int): SkillDef =
        SkillDef(id, name, DamageKind.PHYSICAL, power, SkillUse(RangeDef(min, max), area = "single", targeting = "enemy"))

    private fun terrain(): List<TerrainDef> = listOf(
        TerrainDef("plain", "Plain", moveCost = 1),
        TerrainDef("wall", "Wall", moveCost = 1, passable = false),
        TerrainDef("forest", "Forest", moveCost = 2),
    )

    private fun units(): List<UnitDef> = listOf(
        unit(UnitIdentity("guan", "Guan Yu", "cavalry", Faction.PLAYER), hp = 240, skills = listOf("strike", "spear")),
        unit(UnitIdentity("zhang", "Zhang Fei", "infantry", Faction.PLAYER), hp = 220, skills = listOf("strike")),
        unit(UnitIdentity("foe", "Enemy Archer", "archer", Faction.ENEMY), hp = 180, skills = listOf("bow")),
        unit(UnitIdentity("foe2", "Enemy Spearman", "infantry", Faction.ENEMY), hp = 200, skills = listOf("strike")),
    )

    private fun unit(identity: UnitIdentity, hp: Int, skills: List<String>): UnitDef = UnitDef(
        identity = identity,
        profile = UnitProfile(level = 1, hpMax = hp, stats = CombatStats(atk = 120, def = 80, mat = 40, res = 60)),
        loadout = UnitLoadout(skills = skills),
    )

    private fun map(): MapDef = MapDef(
        id = MAP_ID,
        size = MapSize(WIDTH, HEIGHT),
        tileset = "fields",
        tiles = (0 until HEIGHT).map { y -> (0 until WIDTH).map { x -> terrainIdAt(x, y) } },
    )

    private fun terrainIdAt(x: Int, y: Int): String = when {
        x == 3 && y in 1..3 -> "wall"
        y == 4 && x in 2..5 -> "forest"
        else -> "plain"
    }

    // pre deploys the roster from reserves; win/lose mirrors the classic 主将阵亡 = 失败 rule.
    private fun battleScript(): SScript = SScript(
        id = BATTLE_SCRIPT_ID,
        win = listOf(WinLoseCondition.AnnihilateEnemies),
        lose = listOf(WinLoseCondition.ProtectAlive("guan")),
        pre = listOf(
            BattleOp.SpawnUnit("guan", Pos(1, 2)),
            BattleOp.SpawnUnit("zhang", Pos(4, 2)),
            BattleOp.SpawnUnit("foe", Pos(5, 2)),
            BattleOp.SpawnUnit("foe2", Pos(1, 4)),
        ),
        mid = emptyList(),
        post = emptyList(),
    )
}
