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
 * Top-level battle UI. Holds the rendered [BattleUiState] and routes every input through
 * the pure [BattleReducer], which is the only thing that talks to game-core. This layer just
 * draws the latest snapshot and forwards taps / skill picks — it owns no combat authority.
 * [skillLabel] maps a skill id to its display name (read from the context's skill table).
 */
@Composable
fun BattleScreen(
    map: BattleMap,
    reducer: BattleReducer,
    initial: BattleUiState,
    skillLabel: (String) -> String,
    script: SScript,
) {
    var ui by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        OutcomeBanner(outcome = ui.outcome)
        Hud(ui = ui, script = script, onEndTurn = { ui = reducer.endTurn(ui) })
        Spacer(modifier = Modifier.height(12.dp))
        SkillBar(ui = ui, skillLabel = skillLabel, onSelectSkill = { ui = reducer.selectSkill(ui, it) })
        WaitButton(ui = ui, onWait = { ui = reducer.wait(ui) })
        BattleBoard(map = map, ui = ui, onTapTile = { pos -> ui = reducer.tapTile(ui, pos) })
        Spacer(modifier = Modifier.height(12.dp))
        EventLog(log = ui.log)
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
    val win = script.win.joinToString(" / ") { conditionText(it, state) }
    val lose = script.lose.joinToString(" / ") { conditionText(it, state) }
    return listOfNotNull(
        win.takeIf { it.isNotEmpty() }?.let { "胜: $it" },
        lose.takeIf { it.isNotEmpty() }?.let { "败: $it" },
    ).joinToString("  ·  ").ifEmpty { null }
}

private fun conditionText(condition: WinLoseCondition, state: BattleState): String {
    fun name(id: String) = state.units[id]?.name ?: id
    return when (condition) {
        is WinLoseCondition.AnnihilateEnemies -> "击破全部敌军"
        is WinLoseCondition.UnitDead -> "击杀 ${name(condition.unit)}"
        is WinLoseCondition.DefeatUnit -> "击破 ${name(condition.unit)}"
        is WinLoseCondition.ProtectAlive -> "${name(condition.unit)} 存活"
        is WinLoseCondition.ReachTile -> "${name(condition.unit)} 抵达 (${condition.pos.x}, ${condition.pos.y})"
        is WinLoseCondition.SurviveTurns -> "坚守 ${condition.turns} 回合"
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
