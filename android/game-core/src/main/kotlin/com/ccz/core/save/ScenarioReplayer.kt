package com.ccz.core.save

import com.ccz.core.event.RScript
import com.ccz.core.event.ScenarioRunner

/** Why a scenario axis cannot be replayed. */
enum class ScenarioRejection { UNKNOWN_SCRIPT, INCOMPLETE_REPLAY }

/**
 * Replays the scenario (cutscene) axis of a save: each [ScenarioReplay] is run through
 * [ScenarioRunner] against its R-script, which is supplied from content ([scripts]) since
 * scripts live there, not in the save. The counterpart to [SaveLoader] for the second
 * (R-script) replay axis. Pure and deterministic — no RNG.
 *
 * Fail-closed: a scenario naming an unknown script ([ScenarioRejection.UNKNOWN_SCRIPT]),
 * or whose recorded choices run out before the scenario completes (left paused at a
 * Choice) or whose script loops past the step budget ([ScenarioRejection.INCOMPLETE_REPLAY]),
 * is rejected rather than silently producing a partial cutscene. A fully-recorded replay
 * runs each scenario to the end with no leftover Choice. Surplus recorded choices (more than
 * the script has Choice ops) are tolerated — they are never read and cannot perturb the
 * deterministic playback, so they are not treated as corruption.
 */
object ScenarioReplayer {
    sealed interface Outcome {
        data class Replayed(val playbacks: List<ScenarioRunner.Playback>) : Outcome
        data class Rejected(val reason: ScenarioRejection) : Outcome
    }

    fun replay(scenarios: List<ScenarioReplay>, scripts: Map<String, RScript>): Outcome {
        val playbacks = mutableListOf<ScenarioRunner.Playback>()
        for (scenario in scenarios) {
            val script = scripts[scenario.scriptId]
                ?: return Outcome.Rejected(ScenarioRejection.UNKNOWN_SCRIPT)
            val playback = ScenarioRunner.run(script, choices = scenario.choices)
            if (playback.pausedAt != null || playback.haltedOnBudget) {
                return Outcome.Rejected(ScenarioRejection.INCOMPLETE_REPLAY)
            }
            playbacks += playback
        }
        return Outcome.Replayed(playbacks)
    }
}
