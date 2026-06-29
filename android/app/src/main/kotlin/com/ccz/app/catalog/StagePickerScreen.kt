package com.ccz.app.catalog

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ccz.app.R
import com.ccz.app.campaign.CampaignStageRuntime

@Composable
fun StagePickerScreen(onStart: (CampaignStageRuntime) -> Unit) {
    val stages = remember { PlayableStageCatalog.playableStages() }
    var selectedStageId by remember { mutableStateOf(stages.first().stage.id) }
    var accessByStage by remember {
        mutableStateOf(stages.associate { it.stage.id to PlayableStageCatalog.accessFor(it.stage.id) })
    }
    val selectedStage = stages.first { it.stage.id == selectedStageId }
    val access = accessByStage.getValue(selectedStageId)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = stringResource(R.string.stage_picker_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        StageTabs(stages = stages, selectedStageId = selectedStageId, onSelected = { selectedStageId = it })
        Spacer(modifier = Modifier.height(16.dp))
        StageAccessStatus(access = access)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            accessByStage = accessByStage + (
                selectedStageId to PlayableStageCatalog.resolvePurchase(
                    productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
                    stageId = selectedStageId,
                )
                )
        }) {
            Text(
                text = stringResource(
                    R.string.stage_picker_buy_full_unlock,
                    PlayableStageCatalog.productName(PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID),
                ),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = access.canStart,
            onClick = { access.launchRuntimeOrNull()?.let(onStart) },
        ) {
            Text(text = stringResource(R.string.stage_picker_start, selectedStage.stage.name))
        }
    }
}

@Composable
private fun StageTabs(
    stages: List<PlayableStageSummary>,
    selectedStageId: String,
    onSelected: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        stages.forEach { stage ->
            FilterChip(
                selected = stage.stage.id == selectedStageId,
                onClick = { onSelected(stage.stage.id) },
                label = { Text(text = stage.stage.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun StageAccessStatus(access: PlayableStageAccess) {
    val text = when {
        !access.stageAccess.unlocked -> stringResource(
            R.string.stage_picker_locked,
            access.stageAccess.missingItems.joinToString { PlayableStageCatalog.itemName(it) },
        )
        access.runtime == null -> stringResource(R.string.stage_picker_not_playable)
        else -> stringResource(R.string.stage_picker_ready)
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
