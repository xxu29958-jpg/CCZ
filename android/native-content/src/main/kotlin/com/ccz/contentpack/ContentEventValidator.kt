package com.ccz.contentpack

import com.ccz.core.event.BattleOp
import com.ccz.core.event.SScript
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition

/**
 * Validates event reference integrity for S-scripts: units / items referenced by
 * battle ops, triggers, and win/lose conditions must resolve to known content ids.
 *
 * Note: the op set itself is whitelisted by Kotlin's sealed interfaces (an unknown
 * op cannot be constructed in memory); a string-keyed op whitelist only becomes
 * relevant at a future JSON decode boundary. This validator covers the reference
 * integrity that the type system does not.
 */
internal object ContentEventValidator {
    fun validate(events: EventTables, unitIds: Set<String>, itemIds: Set<String>): List<ValidationIssue> =
        events.sScripts.flatMap { script(it, unitIds, itemIds) }

    private fun script(script: SScript, unitIds: Set<String>, itemIds: Set<String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        (script.pre + script.post + script.mid.flatMap { it.actions }).forEach {
            issues += battleOp(script.id, it, unitIds, itemIds)
        }
        script.mid.forEach { issues += trigger(script.id, it.whenCondition, unitIds) }
        script.win.forEach { issues += winLose(script.id, "win", it, unitIds) }
        script.lose.forEach { issues += winLose(script.id, "lose", it, unitIds) }
        return issues
    }

    private fun unit(path: String, id: String, unitIds: Set<String>): ValidationIssue? =
        if (id in unitIds) null else ValidationIssue(path, "unknown unit: $id")

    private fun battleOp(id: String, op: BattleOp, unitIds: Set<String>, itemIds: Set<String>): List<ValidationIssue> {
        val path = "events.sScripts[$id]"
        return when (op) {
            is BattleOp.SpawnUnit -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.RemoveUnit -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.MoveUnit -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.SetHp -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.SetStatus -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.GiveItem -> listOfNotNull(
                unit(path, op.to, unitIds),
                if (op.item in itemIds) null else ValidationIssue(path, "unknown item: ${op.item}"),
            )
            is BattleOp.Script, BattleOp.ForceWin, BattleOp.ForceLose -> emptyList()
        }
    }

    private fun trigger(id: String, cond: TriggerCondition, unitIds: Set<String>): List<ValidationIssue> {
        val path = "events.sScripts[$id].trigger"
        return when (cond) {
            is TriggerCondition.UnitDead -> listOfNotNull(unit(path, cond.unit, unitIds))
            is TriggerCondition.UnitReach -> listOfNotNull(unit(path, cond.unit, unitIds))
            is TriggerCondition.HpBelow -> listOfNotNull(unit(path, cond.unit, unitIds))
            is TriggerCondition.TurnStart,
            is TriggerCondition.EnemyCountBelow,
            is TriggerCondition.VarEquals,
            -> emptyList()
        }
    }

    private fun winLose(id: String, side: String, cond: WinLoseCondition, unitIds: Set<String>): List<ValidationIssue> {
        val path = "events.sScripts[$id].$side"
        return when (cond) {
            is WinLoseCondition.UnitDead -> listOfNotNull(unit(path, cond.unit, unitIds))
            is WinLoseCondition.ReachTile -> listOfNotNull(unit(path, cond.unit, unitIds))
            is WinLoseCondition.ProtectAlive -> listOfNotNull(unit(path, cond.unit, unitIds))
            is WinLoseCondition.DefeatUnit -> listOfNotNull(unit(path, cond.unit, unitIds))
            WinLoseCondition.AnnihilateEnemies,
            is WinLoseCondition.SurviveTurns,
            -> emptyList()
        }
    }
}
