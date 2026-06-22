package com.ccz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ccz.app.battle.BattleReducer
import com.ccz.app.battle.BattleScreen
import com.ccz.app.battle.DemoBattle
import com.ccz.app.scenario.DemoScenario
import com.ccz.app.scenario.ScenarioReducer
import com.ccz.app.scenario.ScenarioScreen

/**
 * App shell. The presentation layer holds no authority: it renders what game-core decides and forwards
 * input through the pure reducers, which are the only things that talk to game-core. For battle that is
 * [BattleReducer] (Gameplay.submit / legalDestinations / legalTargets / legalSkills); for the cutscene it
 * is [ScenarioReducer] driving the deterministic ScenarioRunner. The app never computes damage, decides
 * range/legality, mutates state, consumes RNG, decides outcomes, or evolves scenario vars/branches — it
 * only draws the authority's output. The battle now runs off the content-driven [DemoBattle], which
 * [com.ccz.contentpack.assembly.CampaignAssembler] assembles from the [com.ccz.app.battle.CampaignContent]
 * native-content pack; the intro cutscene still runs off the hand-built [DemoScenario] seed pending a
 * content path for non-combatant speakers.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppHost()
                }
            }
        }
    }
}

/** Plays the intro cutscene, then hands off to the battle once it finishes. */
@Composable
private fun AppHost() {
    var inScenario by remember { mutableStateOf(true) }
    if (inScenario) {
        val reducer = remember { ScenarioReducer(DemoScenario.script()) }
        val initial = remember { reducer.initial() }
        ScenarioScreen(reducer = reducer, initial = initial, onFinished = { inScenario = false })
    } else {
        BattleHost()
    }
}

@Composable
private fun BattleHost() {
    val context = remember { DemoBattle.context() }
    val reducer = remember { BattleReducer(context, DemoBattle.script()) }
    val initial = remember { reducer.initial(DemoBattle.initialState()) }
    BattleScreen(
        map = context.map,
        reducer = reducer,
        initial = initial,
        skillLabel = { id -> context.skills[id]?.name ?: id },
    )
}
