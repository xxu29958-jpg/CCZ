package com.ccz.contentpack.json

import kotlinx.serialization.SerialName
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
    val growth: GrowthDto = GrowthDto(),
)

@Serializable
internal data class GrowthDto(
    val atk: Int = 0,
    val def: Int = 0,
    val mat: Int = 0,
    val res: Int = 0,
    val hp: Int = 0,
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
    val grade: Int = 0,
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
    // Engine effects beyond damage (ADR 0008). Default empty so v1 packs without the field still decode
    // (ignoreUnknownKeys=false). Polymorphic by the loader's "type" discriminator — an unknown effect
    // type has no registered subclass and fails closed (ContentDecodeException).
    val effects: List<SkillEffectDto> = emptyList(),
)

/**
 * Polymorphic skill-effect op (ADR 0008), discriminated by the loader's `type` field whose value is each
 * subclass's [SerialName] (the effect-string whitelist — an unknown type fails closed). Registers `heal`,
 * `stat_delta`, `apply_ailment`, and `cleanse`; later phases extend this sealed set. The `target`/`stat`/`ailment`
 * strings are whitelisted to their game-core enums at the mapper boundary ([decodeEffectTarget] etc.).
 */
@Serializable
internal sealed interface SkillEffectDto {
    @Serializable
    @SerialName("heal")
    data class Heal(val target: String, val amount: Int, val mode: String = "flat") : SkillEffectDto

    @Serializable
    @SerialName("stat_delta")
    data class StatDelta(val target: String, val stat: String, val amount: Int, val duration: Int = 0) : SkillEffectDto

    @Serializable
    @SerialName("apply_ailment")
    data class ApplyAilment(val target: String, val ailment: String, val duration: Int) : SkillEffectDto

    @Serializable
    @SerialName("cleanse")
    data class Cleanse(val target: String) : SkillEffectDto
}

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
