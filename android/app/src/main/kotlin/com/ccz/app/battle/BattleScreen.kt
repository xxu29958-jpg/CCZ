package com.ccz.app.battle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleState
import com.ccz.core.event.SScript
import com.ccz.core.event.WinLoseCondition

/**
 * Display-name lookups the battle UI needs, each resolving an id to a human name from the campaign's
 * content tables: [skill] for the skill picker, [terrain] for the tile inspector. Grouped so the
 * screen takes one labels holder rather than a growing list of lookup params.
 */
data class BattleLabels(
    val skill: (String) -> String,
    val terrain: (String) -> String,
)

/**
 * Top-level battle UI. Holds the rendered [BattleUiState] and routes every input through
 * the pure [BattleReducer], which is the only thing that talks to game-core. This layer just
 * draws the latest snapshot and forwards taps / skill picks — it owns no combat authority.
 * [labels] maps skill / terrain ids to their display names (read from the campaign content).
 */
@Composable
fun BattleScreen(
    map: BattleMap,
    reducer: BattleReducer,
    initial: BattleUiState,
    labels: BattleLabels,
    script: SScript,
) {
    var ui by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        OutcomeBanner(outcome = ui.outcome)
        Hud(ui = ui, script = script, onEndTurn = { ui = reducer.endTurn(ui) })
        Spacer(modifier = Modifier.height(12.dp))
        SkillBar(ui = ui, skillLabel = labels.skill, onSelectSkill = { ui = reducer.selectSkill(ui, it) })
        WaitButton(ui = ui, onWait = { ui = reducer.wait(ui) })
        BattleBoard(map = map, ui = ui, onTapTile = { pos -> ui = reducer.tapTile(ui, pos) })
        TerrainPanel(map = map, ui = ui, terrainName = labels.terrain)
        Spacer(modifier = Modifier.height(12.dp))
        EventLog(log = ui.log)
    }
}

/**
 * Shows the last-tapped tile's terrain: its name, move cost, and combat cover (def/avoid/heal — the
 * same modifiers game-core's formula reads), plus whoever stands on it. A read-only readout of the
 * map; renders nothing until a tile is tapped. Lets the player see why a defender on cover holds.
 */
@Composable
private fun TerrainPanel(map: BattleMap, ui: BattleUiState, terrainName: (String) -> String) {
    val pos = ui.inspected ?: return
    val info = terrainInfoAt(map, terrainName, pos) ?: return
    val lines = terrainBonusLines(info)
    val occupant = ui.state.unitAt(pos)
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(text = "地形: ${info.name} (${pos.x}, ${pos.y})", style = MaterialTheme.typography.bodyMedium)
        Text(text = "移动消耗 ${info.moveCost}", style = MaterialTheme.typography.bodySmall)
        if (lines.isNotEmpty()) Text(text = lines.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
        occupant?.let {
            Text(text = "占据: ${it.name}", style = MaterialTheme.typography.bodySmall)
            // The live combat panel, so a heal/buff/debuff (ADR 0008) is visible here, not just a one-shot badge.
            Text(text = combatantSummary(it), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "Wait" stands the selected unit down for the turn; shown only while a unit is selected. */
@Composable
private fun WaitButton(ui: BattleUiState, onWait: () -> Unit) {
    if (ui.selection == null) return
    Button(onClick = onWait, modifier = Modifier.padding(bottom = 12.dp)) { Text(text = "Wait") }
}

/** Victory/defeat banner; renders nothing while the battle is ongoing. */
@Composable
private fun OutcomeBanner(outcome: BattleOutcome) {
    if (outcome == BattleOutcome.ONGOING) return
    Text(text = verdictBanner(outcome), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun Hud(ui: BattleUiState, script: SScript, onEndTurn: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Turn ${ui.state.turn} · ${sideLabel(ui.state.active)}")
            objectiveText(script, ui.state)?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            Text(text = ui.selection?.unit?.let { "Selected: ${ui.state.units[it]?.name ?: it}" } ?: "Tap a unit to select")
        }
        Button(onClick = onEndTurn) { Text(text = "End Turn") }
    }
}

/** The battle's win/lose goals as a one-line objective, names resolved from the live roster; null if none. */
private fun objectiveText(script: SScript, state: BattleState): String? {
    val win = script.win.joinToString(" / ") { winText(it, state) }
    val lose = script.lose.joinToString(" / ") { loseText(it, state) }
    return listOfNotNull(
        win.takeIf { it.isNotEmpty() }?.let { "胜: $it" },
        lose.takeIf { it.isNotEmpty() }?.let { "败: $it" },
    ).joinToString("  ·  ").ifEmpty { null }
}

/** A win condition phrased as a GOAL to achieve. */
private fun winText(condition: WinLoseCondition, state: BattleState): String {
    fun name(id: String) = state.units[id]?.name ?: id
    return when (condition) {
        is WinLoseCondition.AnnihilateEnemies -> "击破全部敌军"
        is WinLoseCondition.UnitDead -> "击杀 ${name(condition.unit)}"
        is WinLoseCondition.DefeatUnit -> "击破 ${name(condition.unit)}"
        is WinLoseCondition.ProtectAlive -> "保全 ${name(condition.unit)}"
        is WinLoseCondition.ReachTile -> "${name(condition.unit)} 抵达 (${condition.pos.x}, ${condition.pos.y})"
        is WinLoseCondition.SurviveTurns -> "坚守 ${condition.turns} 回合"
    }
}

/** The SAME conditions phrased as a DEFEAT trigger — what makes you lose (e.g. ProtectAlive → unit falls). */
private fun loseText(condition: WinLoseCondition, state: BattleState): String {
    fun name(id: String) = state.units[id]?.name ?: id
    return when (condition) {
        is WinLoseCondition.ProtectAlive -> "${name(condition.unit)} 阵亡"
        is WinLoseCondition.UnitDead -> "${name(condition.unit)} 阵亡"
        is WinLoseCondition.SurviveTurns -> "未能坚守 ${condition.turns} 回合"
        is WinLoseCondition.AnnihilateEnemies -> "敌军未被击破"
        is WinLoseCondition.DefeatUnit -> "未能击破 ${name(condition.unit)}"
        is WinLoseCondition.ReachTile -> "${name(condition.unit)} 未抵达 (${condition.pos.x}, ${condition.pos.y})"
    }
}

/** Skill picker for the selected unit; only shown when it has more than one usable skill. */
@Composable
private fun SkillBar(ui: BattleUiState, skillLabel: (String) -> String, onSelectSkill: (String) -> Unit) {
    val selection = ui.selection ?: return
    if (selection.skills.size < 2) return
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Skill: ", modifier = Modifier.padding(end = 8.dp))
        selection.skills.forEach { id ->
            FilterChip(
                selected = id == selection.selectedSkill,
                onClick = { onSelectSkill(id) },
                label = { Text(text = skillLabel(id)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun EventLog(log: List<String>) {
    Column {
        Text(text = "Log")
        log.forEach { Text(text = it) }
    }
}
