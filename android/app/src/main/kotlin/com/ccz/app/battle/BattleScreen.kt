package com.ccz.app.battle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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

/**
 * Top-level battle UI. Holds the rendered [BattleUiState] and routes every input through
 * the pure [BattleReducer], which is the only thing that talks to game-core. This layer just
 * draws the latest snapshot and forwards taps / skill picks — it owns no combat authority.
 * [skillLabel] maps a skill id to its display name (read from the context's skill table).
 */
@Composable
fun BattleScreen(map: BattleMap, reducer: BattleReducer, initial: BattleUiState, skillLabel: (String) -> String) {
    var ui by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Hud(ui = ui, onEndTurn = { ui = reducer.endTurn(ui) })
        Spacer(modifier = Modifier.height(12.dp))
        SkillBar(ui = ui, skillLabel = skillLabel, onSelectSkill = { ui = reducer.selectSkill(ui, it) })
        BattleBoard(map = map, ui = ui, onTapTile = { pos -> ui = reducer.tapTile(ui, pos) })
        Spacer(modifier = Modifier.height(12.dp))
        EventLog(log = ui.log)
    }
}

@Composable
private fun Hud(ui: BattleUiState, onEndTurn: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Turn ${ui.state.turn} · ${sideLabel(ui.state.active)}")
            Text(text = ui.selection?.unit?.let { "Selected: ${ui.state.units[it]?.name ?: it}" } ?: "Tap a unit to select")
        }
        Button(onClick = onEndTurn) { Text(text = "End Turn") }
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
