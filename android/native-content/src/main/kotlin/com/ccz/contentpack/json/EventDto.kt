package com.ccz.contentpack.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for native event scripts (R = scenario flow, S = battle script).
 *
 * The op layers are polymorphic: kotlinx decodes each op by a class-discriminator
 * field ("type", configured on the loader's [ContentJsonLoader] Json) whose value is
 * the op's [SerialName]. That discriminator IS the op-string whitelist — an unknown
 * op string has no registered sealed subclass and fails closed (kotlinx
 * SerializationException -> [ContentDecodeException]). Reference integrity (unit /
 * item ids) is a separate, downstream layer in
 * [com.ccz.contentpack.ContentEventValidator].
 *
 * The game-core event types stay free of any serialization dependency; these DTOs
 * mirror them and the event mappers ([toEvents]) translate, decoding faction strings
 * through the shared [decodeFaction] whitelist. JSON keys are snake_case (loader
 * naming strategy); op discriminator values are the explicit snake_case [SerialName]s
 * (a naming strategy does not transform serial names).
 */
@Serializable
internal data class EventsDto(
    val rScripts: List<RScriptDto> = emptyList(),
    val sScripts: List<SScriptDto> = emptyList(),
    val portraitSubjects: List<PortraitSubjectDto> = emptyList(),
)

@Serializable
internal data class PortraitSubjectDto(val id: String, val name: String, val portrait: String? = null)

@Serializable
internal data class RScriptDto(
    val id: String,
    val ops: List<ScenarioOpDto> = emptyList(),
)

@Serializable
internal data class SScriptDto(
    val id: String,
    val win: List<WinLoseConditionDto> = emptyList(),
    val lose: List<WinLoseConditionDto> = emptyList(),
    val pre: List<BattleOpDto> = emptyList(),
    val mid: List<BattleTriggerDto> = emptyList(),
    val post: List<BattleOpDto> = emptyList(),
)

@Serializable
internal data class BattleTriggerDto(
    val id: String,
    val whenCondition: TriggerConditionDto,
    val once: Boolean = true,
    val actions: List<BattleOpDto> = emptyList(),
)

@Serializable
internal data class DialogueLineDto(
    val speaker: String? = null,
    val text: String,
)

@Serializable
internal data class ChoiceOptionDto(
    val text: String,
    val goto: String? = null,
    val setVars: Map<String, Int> = emptyMap(),
)

@Serializable
internal sealed interface ScenarioOpDto {
    @Serializable
    @SerialName("dialogue")
    data class Dialogue(val line: DialogueLineDto) : ScenarioOpDto

    @Serializable
    @SerialName("portrait")
    data class Portrait(val unit: String, val emotion: String? = null) : ScenarioOpDto

    @Serializable
    @SerialName("choice")
    data class Choice(val prompt: String, val options: List<ChoiceOptionDto> = emptyList()) : ScenarioOpDto

    @Serializable
    @SerialName("set_var")
    data class SetVar(val name: String, val value: Int) : ScenarioOpDto

    @Serializable
    @SerialName("branch")
    data class Branch(val variable: String, val equals: Int, val target: String) : ScenarioOpDto

    @Serializable
    @SerialName("label")
    data class Label(val name: String) : ScenarioOpDto

    @Serializable
    @SerialName("wait")
    data class Wait(val ticks: Int) : ScenarioOpDto

    @Serializable
    @SerialName("scene_transition")
    data class SceneTransition(val target: String) : ScenarioOpDto

    @Serializable
    @SerialName("play_bgm")
    data class PlayBgm(val id: String) : ScenarioOpDto

    @Serializable
    @SerialName("fade_in")
    data object FadeIn : ScenarioOpDto

    @Serializable
    @SerialName("fade_out")
    data object FadeOut : ScenarioOpDto
}

@Serializable
internal sealed interface TriggerConditionDto {
    @Serializable
    @SerialName("turn_start")
    data class TurnStart(val turn: Int, val faction: String? = null) : TriggerConditionDto

    @Serializable
    @SerialName("unit_dead")
    data class UnitDead(val unit: String) : TriggerConditionDto

    @Serializable
    @SerialName("unit_reach")
    data class UnitReach(val unit: String, val pos: PosDto) : TriggerConditionDto

    @Serializable
    @SerialName("hp_below")
    data class HpBelow(val unit: String, val pct: Int) : TriggerConditionDto

    @Serializable
    @SerialName("enemy_count_below")
    data class EnemyCountBelow(val count: Int) : TriggerConditionDto

    @Serializable
    @SerialName("var_equals")
    data class VarEquals(val name: String, val value: Int) : TriggerConditionDto
}

@Serializable
internal sealed interface BattleOpDto {
    @Serializable
    @SerialName("script")
    data class Script(val op: ScenarioOpDto) : BattleOpDto

    @Serializable
    @SerialName("spawn_unit")
    data class SpawnUnit(val unit: String, val at: PosDto, val faction: String? = null) : BattleOpDto

    @Serializable
    @SerialName("remove_unit")
    data class RemoveUnit(val unit: String) : BattleOpDto

    @Serializable
    @SerialName("move_unit")
    data class MoveUnit(val unit: String, val to: PosDto) : BattleOpDto

    @Serializable
    @SerialName("set_hp")
    data class SetHp(val unit: String, val hp: Int) : BattleOpDto

    @Serializable
    @SerialName("set_status")
    data class SetStatus(val unit: String, val status: String) : BattleOpDto

    @Serializable
    @SerialName("give_item")
    data class GiveItem(val to: String, val item: String) : BattleOpDto

    @Serializable
    @SerialName("force_win")
    data object ForceWin : BattleOpDto

    @Serializable
    @SerialName("force_lose")
    data object ForceLose : BattleOpDto
}

@Serializable
internal sealed interface WinLoseConditionDto {
    @Serializable
    @SerialName("annihilate_enemies")
    data object AnnihilateEnemies : WinLoseConditionDto

    @Serializable
    @SerialName("unit_dead")
    data class UnitDead(val unit: String) : WinLoseConditionDto

    @Serializable
    @SerialName("reach_tile")
    data class ReachTile(val unit: String, val pos: PosDto) : WinLoseConditionDto

    @Serializable
    @SerialName("survive_turns")
    data class SurviveTurns(val turns: Int) : WinLoseConditionDto

    @Serializable
    @SerialName("protect_alive")
    data class ProtectAlive(val unit: String) : WinLoseConditionDto

    @Serializable
    @SerialName("defeat_unit")
    data class DefeatUnit(val unit: String) : WinLoseConditionDto
}
