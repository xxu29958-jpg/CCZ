package com.ccz.contentpack

import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.SkillEffect

data class NativeContent(
    val manifest: ContentManifest,
    val tables: ContentTables,
    val events: EventTables = EventTables(),
)

data class ContentTables(
    val classes: List<ClassDef>,
    val units: List<UnitDef>,
    val terrain: List<TerrainDef>,
    val skills: List<SkillDef>,
    val items: List<ItemDef>,
    val maps: List<MapDef>,
)

data class EventTables(
    val rScripts: List<RScript> = emptyList(),
    val sScripts: List<SScript> = emptyList(),
    val portraitSubjects: List<PortraitSubjectDef> = emptyList(),
)

data class ContentManifest(
    val nativeFormatVersion: String,
    val contentId: String,
    val contentVersion: String,
    val source: SourceInfo,
    val entry: String,
)

data class SourceInfo(
    val mod: String,
    val engine: String? = null,
)

data class ClassDef(
    val id: String,
    val name: String,
    val movement: ClassMovement,
    val combat: ClassCombat = ClassCombat(),
)

data class ClassMovement(
    val moveType: String,
    val move: Int,
    /** Per-terrain move cost for this class (terrainId → cost; `<= 0` impassable). Empty = tile default. */
    val terrainCost: Map<String, Int> = emptyMap(),
)

data class ClassCombat(
    val counters: Map<String, String> = emptyMap(),
    val terrainAffinity: Map<String, Int> = emptyMap(),
    val skills: List<String> = emptyList(),
    val growth: ClassGrowth = ClassGrowth(),
)

/**
 * Per-class stat growth weights consumed at assembly time by [com.ccz.contentpack.assembly.GrowthBudget]
 * (ADR 0006). Each weight is the flat amount that stat gains per level above 1. All-zero (the default)
 * means no growth — units assemble at their base panel, the engine's current behaviour — so this is
 * dormant until content (or the legacy `dic_job` importer, a later phase) supplies real weights. `res`
 * has no legacy source yet and is left 0 rather than invented.
 */
data class ClassGrowth(
    val atk: Int = 0,
    val def: Int = 0,
    val mat: Int = 0,
    val res: Int = 0,
    val hp: Int = 0,
)

data class UnitDef(
    val identity: UnitIdentity,
    val profile: UnitProfile,
    val loadout: UnitLoadout = UnitLoadout(),
    val assets: UnitAssets = UnitAssets(),
) {
    val id: String get() = identity.id
    val classId: String get() = identity.classId
}

data class UnitIdentity(
    val id: String,
    val name: String,
    val classId: String,
    val faction: Faction,
)

data class UnitProfile(
    val level: Int,
    val hpMax: Int,
    val stats: CombatStats,
    val rates: CombatRates = CombatRates(),
    /** Quality tier (0 = neutral baseline) into this engine's own grade → growth-multiplier ladder
     *  (`GrowthConfig.gradeMulPctByGrade`, ADR 0006 "评级"): it scales how fast class growth accrues at
     *  assembly time. A non-negative tier index, designed here rather than ported from the old rating
     *  system; units default to 0 (budget exactly as before), and a higher tier is only set by content
     *  that declares one. */
    val grade: Int = 0,
)

data class UnitLoadout(
    val skills: List<String> = emptyList(),
    val items: List<String> = emptyList(),
)

data class UnitAssets(
    val portrait: String? = null,
    val spriteSet: String? = null,
)

data class PortraitSubjectDef(
    val id: String,
    val name: String,
    val portrait: String? = null,
)

data class TerrainDef(
    val id: String,
    val name: String,
    val moveCost: Int,
    // Whether a unit may enter the tile at all. False models a wall / blocked tile; the assembler
    // carries it straight onto the engine's MapTile.passable, which gates both movement reachability
    // and op-driven placement. Defaults true (an ordinary terrain is passable) so existing packs and
    // v1 saves that predate the field decode unchanged.
    val passable: Boolean = true,
    val bonuses: TerrainBonuses = TerrainBonuses(),
)

/**
 * Combat modifiers a tile grants its occupant. The assembler carries these onto
 * [com.ccz.core.battle.MapTile] (defBonus/avoidBonus/heal), where game-core's damage/heal
 * resolution reads them — terrain cover is live, not just metadata.
 */
data class TerrainBonuses(
    val defBonus: Int = 0,
    val avoidBonus: Int = 0,
    val heal: Int = 0,
)

data class SkillDef(
    val id: String,
    val name: String,
    val kind: DamageKind,
    val powerCoeff: Int,
    val use: SkillUse,
    // Engine effects beyond damage (ADR 0008), reusing the game-core SkillEffect type directly (as `kind`
    // reuses DamageKind). Default empty = a pure damage skill. The assembler carries these onto the core
    // Skill so the resolver can apply them via Command.Cast.
    val effects: List<SkillEffect> = emptyList(),
)

data class SkillUse(
    val range: RangeDef,
    val area: String,
    val targeting: String,
    val mpCost: Int = 0,
)

data class RangeDef(val min: Int, val max: Int)

data class ItemDef(
    val id: String,
    val name: String,
    val type: String,
    val statMods: CombatStats? = null,
    val effects: List<String> = emptyList(),
    val equipClass: String? = null,
)

data class MapDef(
    val id: String,
    val size: MapSize,
    val tileset: String,
    val tiles: List<List<String>>,
    val spawnPoints: Map<String, List<Pos>> = emptyMap(),
    val fog: Boolean = false,
)

data class MapSize(val width: Int, val height: Int)
