package com.ccz.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ccz.app.battle.BattleLabels
import com.ccz.app.battle.BattleReducer
import com.ccz.app.battle.BattleScreen
import com.ccz.app.campaign.CampaignStageRuntime
import com.ccz.app.catalog.StagePickerScreen
import com.ccz.app.scenario.ScenarioReducer
import com.ccz.app.scenario.ScenarioScreen

private sealed interface AppRoute {
    data object StagePicker : AppRoute
    data class Scenario(val runtime: CampaignStageRuntime) : AppRoute
    data class Battle(val runtime: CampaignStageRuntime) : AppRoute
}

@Composable
fun AppHost() {
    var route by remember { mutableStateOf<AppRoute>(AppRoute.StagePicker) }
    when (val current = route) {
        AppRoute.StagePicker -> StagePickerScreen { runtime ->
            route = if (runtime.introScriptOrNull() == null) AppRoute.Battle(runtime) else AppRoute.Scenario(runtime)
        }
        is AppRoute.Scenario -> ScenarioHost(runtime = current.runtime, onFinished = { route = AppRoute.Battle(current.runtime) })
        is AppRoute.Battle -> BattleHost(runtime = current.runtime)
    }
}

@Composable
private fun ScenarioHost(runtime: CampaignStageRuntime, onFinished: () -> Unit) {
    val intro = requireNotNull(runtime.introScriptOrNull()) { "scenario route requires an intro script: ${runtime.stageId}" }
    val reducer = remember(runtime.stageId) { ScenarioReducer(intro) }
    val initial = remember(runtime.stageId) { reducer.initial() }
    ScenarioScreen(reducer = reducer, initial = initial, onFinished = onFinished)
}

@Composable
private fun BattleHost(runtime: CampaignStageRuntime) {
    val context = remember(runtime.stageId) { runtime.context() }
    val reducer = remember(runtime.stageId) { BattleReducer(context, runtime.script(), runtime.scriptContext()) }
    val initial = remember(runtime.stageId) { reducer.initial(runtime.initialState()) }
    val terrainNames = remember(runtime.stageId) { runtime.terrainNames() }
    BattleScreen(
        map = context.map,
        reducer = reducer,
        initial = initial,
        labels = BattleLabels(
            skill = { id -> context.skills[id]?.name ?: id },
            terrain = { id -> terrainNames[id] ?: id },
        ),
        script = runtime.script(),
    )
}
