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

/** Everything needed to draw one grid cell, derived from the current snapshot. */
private data class CellModel(
    val pos: Pos,
    val tile: MapTile,
    val unit: Combatant?,
    val highlighted: Boolean,
    val selected: Boolean,
)

/** Draws the static map plus units, selection ring, and the move highlights from state. */
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
            .border(width = if (cell.selected) 3.dp else 1.dp, color = borderColor(cell))
            .clickable { onTap(cell.pos) },
        contentAlignment = Alignment.Center,
    ) {
        cell.unit?.let { UnitMarker(it) }
    }
}

@Composable
private fun UnitMarker(unit: Combatant) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = unit.name.take(2), color = factionColor(unit.faction), fontWeight = FontWeight.Bold)
        Text(text = unit.hp.toString(), fontSize = 10.sp)
    }
}

private fun rowCells(map: BattleMap, ui: BattleUiState, y: Int): List<CellModel> =
    (0 until map.width).map { x -> cellAt(map, ui, Pos(x, y)) }

private fun cellAt(map: BattleMap, ui: BattleUiState, pos: Pos): CellModel {
    val unit = ui.state.units.values.firstOrNull { it.alive && it.pos == pos }
    return CellModel(
        pos = pos,
        tile = map.tileAt(pos),
        unit = unit,
        highlighted = pos in ui.destinations,
        selected = unit != null && unit.id == ui.selected,
    )
}

private fun tileColor(cell: CellModel): Color = when {
    !cell.tile.passable -> Color(0xFF37474F)
    cell.highlighted -> Color(0xFF9CCC65)
    cell.tile.moveCost > 1 -> Color(0xFF558B2F)
    else -> Color(0xFFCFD8DC)
}

private fun borderColor(cell: CellModel): Color =
    if (cell.selected) Color(0xFFFFC107) else Color(0xFF90A4AE)

private fun factionColor(faction: Faction): Color = when (faction) {
    Faction.PLAYER, Faction.ALLY -> Color(0xFF1565C0)
    Faction.ENEMY -> Color(0xFFC62828)
}
