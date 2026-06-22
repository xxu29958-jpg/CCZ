package com.ccz.app.scenario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ccz.core.event.ScenarioOp

/**
 * Top-level cutscene UI. Holds the rendered [ScenarioUiState] and routes advance/choose through the
 * pure [ScenarioReducer], which is the only thing that talks to game-core's ScenarioRunner. This layer
 * just draws the current op and forwards input — it owns no scenario truth (no branch/var logic of its
 * own). [onFinished] fires once the scene has played out, letting the host move on (e.g. into battle).
 */
@Composable
fun ScenarioScreen(reducer: ScenarioReducer, initial: ScenarioUiState, onFinished: () -> Unit) {
    var ui by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val current = ui.current
        val choice = ui.pausedChoice
        when {
            current != null -> DialogueBox(op = current, onAdvance = { ui = reducer.advance(ui) })
            choice != null -> ChoiceBox(choice = choice, onChoose = { ui = reducer.choose(ui, it) })
            ui.halted -> Text(text = "（剧情中断：超出步数预算）")
            else -> SceneEnd(onFinished = onFinished)
        }
    }
}

/** Renders the current op and advances on tap. */
@Composable
private fun DialogueBox(op: ScenarioOp, onAdvance: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onAdvance() }.padding(16.dp)) {
        OpContent(op = op)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "▼ 点击继续")
    }
}

/**
 * Single-`when` projection of an emitted op to its on-screen text — kept isolated so detekt's
 * ignoreSingleWhenExpression exempts the exhaustive branch. Control-flow ops (Choice / SetVar / Branch /
 * Label) are never emitted to Playback.events, so their branch is unreachable here.
 */
@Composable
private fun OpContent(op: ScenarioOp) {
    when (op) {
        is ScenarioOp.Dialogue -> {
            op.line.speaker?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
            Text(text = op.line.text)
        }
        is ScenarioOp.Portrait -> Text(text = "立绘：${op.unit}" + (op.emotion?.let { "（$it）" } ?: ""))
        is ScenarioOp.SceneTransition -> Text(text = "场景 → ${op.target}")
        is ScenarioOp.PlayBgm -> Text(text = "♪ ${op.id}")
        is ScenarioOp.Wait -> Text(text = "……")
        ScenarioOp.FadeIn -> Text(text = "（淡入）")
        ScenarioOp.FadeOut -> Text(text = "（淡出）")
        is ScenarioOp.Choice, is ScenarioOp.SetVar, is ScenarioOp.Branch, is ScenarioOp.Label -> Unit
    }
}

/** Renders the pending choice as one button per option. */
@Composable
private fun ChoiceBox(choice: ScenarioOp.Choice, onChoose: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = choice.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        choice.options.forEachIndexed { index, option ->
            Button(onClick = { onChoose(index) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(text = option.text)
            }
        }
    }
}

@Composable
private fun SceneEnd(onFinished: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "（剧情结束）")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onFinished) { Text(text = "进入战斗") }
    }
}
