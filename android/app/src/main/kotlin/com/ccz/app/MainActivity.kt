package com.ccz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * App shell. The presentation layer holds no authority: it renders what game-core decides and forwards
 * input through pure reducers. [AppHost] owns the app flow from playable-stage registry to scenario to battle;
 * selected runtimes still assemble native content into game-core inputs, and UI never computes combat truth.
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
