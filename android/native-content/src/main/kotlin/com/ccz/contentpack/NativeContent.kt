package com.ccz.contentpack

import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

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
)

data class ClassCombat(
    val counters: Map<String, String> = emptyMap(),
    val terrainAffinity: Map<String, Int> = emptyMap(),
    val skills: List<String> = emptyList(),
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

/** Combat modifiers a tile grants its occupant; content metadata not yet read by game-core. */
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
