package com.ccz.core.event

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

data class DialogueLine(
    val speaker: String? = null,
    val text: String,
)

sealed interface ScenarioOp {
    data class Dialogue(val line: DialogueLine) : ScenarioOp
    data class Portrait(val unit: String, val emotion: String? = null) : ScenarioOp
    data class Choice(val prompt: String, val options: List<ChoiceOption>) : ScenarioOp
    data class SetVar(val name: String, val value: Int) : ScenarioOp
    data class Branch(val variable: String, val equals: Int, val target: String) : ScenarioOp
    data class Label(val name: String) : ScenarioOp
    data class Wait(val ticks: Int) : ScenarioOp
    data class SceneTransition(val target: String) : ScenarioOp
    data class PlayBgm(val id: String) : ScenarioOp
    data object FadeIn : ScenarioOp
    data object FadeOut : ScenarioOp
}

data class ChoiceOption(
    val text: String,
    val goto: String? = null,
    val setVars: Map<String, Int> = emptyMap(),
)

sealed interface TriggerCondition {
    data class TurnStart(val turn: Int, val faction: Faction? = null) : TriggerCondition
    data class UnitDead(val unit: String) : TriggerCondition
    data class UnitReach(val unit: String, val pos: Pos) : TriggerCondition
    data class HpBelow(val unit: String, val pct: Int) : TriggerCondition
    data class EnemyCountBelow(val count: Int) : TriggerCondition
    data class VarEquals(val name: String, val value: Int) : TriggerCondition
}

sealed interface BattleOp {
    data class Script(val op: ScenarioOp) : BattleOp
    data class SpawnUnit(val unit: String, val at: Pos, val faction: Faction? = null) : BattleOp
    data class RemoveUnit(val unit: String) : BattleOp
    data class MoveUnit(val unit: String, val to: Pos) : BattleOp
    data class SetHp(val unit: String, val hp: Int) : BattleOp
    data class SetStatus(val unit: String, val status: String) : BattleOp
    data class GiveItem(val to: String, val item: String) : BattleOp
    data object ForceWin : BattleOp
    data object ForceLose : BattleOp
}

data class BattleTrigger(
    val id: String,
    val whenCondition: TriggerCondition,
    val once: Boolean = true,
    val actions: List<BattleOp>,
)

sealed interface WinLoseCondition {
    data object AnnihilateEnemies : WinLoseCondition
    data class UnitDead(val unit: String) : WinLoseCondition
    data class ReachTile(val unit: String, val pos: Pos) : WinLoseCondition
    data class SurviveTurns(val turns: Int) : WinLoseCondition
    data class ProtectAlive(val unit: String) : WinLoseCondition
    data class DefeatUnit(val unit: String) : WinLoseCondition
}

data class RScript(
    val id: String,
    val ops: List<ScenarioOp>,
)

data class SScript(
    val id: String,
    val win: List<WinLoseCondition>,
    val lose: List<WinLoseCondition>,
    val pre: List<BattleOp>,
    val mid: List<BattleTrigger>,
    val post: List<BattleOp>,
)
