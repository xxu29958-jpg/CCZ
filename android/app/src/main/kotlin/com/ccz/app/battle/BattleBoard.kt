package com.ccz.app.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.MapTile
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos

private val CELL_SIZE = 44.dp

/** The mutually-exclusive highlight a tile can carry, derived from the snapshot. */
private enum class CellMark { NONE, MOVE, SELECTED, TARGET }

/** Everything needed to draw one grid cell, derived from the current snapshot. */
private data class CellModel(
    val pos: Pos,
    val tile: MapTile,
    val unit: Combatant?,
    val mark: CellMark,
    val effect: BattleEffect?,
)

/**
 * Draws the static map plus units, selection ring, the move/attack highlights, and the
 * floating damage/miss/KO badges — all derived from the current snapshot, none recomputed.
 */
@Composable
fun BattleBoard(map: BattleMap, ui: BattleUiState, onTapTile: (Pos) -> Unit) {
    Column {
        for (y in 0 until map.height) {
            BoardRow(cells = rowCells(map, ui, y), onTapTile = onTapTile)
        }
    }
}

@Composable
private fun BoardRow(cells: List<CellModel>, onTapTile: (Pos) -> Unit) {
    Row {
        cells.forEach { GridCell(cell = it, onTap = onTapTile) }
    }
}

@Composable
private fun GridCell(cell: CellModel, onTap: (Pos) -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .size(CELL_SIZE)
            .background(tileColor(cell))
            .border(width = borderWidth(cell), color = borderColor(cell))
            .clickable { onTap(cell.pos) },
        contentAlignment = Alignment.Center,
    ) {
        cell.unit?.let { UnitMarker(it) }
        cell.effect?.let { EffectBadge(effect = it, modifier = Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun UnitMarker(unit: Combatant) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = unit.name.take(2), color = factionColor(unit.faction), fontWeight = FontWeight.Bold)
        Text(text = unit.hp.toString(), fontSize = 10.sp)
    }
}

/** A floating badge translating one authority event (damage / miss / death) into a readout. */
@Composable
private fun EffectBadge(effect: BattleEffect, modifier: Modifier = Modifier) {
    val (text, color) = when (effect) {
        is BattleEffect.Damaged ->
            ("−${effect.amount}" + (if (effect.crit) "!" else "") + (if (effect.combo) "+" else "")) to Color(0xFFB71C1C)
        is BattleEffect.Missed -> "Miss" to Color(0xFF455A64)
        is BattleEffect.Defeated -> "KO" to Color(0xFF7B1FA2)
    }
    Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

private fun rowCells(map: BattleMap, ui: BattleUiState, y: Int): List<CellModel> =
    (0 until map.width).map { x -> cellAt(map, ui, Pos(x, y)) }

private fun cellAt(map: BattleMap, ui: BattleUiState, pos: Pos): CellModel {
    val unit = ui.state.unitAt(pos)
    val selection = ui.selection
    val mark = when {
        unit != null && unit.id == selection?.unit -> CellMark.SELECTED
        unit != null && unit.id in (selection?.targets ?: emptySet()) -> CellMark.TARGET
        pos in (selection?.destinations ?: emptySet()) -> CellMark.MOVE
        else -> CellMark.NONE
    }
    return CellModel(
        pos = pos,
        tile = map.tileAt(pos),
        unit = unit,
        mark = mark,
        // The latest effect anchored to whoever stands (or just fell) on this tile — a defeated
        // unit keeps its tile in state, so its "KO" still lands here even though it stops rendering.
        effect = ui.effects.lastOrNull { ui.state.units[it.unit]?.pos == pos },
    )
}

private fun tileColor(cell: CellModel): Color = when {
    !cell.tile.passable -> Color(0xFF37474F)
    cell.mark == CellMark.TARGET -> Color(0xFFEF9A9A)
    cell.mark == CellMark.MOVE || cell.mark == CellMark.SELECTED -> Color(0xFF9CCC65)
    cell.tile.moveCost > 1 -> Color(0xFF558B2F)
    else -> Color(0xFFCFD8DC)
}

private fun borderWidth(cell: CellModel) = when (cell.mark) {
    CellMark.SELECTED -> 3.dp
    CellMark.TARGET -> 2.dp
    else -> 1.dp
}

private fun borderColor(cell: CellModel): Color = when (cell.mark) {
    CellMark.SELECTED -> Color(0xFFFFC107)
    CellMark.TARGET -> Color(0xFFD32F2F)
    else -> Color(0xFF90A4AE)
}

private fun factionColor(faction: Faction): Color = when (faction) {
    Faction.PLAYER, Faction.ALLY -> Color(0xFF1565C0)
    Faction.ENEMY -> Color(0xFFC62828)
}
