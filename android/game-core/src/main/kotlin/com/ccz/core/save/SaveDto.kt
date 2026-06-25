package com.ccz.core.save

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the on-disk save format. They mirror the game-core domain
 * ([SaveEnvelope] / [BattleState][com.ccz.core.battle.BattleState] / ...) so the
 * domain types stay free of any serialization annotation (CCZ_ENGINE_RULES: game-core
 * 域类型零序列化依赖，DTO 层隔离 JSON). Enums are carried as strings and whitelisted on
 * decode ([SaveMappers]); a `Set` is serialized as a JSON array. Commands are
 * polymorphic, decoded by the `type` class-discriminator whose value is the command's
 * [SerialName] (the command-kind whitelist — an unknown kind fails closed). JSON keys
 * are snake_case (codec naming strategy); discriminator values are explicit snake_case
 * [SerialName]s.
 */
@Serializable
internal data class SaveEnvelopeDto(
    val versions: SaveVersionsDto,
    val initialState: BattleStateDto,
    val commands: List<CommandDto> = emptyList(),
    val scenarios: List<ScenarioReplayDto> = emptyList(),
)

@Serializable
internal data class ScenarioReplayDto(
    val scriptId: String,
    val choices: List<Int> = emptyList(),
)

@Serializable
internal data class SaveVersionsDto(
    val saveSchemaVersion: Int,
    val rulesVersion: Int,
    val engineVersion: String,
    val nativeFormatVersion: String,
    val contentVersion: String,
    val converterVersion: String? = null,
)

@Serializable
internal data class BattleStateDto(
    val units: Map<String, CombatantDto> = emptyMap(),
    val turn: Int,
    val active: String,
    val rngState: Long,
    val progress: BattleProgressDto = BattleProgressDto(),
)

@Serializable
internal data class BattleProgressDto(
    val outcome: String = "ONGOING",
    val vars: Map<String, Int> = emptyMap(),
    val firedTriggers: Set<String> = emptySet(),
)

@Serializable
internal data class CombatantDto(
    val identity: CombatIdentityDto,
    val pos: PosDto,
    val vitals: CombatVitalsDto,
    val stats: CombatStatsDto,
    val rates: CombatRatesDto = CombatRatesDto(),
    val statuses: Set<String> = emptySet(),
)

@Serializable
internal data class CombatIdentityDto(
    val id: String,
    val name: String,
    val classId: String,
    val faction: String,
)

@Serializable
internal data class CombatVitalsDto(val hp: Int, val hpMax: Int)

@Serializable
internal data class CombatStatsDto(val atk: Int, val def: Int, val mat: Int, val res: Int)

@Serializable
internal data class CombatRatesDto(
    val accuracy: AccuracyRatesDto = AccuracyRatesDto(),
    val burst: BurstRatesDto = BurstRatesDto(),
)

@Serializable
internal data class AccuracyRatesDto(
    val hit: Int = 100,
    val evade: Int = 0,
    val precision: Int = 0,
    val block: Int = 0,
)

@Serializable
internal data class BurstRatesDto(
    val crit: Int = 0,
    val critResist: Int = 0,
    val combo: Int = 0,
    val comboResist: Int = 0,
)

@Serializable
internal data class PosDto(val x: Int, val y: Int)

@Serializable
internal sealed interface CommandDto {
    @Serializable
    @SerialName("move")
    data class Move(val unit: String, val to: PosDto) : CommandDto

    @Serializable
    @SerialName("attack")
    data class Attack(val attacker: String, val target: String, val skill: String) : CommandDto

    @Serializable
    @SerialName("cast")
    data class Cast(val caster: String, val target: String, val skill: String) : CommandDto

    @Serializable
    @SerialName("wait")
    data class Wait(val unit: String) : CommandDto

    @Serializable
    @SerialName("end_turn")
    data class EndTurn(val faction: String) : CommandDto
}
