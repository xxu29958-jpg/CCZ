package com.ccz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ccz.core.battle.BattleRules

/**
 * Minimal app shell. The presentation layer holds no combat authority: it reads
 * already-decided values from game-core (here, the rules version) and renders them —
 * it never computes damage, mutates battle state, consumes RNG, or decides outcomes.
 * Rendering map/units, input -> command (via Gameplay.submit), and the event-driven
 * presentation layer arrive in later slices.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShellScreen(rulesVersion = BattleRules.RULES_VERSION)
                }
            }
        }
    }
}

@Composable
private fun ShellScreen(rulesVersion: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "CCZ Tactics Engine — game-core rules v$rulesVersion")
    }
}
