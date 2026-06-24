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
import com.ccz.app.battle.RealBattle
import com.ccz.app.scenario.RealScenario
import com.ccz.app.scenario.ScenarioReducer
import com.ccz.app.scenario.ScenarioScreen

/**
 * App shell. The presentation layer holds no authority: it renders what game-core decides and forwards
 * input through the pure reducers, which are the only things that talk to game-core. For battle that is
 * [BattleReducer] (Gameplay.submit / legalDestinations / legalTargets / legalSkills); for the cutscene it
 * is [ScenarioReducer] driving the deterministic ScenarioRunner. The app never computes damage, decides
 * range/legality, mutates state, consumes RNG, decides outcomes, or evolves scenario vars/branches — it
 * only draws the authority's output. [RealBattle] assembles the playable battle from a content pack
 * generated out of the user's real legacy data; [RealScenario] supplies the matching 大兴山 intro R-script.
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

/** Plays the 大兴山 intro cutscene ([RealScenario]), then hands off to the matching real battle. */
@Composable
private fun AppHost() {
    var inScenario by remember { mutableStateOf(true) }
    if (inScenario) {
        val reducer = remember { ScenarioReducer(RealScenario.script()) }
        val initial = remember { reducer.initial() }
        ScenarioScreen(reducer = reducer, initial = initial, onFinished = { inScenario = false })
    } else {
        BattleHost()
    }
}

@Composable
private fun BattleHost() {
    val context = remember { RealBattle.context() }
    val reducer = remember { BattleReducer(context, RealBattle.script(), RealBattle.scriptContext()) }
    val initial = remember { reducer.initial(RealBattle.initialState()) }
    BattleScreen(
        map = context.map,
        reducer = reducer,
        initial = initial,
        skillLabel = { id -> context.skills[id]?.name ?: id },
    )
}
