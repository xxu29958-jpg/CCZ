package com.ccz.contentpack.json

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the native content pack. These mirror the domain model but use
 * primitives + strings for enums, so JSON decoding never touches the pure
 * game-core types. Enum strings are whitelisted (fail-closed) when mapping DTO ->
 * domain (see [decodeFaction] / [decodeDamageKind] / [decodeCounterRelation]).
 * JSON keys are snake_case via the loader's naming strategy.
 */
@Serializable
internal data class ContentDto(
    val manifest: ManifestDto,
    val tables: TablesDto,
    val events: EventsDto = EventsDto(),
)

@Serializable
internal data class ManifestDto(
    val nativeFormatVersion: String,
    val contentId: String,
    val contentVersion: String,
    val source: SourceDto,
    val entry: String,
)

@Serializable
internal data class SourceDto(val mod: String, val engine: String? = null)

@Serializable
internal data class TablesDto(
    val classes: List<ClassDto> = emptyList(),
    val units: List<UnitDto> = emptyList(),
    val terrain: List<TerrainDto> = emptyList(),
    val skills: List<SkillDto> = emptyList(),
    val items: List<ItemDto> = emptyList(),
    val maps: List<MapDto> = emptyList(),
)

@Serializable
internal data class ClassDto(
    val id: String,
    val name: String,
    val movement: MovementDto,
    val combat: CombatDto = CombatDto(),
)

@Serializable
internal data class MovementDto(
    val moveType: String,
    val move: Int,
    val terrainCost: Map<String, Int> = emptyMap(),
)

@Serializable
internal data class CombatDto(
    val counters: Map<String, String> = emptyMap(),
    val terrainAffinity: Map<String, Int> = emptyMap(),
    val skills: List<String> = emptyList(),
)

@Serializable
internal data class UnitDto(
    val identity: IdentityDto,
    val profile: ProfileDto,
    val loadout: LoadoutDto = LoadoutDto(),
    val assets: AssetsDto = AssetsDto(),
)

@Serializable
internal data class IdentityDto(val id: String, val name: String, val classId: String, val faction: String)

@Serializable
internal data class ProfileDto(
    val level: Int,
    val hpMax: Int,
    val stats: StatsDto,
    val rates: RatesDto = RatesDto(),
)

@Serializable
internal data class StatsDto(val atk: Int, val def: Int, val mat: Int, val res: Int)

@Serializable
internal data class RatesDto(val accuracy: AccuracyDto = AccuracyDto(), val burst: BurstDto = BurstDto())

@Serializable
internal data class AccuracyDto(
    val hit: Int = 100,
    val evade: Int = 0,
    val precision: Int = 0,
    val block: Int = 0,
)

@Serializable
internal data class BurstDto(
    val crit: Int = 0,
    val critResist: Int = 0,
    val combo: Int = 0,
    val comboResist: Int = 0,
)

@Serializable
internal data class LoadoutDto(val skills: List<String> = emptyList(), val items: List<String> = emptyList())

@Serializable
internal data class AssetsDto(val portrait: String? = null, val spriteSet: String? = null)

@Serializable
internal data class TerrainDto(
    val id: String,
    val name: String,
    val moveCost: Int,
    val passable: Boolean = true,
    val bonuses: TerrainBonusesDto = TerrainBonusesDto(),
)

@Serializable
internal data class TerrainBonusesDto(
    val defBonus: Int = 0,
    val avoidBonus: Int = 0,
    val heal: Int = 0,
)

@Serializable
internal data class SkillDto(
    val id: String,
    val name: String,
    val kind: String,
    val powerCoeff: Int,
    val use: UseDto,
)

@Serializable
internal data class UseDto(val range: RangeDto, val area: String, val targeting: String, val mpCost: Int = 0)

@Serializable
internal data class RangeDto(val min: Int, val max: Int)

@Serializable
internal data class ItemDto(
    val id: String,
    val name: String,
    val type: String,
    val statMods: StatsDto? = null,
    val effects: List<String> = emptyList(),
    val equipClass: String? = null,
)

@Serializable
internal data class MapDto(
    val id: String,
    val size: SizeDto,
    val tileset: String,
    val tiles: List<List<String>>,
    val spawnPoints: Map<String, List<PosDto>> = emptyMap(),
    val fog: Boolean = false,
)

@Serializable
internal data class SizeDto(val width: Int, val height: Int)

@Serializable
internal data class PosDto(val x: Int, val y: Int)

/** Thrown when a content pack cannot be decoded or whitelisted; carries a locating message. */
class ContentDecodeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
