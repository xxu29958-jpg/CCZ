package com.ccz.app.scenario

import com.ccz.core.event.RScript
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.ScenarioRunner

/**
 * Pure presentation reducer for an R-script cutscene. Holds no scenario authority: it calls the
 * deterministic [ScenarioRunner] (the single source of truth for scenario flow — var evolution,
 * branch/goto, choice resolution) and only renders the [ScenarioRunner.Playback] it returns. Player
 * picks accumulate in [ScenarioUiState.choices] — the replayable input axis, mirroring how a battle's
 * command list replays — and each step is `ScenarioRunner.run(script, choices)`, never the reducer
 * re-implementing control flow. No Android dependency → JVM-testable (mirrors `battle.BattleReducer`).
 */
class ScenarioReducer(private val script: RScript) {
    fun initial(): ScenarioUiState = stateFor(choices = emptyList(), cursor = 0)

    /**
     * Advance to the next emitted op. No-op once the cursor has consumed every emitted op — at that
     * point the scene is either awaiting a choice or finished (see [ScenarioUiState]); advancing must
     * not run past the authority's output.
     */
    fun advance(ui: ScenarioUiState): ScenarioUiState =
        if (ui.cursor < ui.playback.events.size) ui.copy(cursor = ui.cursor + 1) else ui

    /**
     * Resolve the pending choice by option index, re-running the script with the extended choice list.
     * Fail-closed: a no-op unless the scene is actually awaiting a choice AND [optionIndex] is within
     * that choice's options — the reducer never feeds the runner an out-of-range index (the runner
     * would merely re-pause) nor fabricates a branch. The cursor is preserved, so playback continues
     * into the newly-emitted ops past the resolved choice (the run is deterministic, so the already-seen
     * prefix is reproduced identically).
     */
    fun choose(ui: ScenarioUiState, optionIndex: Int): ScenarioUiState {
        val pending = ui.pausedChoice ?: return ui
        if (optionIndex !in pending.options.indices) return ui
        return stateFor(choices = ui.choices + optionIndex, cursor = ui.cursor)
    }

    private fun stateFor(choices: List<Int>, cursor: Int): ScenarioUiState =
        ScenarioUiState(playback = ScenarioRunner.run(script, choices = choices), choices = choices, cursor = cursor)
}

/**
 * Rendered scenario snapshot. [playback] is the authority's output for the choices made so far;
 * [cursor] is how many of its emitted ops the player has advanced past. The derived flags below are
 * the only thing the UI branches on — all computed from the authority's [playback], never second-guessed.
 */
data class ScenarioUiState(
    val playback: ScenarioRunner.Playback,
    val choices: List<Int>,
    val cursor: Int,
) {
    /** The op currently on screen, or null once the cursor has consumed every emitted op. */
    val current: ScenarioOp? get() = playback.events.getOrNull(cursor)

    /** All emitted ops have been shown (the cursor sits at/after the end of the list). */
    private val atEnd: Boolean get() = cursor >= playback.events.size

    /** The pending choice to render, or null — only offered once every emitted op has been shown. */
    val pausedChoice: ScenarioOp.Choice? get() = if (atEnd) playback.pausedAt else null

    /** The scene played out: every op shown, no choice pending, and the step budget was not hit. */
    val finished: Boolean get() = atEnd && playback.pausedAt == null && !playback.haltedOnBudget

    /** The runner hit its loop-guard step budget; surfaced so the UI can show a fail-safe, not hang. */
    val halted: Boolean get() = atEnd && playback.haltedOnBudget
}
