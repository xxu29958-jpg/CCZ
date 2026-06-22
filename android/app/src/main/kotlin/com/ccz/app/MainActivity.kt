package com.ccz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ccz.app.battle.BattleReducer
import com.ccz.app.battle.BattleScreen
import com.ccz.app.battle.DemoBattle

/**
 * App shell. The presentation layer holds no combat authority: it renders the battle
 * state game-core decides and forwards taps as commands through [BattleReducer], which
 * is the only thing that talks to game-core (Gameplay.submit / legalDestinations /
 * legalTargets). The app never computes damage, decides range, mutates battle state,
 * consumes RNG, or decides outcomes. The battle here runs off a hardcoded [DemoBattle]
 * seed until the content-fed driver layer lands; event-driven cutscenes and a multi-skill
 * picker arrive in later slices.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BattleHost()
                }
            }
        }
    }
}

@Composable
private fun BattleHost() {
    val context = remember { DemoBattle.context() }
    val reducer = remember { BattleReducer(context, DemoBattle.BASIC_ATTACK) }
    val initial = remember { reducer.initial(DemoBattle.initialState()) }
    BattleScreen(map = context.map, reducer = reducer, initial = initial)
}
