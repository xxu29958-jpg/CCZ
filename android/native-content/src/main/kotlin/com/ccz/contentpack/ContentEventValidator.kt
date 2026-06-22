package com.ccz.contentpack

import com.ccz.core.event.BattleOp
import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Pos

/**
 * Validates event reference integrity for S-scripts and R-scripts: units / items
 * referenced by battle ops, triggers, and win/lose conditions must resolve to known
 * content ids; an R-script's branch/choice jumps must target a label defined in the
 * same script (labels are unique), and a portrait must name a known unit.
 *
 * It also fails closed on the value-domain floors that are unambiguous independent of
 * any map or game-design choice: a percentage threshold ([TriggerCondition.HpBelow.pct])
 * must lie in 0..100; a survive-N-turns objective ([WinLoseCondition.SurviveTurns]) must
 * be >= 1; and every script-op coordinate (spawn / move / reach targets) must be
 * non-negative (a negative tile is off-board regardless of map size — the upper bound
 * against a concrete map stays a later, map-aware layer).
 *
 * Note: the op set itself is whitelisted by Kotlin's sealed interfaces (an unknown
 * op cannot be constructed in memory); a string-keyed op whitelist only becomes
 * relevant at a future JSON decode boundary. This validator covers the reference
 * integrity that the type system does not.
 */
internal object ContentEventValidator {
    private const val PERCENT_MAX = 100

    fun validate(events: EventTables, unitIds: Set<String>, itemIds: Set<String>): List<ValidationIssue> =
        events.sScripts.flatMap { script(it, unitIds, itemIds) } +
            events.rScripts.flatMap { rScript(it, unitIds) }

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

    /**
     * Map-independent floor on a script-op coordinate: a negative tile is off-board for
     * any map size. The upper bound against a concrete map stays a later, map-aware layer.
     */
    private fun pos(path: String, at: Pos): ValidationIssue? =
        if (at.x >= 0 && at.y >= 0) null else ValidationIssue(path, "negative coordinate: (${at.x}, ${at.y})")

    private fun battleOp(id: String, op: BattleOp, unitIds: Set<String>, itemIds: Set<String>): List<ValidationIssue> {
        val path = "events.sScripts[$id]"
        return when (op) {
            is BattleOp.SpawnUnit -> listOfNotNull(unit(path, op.unit, unitIds), pos(path, op.at))
            is BattleOp.RemoveUnit -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.MoveUnit -> listOfNotNull(unit(path, op.unit, unitIds), pos(path, op.to))
            is BattleOp.SetHp -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.SetStatus -> listOfNotNull(unit(path, op.unit, unitIds))
            is BattleOp.GiveItem -> listOfNotNull(
                unit(path, op.to, unitIds),
                if (op.item in itemIds) null else ValidationIssue(path, "unknown item: ${op.item}"),
            )
            is BattleOp.Script -> embeddedScenarioOp(path, op.op, unitIds)
            BattleOp.ForceWin, BattleOp.ForceLose -> emptyList()
        }
    }

    /**
     * Reference / support check for a scenario op embedded in an S-script via
     * [BattleOp.Script]. Unlike an R-script, an S-script has no label table and
     * [com.ccz.core.battle.BattleOps] only interprets [ScenarioOp.SetVar]; every other
     * embedded op is surfaced as a presentation event. So a Portrait still needs its unit
     * reference checked (the R-script path already does), while a Branch / Choice is
     * provably inert control flow here (its label target can never resolve) and is flagged
     * fail-closed rather than silently dropped. Presentation ops carry no reference.
     */
    private fun embeddedScenarioOp(path: String, op: ScenarioOp, unitIds: Set<String>): List<ValidationIssue> =
        when (op) {
            is ScenarioOp.Portrait -> listOfNotNull(unit(path, op.unit, unitIds))
            is ScenarioOp.Branch -> listOf(ValidationIssue(path, "branch unsupported in s-script op"))
            is ScenarioOp.Choice -> listOf(ValidationIssue(path, "choice unsupported in s-script op"))
            is ScenarioOp.Dialogue,
            is ScenarioOp.SetVar,
            is ScenarioOp.Label,
            is ScenarioOp.Wait,
            is ScenarioOp.SceneTransition,
            is ScenarioOp.PlayBgm,
            ScenarioOp.FadeIn,
            ScenarioOp.FadeOut,
            -> emptyList()
        }

    private fun trigger(id: String, cond: TriggerCondition, unitIds: Set<String>): List<ValidationIssue> {
        val path = "events.sScripts[$id].trigger"
        return when (cond) {
            is TriggerCondition.UnitDead -> listOfNotNull(unit(path, cond.unit, unitIds))
            is TriggerCondition.UnitReach -> listOfNotNull(unit(path, cond.unit, unitIds), pos(path, cond.pos))
            is TriggerCondition.HpBelow -> listOfNotNull(
                unit(path, cond.unit, unitIds),
                if (cond.pct in 0..PERCENT_MAX) null else ValidationIssue(path, "hp_below pct out of range: ${cond.pct}"),
            )
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
            is WinLoseCondition.ReachTile -> listOfNotNull(unit(path, cond.unit, unitIds), pos(path, cond.pos))
            is WinLoseCondition.ProtectAlive -> listOfNotNull(unit(path, cond.unit, unitIds))
            is WinLoseCondition.DefeatUnit -> listOfNotNull(unit(path, cond.unit, unitIds))
            is WinLoseCondition.SurviveTurns ->
                if (cond.turns >= 1) emptyList() else listOf(ValidationIssue(path, "survive_turns must be >= 1: ${cond.turns}"))
            WinLoseCondition.AnnihilateEnemies -> emptyList()
        }
    }

    private fun rScript(script: RScript, unitIds: Set<String>): List<ValidationIssue> {
        val path = "events.rScripts[${script.id}]"
        val labels = mutableSetOf<String>()
        val issues = mutableListOf<ValidationIssue>()
        script.ops.filterIsInstance<ScenarioOp.Label>().forEach { label ->
            if (!labels.add(label.name)) issues += ValidationIssue(path, "duplicate label: ${label.name}")
        }
        script.ops.forEach { issues += scenarioOp(path, it, labels, unitIds) }
        return issues
    }

    private fun scenarioOp(
        path: String,
        op: ScenarioOp,
        labels: Set<String>,
        unitIds: Set<String>,
    ): List<ValidationIssue> = when (op) {
        is ScenarioOp.Branch -> listOfNotNull(label(path, op.target, labels))
        is ScenarioOp.Choice -> op.options.mapNotNull { label(path, it.goto, labels) }
        is ScenarioOp.Portrait -> listOfNotNull(unit(path, op.unit, unitIds))
        is ScenarioOp.Dialogue,
        is ScenarioOp.SetVar,
        is ScenarioOp.Label,
        is ScenarioOp.Wait,
        is ScenarioOp.SceneTransition,
        is ScenarioOp.PlayBgm,
        ScenarioOp.FadeIn,
        ScenarioOp.FadeOut,
        -> emptyList()
    }

    private fun label(path: String, target: String?, labels: Set<String>): ValidationIssue? =
        when {
            target == null -> null
            target in labels -> null
            else -> ValidationIssue(path, "unknown label: $target")
        }
}
