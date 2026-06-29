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
 * same script (labels are unique), and a portrait must name a known unit or a known
 * non-combat portrait subject.
 *
 * It also fails closed on the value-domain floors that are unambiguous independent of
 * any map or game-design choice: a percentage threshold ([TriggerCondition.HpBelow.pct])
 * must lie in 0..100; a survive-N-turns objective ([WinLoseCondition.SurviveTurns]) must
 * be >= 1; and every script-op coordinate (spawn / move / reach targets) must be
 * non-negative (a negative tile is off-board regardless of map size). Upper bounds require
 * knowing which concrete map a script is fought on and are checked by CampaignAssembler.
 *
 * Note: the op set itself is whitelisted by Kotlin's sealed interfaces (an unknown
 * op cannot be constructed in memory); a string-keyed op whitelist only becomes
 * relevant at a future JSON decode boundary. This validator covers the reference
 * integrity that the type system does not.
 */
internal object ContentEventValidator {
    private const val PERCENT_MAX = 100

    fun validate(
        events: EventTables,
        unitIds: Set<String>,
        itemIds: Set<String>,
        portraitIds: Set<String>,
    ): List<ValidationIssue> =
        events.sScripts.flatMap { script(it, unitIds, itemIds, portraitIds) } +
            deferredDeployments(events, unitIds) +
            events.rScripts.flatMap { rScript(it, portraitIds) }

    private fun script(
        script: SScript,
        unitIds: Set<String>,
        itemIds: Set<String>,
        portraitIds: Set<String>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        issues += duplicatePreSpawns(script)
        (script.pre + script.post + script.mid.flatMap { it.actions }).forEach {
            issues += battleOp(script.id, it, unitIds, itemIds, portraitIds)
        }
        val triggerIds = mutableSetOf<String>()
        script.mid.forEach { t ->
            // Mirror the R-script label dedup: trigger ids must be unique within a script
            // because TriggerRunner keys `once` firing on the id (BattleProgress.firedTriggers),
            // so a shared id would let one trigger permanently suppress another.
            if (!triggerIds.add(t.id)) {
                issues += ValidationIssue("events.sScripts[${script.id}].mid", "duplicate trigger id: ${t.id}")
            }
            issues += trigger(script.id, t.whenCondition, unitIds)
        }
        script.win.forEach { issues += winLose(script.id, "win", it, unitIds) }
        script.lose.forEach { issues += winLose(script.id, "lose", it, unitIds) }
        return issues
    }

    private fun duplicatePreSpawns(script: SScript): List<ValidationIssue> {
        val spawned = mutableSetOf<String>()
        return script.pre.mapIndexedNotNull { index, op ->
            if (op is BattleOp.SpawnUnit && !spawned.add(op.unit)) {
                ValidationIssue("events.sScripts[${script.id}].pre[$index].unit", "duplicate pre spawn unit: ${op.unit}")
            } else {
                null
            }
        }
    }

    private fun unit(path: String, id: String, unitIds: Set<String>): ValidationIssue? =
        if (id in unitIds) null else ValidationIssue(path, "unknown unit: $id")

    private fun portrait(path: String, id: String, portraitIds: Set<String>): ValidationIssue? =
        if (id in portraitIds) null else ValidationIssue(path, "unknown portrait subject: $id")

    /**
     * Map-independent floor on a script-op coordinate: a negative tile is off-board for
     * any map size. CampaignAssembler checks the concrete map upper bound for the selected
     * battle script.
     */
    private fun pos(path: String, at: Pos): ValidationIssue? =
        if (at.x >= 0 && at.y >= 0) null else ValidationIssue(path, "negative coordinate: (${at.x}, ${at.y})")

    private fun battleOp(
        id: String,
        op: BattleOp,
        unitIds: Set<String>,
        itemIds: Set<String>,
        portraitIds: Set<String>,
    ): List<ValidationIssue> {
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
            is BattleOp.Script -> embeddedScenarioOp(path, op.op, portraitIds)
            BattleOp.ForceWin, BattleOp.ForceLose -> emptyList()
        }
    }

    private fun deferredDeployments(events: EventTables, unitIds: Set<String>): List<ValidationIssue> {
        val scripts = events.sScripts.associateBy { it.id }
        return events.deferredDeployments.flatMapIndexed { index, deployment ->
            deferredDeployment(index, deployment, scripts, unitIds)
        }
    }

    private fun deferredDeployment(
        index: Int,
        deployment: DeferredDeploymentDef,
        scripts: Map<String, SScript>,
        unitIds: Set<String>,
    ): List<ValidationIssue> {
        val path = "events.deferredDeployments[$index]"
        val preSpawned = scripts[deployment.scriptId]?.pre.orEmpty()
            .filterIsInstance<BattleOp.SpawnUnit>()
            .mapTo(HashSet()) { it.unit }
        return listOfNotNull(
            if (deployment.scriptId in scripts) null else ValidationIssue("$path.script", "unknown s-script: ${deployment.scriptId}"),
            unit(path, deployment.unit, unitIds),
            pos("$path.at", deployment.at),
            if (deployment.source.isBlank()) ValidationIssue("$path.source", "source is blank") else null,
            if (deployment.unit in preSpawned) {
                ValidationIssue(path, "deferred unit is already spawned in pre: ${deployment.unit}")
            } else {
                null
            },
        )
    }

    /**
     * Reference / support check for a scenario op embedded in an S-script via
     * [BattleOp.Script]. Unlike an R-script, an S-script has no label table and
     * [com.ccz.core.battle.BattleOps] only interprets [ScenarioOp.SetVar]; every other
     * embedded op is surfaced as a presentation event. So a Portrait still needs its portrait
     * reference checked (the R-script path already does), while a Branch / Choice is
     * provably inert control flow here (its label target can never resolve) and is flagged
     * fail-closed rather than silently dropped. Presentation ops carry no reference.
     */
    private fun embeddedScenarioOp(path: String, op: ScenarioOp, portraitIds: Set<String>): List<ValidationIssue> =
        when (op) {
            is ScenarioOp.Portrait -> listOfNotNull(portrait(path, op.unit, portraitIds))
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

    private fun rScript(script: RScript, portraitIds: Set<String>): List<ValidationIssue> {
        val path = "events.rScripts[${script.id}]"
        val labels = mutableSetOf<String>()
        val issues = mutableListOf<ValidationIssue>()
        script.ops.filterIsInstance<ScenarioOp.Label>().forEach { label ->
            if (!labels.add(label.name)) issues += ValidationIssue(path, "duplicate label: ${label.name}")
        }
        script.ops.forEach { issues += scenarioOp(path, it, labels, portraitIds) }
        return issues
    }

    private fun scenarioOp(
        path: String,
        op: ScenarioOp,
        labels: Set<String>,
        portraitIds: Set<String>,
    ): List<ValidationIssue> = when (op) {
        is ScenarioOp.Branch -> listOfNotNull(label(path, op.target, labels))
        is ScenarioOp.Choice -> op.options.mapNotNull { label(path, it.goto, labels) }
        is ScenarioOp.Portrait -> listOfNotNull(portrait(path, op.unit, portraitIds))
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
