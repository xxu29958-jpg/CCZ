package com.ccz.app.battle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
 * the pure [BattleReducer], which is the only thing that talks to game-core. This layer
 * just draws the latest snapshot and forwards taps — it owns no combat authority.
 */
@Composable
fun BattleScreen(map: BattleMap, reducer: BattleReducer, initial: BattleUiState) {
    var ui by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Hud(ui = ui, onEndTurn = { ui = reducer.endTurn(ui) })
        Spacer(modifier = Modifier.height(12.dp))
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
            Text(text = ui.selected?.let { "Selected: ${ui.state.units[it]?.name ?: it}" } ?: "Tap a unit to select")
        }
        Button(onClick = onEndTurn) { Text(text = "End Turn") }
    }
}

@Composable
private fun EventLog(log: List<String>) {
    Column {
        Text(text = "Log")
        log.forEach { Text(text = it) }
    }
}
