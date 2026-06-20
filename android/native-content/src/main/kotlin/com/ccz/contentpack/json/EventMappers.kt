package com.ccz.contentpack.json

import com.ccz.contentpack.EventTables
import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.ChoiceOption
import com.ccz.core.event.DialogueLine
import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition

/**
 * Maps decoded event-script DTOs into the pure game-core event model. The op string
 * whitelist is already enforced upstream by polymorphic decode (see [EventDto]); this
 * layer only translates shapes and whitelists the embedded faction strings via the
 * shared [decodeFaction] (fail-closed). Positions go through [toPos].
 */
internal fun toEvents(dto: EventsDto): EventTables =
    EventTables(
        rScripts = dto.rScripts.map { toRScript(it) },
        sScripts = dto.sScripts.map { toSScript(it) },
    )

private fun toRScript(dto: RScriptDto): RScript =
    RScript(id = dto.id, ops = dto.ops.map { toScenarioOp(it) })

private fun toSScript(dto: SScriptDto): SScript {
    val path = "events.s_scripts[${dto.id}]"
    return SScript(
        id = dto.id,
        win = dto.win.map { toWinLose(it) },
        lose = dto.lose.map { toWinLose(it) },
        pre = dto.pre.map { toBattleOp("$path.pre", it) },
        mid = dto.mid.map { toBattleTrigger(path, it) },
        post = dto.post.map { toBattleOp("$path.post", it) },
    )
}

private fun toBattleTrigger(scriptPath: String, dto: BattleTriggerDto): BattleTrigger {
    val path = "$scriptPath.mid[${dto.id}]"
    return BattleTrigger(
        id = dto.id,
        whenCondition = toTriggerCondition("$path.when", dto.whenCondition),
        once = dto.once,
        actions = dto.actions.map { toBattleOp("$path.actions", it) },
    )
}

private fun toScenarioOp(dto: ScenarioOpDto): ScenarioOp =
    when (dto) {
        is ScenarioOpDto.Dialogue -> ScenarioOp.Dialogue(toDialogueLine(dto.line))
        is ScenarioOpDto.Portrait -> ScenarioOp.Portrait(dto.unit, dto.emotion)
        is ScenarioOpDto.Choice -> ScenarioOp.Choice(dto.prompt, dto.options.map { toChoiceOption(it) })
        is ScenarioOpDto.SetVar -> ScenarioOp.SetVar(dto.name, dto.value)
        is ScenarioOpDto.Branch -> ScenarioOp.Branch(dto.variable, dto.equals, dto.target)
        is ScenarioOpDto.Label -> ScenarioOp.Label(dto.name)
        is ScenarioOpDto.Wait -> ScenarioOp.Wait(dto.ticks)
        is ScenarioOpDto.SceneTransition -> ScenarioOp.SceneTransition(dto.target)
        is ScenarioOpDto.PlayBgm -> ScenarioOp.PlayBgm(dto.id)
        ScenarioOpDto.FadeIn -> ScenarioOp.FadeIn
        ScenarioOpDto.FadeOut -> ScenarioOp.FadeOut
    }

private fun toBattleOp(path: String, dto: BattleOpDto): BattleOp =
    when (dto) {
        is BattleOpDto.Script -> BattleOp.Script(toScenarioOp(dto.op))
        is BattleOpDto.SpawnUnit -> BattleOp.SpawnUnit(
            unit = dto.unit,
            at = toPos(dto.at),
            faction = dto.faction?.let { decodeFaction("$path.spawn_unit.faction", it) },
        )
        is BattleOpDto.RemoveUnit -> BattleOp.RemoveUnit(dto.unit)
        is BattleOpDto.MoveUnit -> BattleOp.MoveUnit(dto.unit, toPos(dto.to))
        is BattleOpDto.SetHp -> BattleOp.SetHp(dto.unit, dto.hp)
        is BattleOpDto.SetStatus -> BattleOp.SetStatus(dto.unit, dto.status)
        is BattleOpDto.GiveItem -> BattleOp.GiveItem(dto.to, dto.item)
        BattleOpDto.ForceWin -> BattleOp.ForceWin
        BattleOpDto.ForceLose -> BattleOp.ForceLose
    }

private fun toTriggerCondition(path: String, dto: TriggerConditionDto): TriggerCondition =
    when (dto) {
        is TriggerConditionDto.TurnStart -> TriggerCondition.TurnStart(
            turn = dto.turn,
            faction = dto.faction?.let { decodeFaction("$path.turn_start.faction", it) },
        )
        is TriggerConditionDto.UnitDead -> TriggerCondition.UnitDead(dto.unit)
        is TriggerConditionDto.UnitReach -> TriggerCondition.UnitReach(dto.unit, toPos(dto.pos))
        is TriggerConditionDto.HpBelow -> TriggerCondition.HpBelow(dto.unit, dto.pct)
        is TriggerConditionDto.EnemyCountBelow -> TriggerCondition.EnemyCountBelow(dto.count)
        is TriggerConditionDto.VarEquals -> TriggerCondition.VarEquals(dto.name, dto.value)
    }

private fun toWinLose(dto: WinLoseConditionDto): WinLoseCondition =
    when (dto) {
        WinLoseConditionDto.AnnihilateEnemies -> WinLoseCondition.AnnihilateEnemies
        is WinLoseConditionDto.UnitDead -> WinLoseCondition.UnitDead(dto.unit)
        is WinLoseConditionDto.ReachTile -> WinLoseCondition.ReachTile(dto.unit, toPos(dto.pos))
        is WinLoseConditionDto.SurviveTurns -> WinLoseCondition.SurviveTurns(dto.turns)
        is WinLoseConditionDto.ProtectAlive -> WinLoseCondition.ProtectAlive(dto.unit)
        is WinLoseConditionDto.DefeatUnit -> WinLoseCondition.DefeatUnit(dto.unit)
    }

private fun toDialogueLine(dto: DialogueLineDto): DialogueLine =
    DialogueLine(speaker = dto.speaker, text = dto.text)

private fun toChoiceOption(dto: ChoiceOptionDto): ChoiceOption =
    ChoiceOption(text = dto.text, goto = dto.goto, setVars = dto.setVars)
