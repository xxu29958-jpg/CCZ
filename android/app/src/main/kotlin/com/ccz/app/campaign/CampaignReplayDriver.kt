package com.ccz.app.campaign

import com.ccz.app.battle.CampaignContent
import com.ccz.app.battle.DemoBattle
import com.ccz.core.battle.BattleState
import com.ccz.core.event.RScript
import com.ccz.core.event.ScenarioRunner
import com.ccz.core.save.SaveEnvelope
import com.ccz.core.save.SaveLoader
import com.ccz.core.save.SaveRejection
import com.ccz.core.save.ScenarioRejection
import com.ccz.core.save.ScenarioReplayer

/**
 * Content-aware replay entry point for the bundled campaign. This is the app composition
 * layer joining the two pure replay axes that already live in game-core:
 *
 * - battle commands: [SaveLoader.load] folds accepted commands from the saved initial state.
 * - scenario choices: [ScenarioReplayer.replay] re-runs R-scripts from content with recorded choices.
 *
 * The driver supplies the content-derived class/skill/script tables and returns no partial success:
 * if either axis rejects, the app gets a rejection instead of a half-loaded battle or cutscene.
 */
object CampaignReplayDriver {
    sealed interface Outcome {
        data class Replayed(
            val battleState: BattleState,
            val scenarioPlaybacks: List<ScenarioRunner.Playback>,
        ) : Outcome

        data class SaveRejected(val reason: SaveRejection) : Outcome
        data class ScenarioRejected(val reason: ScenarioRejection) : Outcome
    }

    fun load(envelope: SaveEnvelope): Outcome {
        val context = DemoBattle.context()
        val scripts = rScripts()
        val battle = when (val loaded = SaveLoader.load(
            envelope = envelope,
            classes = context.classes,
            skills = context.skills,
            rules = context.rules,
            scripts = scripts,
        )) {
            is SaveLoader.Outcome.Loaded -> loaded
            is SaveLoader.Outcome.Rejected -> return Outcome.SaveRejected(loaded.reason)
        }
        val scenarios = when (val replayed = ScenarioReplayer.replay(envelope.scenarios, scripts)) {
            is ScenarioReplayer.Outcome.Replayed -> replayed
            is ScenarioReplayer.Outcome.Rejected -> return Outcome.ScenarioRejected(replayed.reason)
        }
        return Outcome.Replayed(battle.finalState, scenarios.playbacks)
    }

    private fun rScripts(): Map<String, RScript> =
        CampaignContent.pack().events.rScripts.associateBy { it.id }
}
